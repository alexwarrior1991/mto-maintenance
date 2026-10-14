package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.dto.inspection.CheckItemUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.task.CancelTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.CompleteTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksResponse;
import com.alejandro.mtomaintenance.application.dto.task.InlineDefectRequest;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskResponse;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.task.StartTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.TaskMaterialRequest;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.exception.StaleVersionException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceTaskMapper;
import com.alejandro.mtomaintenance.application.service.MaintenanceCodeGenerator;
import com.alejandro.mtomaintenance.application.service.MaintenanceTaskService;
import com.alejandro.mtomaintenance.application.service.StatusHistoryService;
import com.alejandro.mtomaintenance.application.service.WorkloadEstimator;
import com.alejandro.mtomaintenance.domain.model.ShiftStateMachine;
import com.alejandro.mtomaintenance.domain.model.WorkloadEstimate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.AbstractChecklistItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskCheckItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskType;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryDefectRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class MaintenanceTaskServiceImpl implements MaintenanceTaskService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaintenanceTaskServiceImpl.class);

    /** Lo que un PATCH puede vaciar de una tarea; la descripcion es obligatoria. */
    private static final Set<String> CLEARABLE = Set.of("assignedUser", "taskTypeCodes", "notes", "defectsFound", "photoRefs");

    /** Lo que se hace en cada perfil de via principal: grupos 1, 2 y 4 del plan. */
    static final List<String> DEFAULT_PREVENTIVE_TASK_TYPES =
            List.of("RG-01", "RG-05", "RG-08", "RG-10", "RG-11", "RG-12", "RG-13", "RP-08");

    /**
     * Lo que se hace en cada aislador de seccion: RG-03 del plan. Pide posesion total, asi que sus
     * tareas esperan a un turno FULL (el fin de semana), como el resto del grupo 3.
     */
    static final List<String> DEFAULT_INSULATOR_TASK_TYPES = List.of("RG-03");

    /** Los activos de un tramo que se generan uno a uno; un seccionador no tiene un tipo comun en el plan. */
    private static final Set<CatenaryAssetType> GENERATED_ASSET_TYPES =
            EnumSet.of(CatenaryAssetType.PROFILE, CatenaryAssetType.SECTION_INSULATOR);

    private final MaintenanceTaskRepository repository;
    private final CatenaryAssetRepository assetRepository;
    private final CatenaryDefectRepository defectRepository;
    private final MaintenanceTaskMapper mapper;
    private final MaintenanceLookups lookups;
    private final MaintenanceCodeGenerator codeGenerator;
    private final StatusHistoryService history;
    private final WorkloadEstimator workloadEstimator;
    private final MaterialLineFactory lineFactory;
    private final MaterialStockSynchronizer stock;

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceTaskResponse> findByOrder(UUID orderId) {
        lookups.order(orderId);
        return repository.findByOrderIdOrderBySequenceAsc(orderId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceTaskResponse findById(UUID orderId, UUID taskId) {
        return mapper.toResponse(task(orderId, taskId));
    }

    @Override
    @Transactional
    public MaintenanceTaskResponse create(UUID orderId, MaintenanceTaskRequest request) {
        MaintenanceOrder order = lookups.order(orderId);
        requireAcceptsTasks(order);
        CatenaryAsset asset = request.assetId() == null ? null : lookups.enabledAsset(request.assetId());

        MaintenanceTask task = MaintenanceTask.builder()
                .order(order)
                .sequence(repository.findMaxSequence(orderId) + 1)
                .description(request.description().trim())
                .assignedUser(request.assignedUser())
                .asset(asset)
                .taskTypes(lookups.taskTypes(request.taskTypeCodes()))
                .build();
        if (asset != null && (Boolean.TRUE.equals(request.withChecklist()) || task.isDiagnostic())) {
            attachChecklist(task, asset);
        }
        MaintenanceTask saved = repository.save(task);
        order.getTasks().add(saved);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MaintenanceTaskResponse update(UUID orderId, UUID taskId, MaintenanceTaskUpdateRequest request) {
        return patch(orderId, taskId, MergePatch.of(request));
    }

    @Override
    @Transactional
    public MaintenanceTaskResponse patch(UUID orderId, UUID taskId, MergePatch<MaintenanceTaskUpdateRequest> patch) {
        MaintenanceTask task = task(orderId, taskId);
        MaintenanceTaskUpdateRequest request = patch.values();
        StaleVersionException.check("Task " + task.getSequence() + " of order " + task.getOrder().getCode(), task.getVersion(), request.version());
        PatchRules.requireClearable(patch, CLEARABLE);
        if (!task.isOpen()) {
            throw new InvalidTransitionException("Task " + task.getSequence() + " of order " + task.getOrder().getCode()
                    + " is " + task.getStatus() + " and cannot be changed");
        }
        if (request.description() != null) {
            task.setDescription(PatchRules.requireText(request.description(), "description"));
        }
        PatchRules.set(patch, "assignedUser", request.assignedUser(), task::setAssignedUser);
        if (request.taskTypeCodes() != null || patch.clears("taskTypeCodes")) {
            task.getTaskTypes().clear();
            if (request.taskTypeCodes() != null) {
                task.getTaskTypes().addAll(lookups.taskTypes(request.taskTypeCodes()));
            }
        }
        PatchRules.set(patch, "notes", request.notes(), task::setNotes);
        PatchRules.set(patch, "defectsFound", request.defectsFound(), task::setDefectsFound);
        if (request.photoRefs() != null || patch.clears("photoRefs")) {
            task.setPhotoRefs(request.photoRefs() == null ? new ArrayList<>() : new ArrayList<>(request.photoRefs()));
        }
        return mapper.toResponse(repository.save(task));
    }

    @Override
    @Transactional
    public GeneratePreventiveTasksResponse generatePreventiveTasks(UUID orderId, GeneratePreventiveTasksRequest request) {
        MaintenanceOrder order = lookups.order(orderId);
        if (order.getType() != MaintenanceOrderType.PREVENTIVE) {
            throw new InvalidTransitionException("Only preventive orders generate profile tasks; " + order.getCode() + " is " + order.getType());
        }
        if (order.getStatus() != MaintenanceOrderStatus.DRAFT && order.getStatus() != MaintenanceOrderStatus.PLANNED) {
            throw new InvalidTransitionException("Order " + order.getCode() + " is " + order.getStatus() + "; tasks are generated in DRAFT or PLANNED");
        }
        CatenaryAsset section = order.getAsset();
        if (section.getType() != CatenaryAssetType.TRACK_SECTION) {
            throw new InvalidTransitionException("Order " + order.getCode() + " is not on a track section: nothing to generate profile by profile");
        }
        if (section.getTrackId() == null || section.getStartKp() == null || section.getEndKp() == null) {
            throw new ValidationException("Track section " + section.getCode() + " has no track or kp range");
        }

        Set<CatenaryAssetType> assetTypes = generatedAssetTypes(request.assetTypes());
        // Con tipos pedidos, valen para todo lo generado; sin ellos, cada activo lleva los del plan
        // para el suyo. Para tipos distintos por activo se genera dos veces: es idempotente por activo.
        boolean explicitTypes = request.taskTypeCodes() != null && !request.taskTypeCodes().isEmpty();
        Map<CatenaryAssetType, Set<MaintenanceTaskType>> taskTypesByAsset = new EnumMap<>(CatenaryAssetType.class);
        Set<MaintenanceTaskType> requested = explicitTypes ? lookups.taskTypes(request.taskTypeCodes()) : null;
        for (CatenaryAssetType type : assetTypes) {
            taskTypesByAsset.put(type, explicitTypes ? requested : lookups.taskTypes(defaultTaskTypes(type)));
        }

        List<CatenaryAsset> assets = new ArrayList<>();
        if (assetTypes.contains(CatenaryAssetType.PROFILE)) {
            assets.addAll(assetRepository.findEnabledByTypeOnTrackBetween(
                    CatenaryAssetType.PROFILE, section.getTrackId(), section.getStartKp(), section.getEndKp()));
        }
        if (assetTypes.contains(CatenaryAssetType.SECTION_INSULATOR)) {
            assets.addAll(assetRepository.findEnabledByTypeTouchingSection(
                    CatenaryAssetType.SECTION_INSULATOR, section.getTrackId(), section.getStartKp(), section.getEndKp()));
        }
        // En el orden en que se recorre la via de noche: un aislador entre los perfiles que lo rodean.
        assets.sort(Comparator.comparing(CatenaryAsset::getStartKp).thenComparing(CatenaryAsset::getCode));

        List<UUID> alreadyCovered = repository.findAssetIdsByOrderId(orderId);
        int sequence = repository.findMaxSequence(orderId);
        int created = 0;
        int skippedProfiles = 0;
        int skipped = 0;

        for (CatenaryAsset asset : assets) {
            if (alreadyCovered.contains(asset.getId())) {
                skipped++;
                if (asset.getType() == CatenaryAssetType.PROFILE) {
                    skippedProfiles++;
                }
                continue;
            }
            Set<MaintenanceTaskType> taskTypes = taskTypesByAsset.get(asset.getType());
            MaintenanceTask task = MaintenanceTask.builder()
                    .order(order)
                    .sequence(++sequence)
                    .description("Preventive maintenance of " + describe(asset.getType()) + " " + asset.getName() + " (kp " + asset.getStartKp() + ")")
                    .asset(asset)
                    .taskTypes(new java.util.LinkedHashSet<>(taskTypes))
                    .build();
            if (Boolean.TRUE.equals(request.withChecklist()) || taskTypes.stream().anyMatch(MaintenanceTaskType::getDiagnostic)) {
                attachChecklist(task, asset);
            }
            order.getTasks().add(repository.save(task));
            created++;
        }
        LOGGER.info("Preventive tasks generated for order {}: created={}, skipped={}, assets={} ({})",
                order.getCode(), created, skipped, assets.size(), assetTypes);

        WorkloadEstimate estimate = workloadEstimator.estimate(order);
        return new GeneratePreventiveTasksResponse(created, skippedProfiles, order.getTasks().size(),
                estimate.estimatedMinutes(), estimate.estimatedShifts(), skipped);
    }

    /** Sin tipos de activo, los perfiles, que es lo que hacia antes de admitirlos; un seccionador o un tramo no se generan. */
    private static Set<CatenaryAssetType> generatedAssetTypes(List<CatenaryAssetType> requested) {
        if (requested == null || requested.isEmpty()) {
            return EnumSet.of(CatenaryAssetType.PROFILE);
        }
        Set<CatenaryAssetType> types = EnumSet.copyOf(requested);
        if (!GENERATED_ASSET_TYPES.containsAll(types)) {
            throw new ValidationException("assetTypes: only " + GENERATED_ASSET_TYPES + " are generated one by one along a section, not "
                    + types.stream().filter(type -> !GENERATED_ASSET_TYPES.contains(type)).toList());
        }
        return types;
    }

    private static List<String> defaultTaskTypes(CatenaryAssetType type) {
        return type == CatenaryAssetType.SECTION_INSULATOR ? DEFAULT_INSULATOR_TASK_TYPES : DEFAULT_PREVENTIVE_TASK_TYPES;
    }

    private static String describe(CatenaryAssetType type) {
        return type == CatenaryAssetType.SECTION_INSULATOR ? "section insulator" : "profile";
    }

    @Override
    @Transactional
    public MaintenanceTaskResponse start(UUID orderId, UUID taskId, StartTaskRequest request) {
        MaintenanceTask task = task(orderId, taskId);
        requireOrderInProgress(task.getOrder());
        if (task.getStatus() != MaintenanceTaskStatus.PENDING) {
            throw new InvalidTransitionException("Task " + task.getSequence() + " is " + task.getStatus() + " and cannot be started");
        }
        MaintenanceShift shift = workingShift(task, request.shiftId());
        task.setShift(shift);
        task.setStartedAt(Instant.now());
        task.setStatus(MaintenanceTaskStatus.IN_PROGRESS);
        if (request.assignedUser() != null && !request.assignedUser().isBlank()) {
            task.setAssignedUser(request.assignedUser().trim());
        }
        return mapper.toResponse(repository.save(task));
    }

    @Override
    @Transactional
    public MaintenanceTaskResponse complete(UUID orderId, UUID taskId, CompleteTaskRequest request) {
        MaintenanceTask task = task(orderId, taskId);
        MaintenanceOrder order = task.getOrder();
        requireOrderInProgress(order);
        if (!task.isOpen()) {
            throw new InvalidTransitionException("Task " + task.getSequence() + " is " + task.getStatus() + " and cannot be completed");
        }
        // Los tipos de tarea se aplican ANTES de comprobar el turno: son ellos los que dicen si el
        // trabajo exige posesion total.
        if (request.taskTypeCodes() != null && !request.taskTypeCodes().isEmpty()) {
            task.getTaskTypes().clear();
            task.getTaskTypes().addAll(lookups.taskTypes(request.taskTypeCodes()));
        }
        MaintenanceShift shift = workingShift(task, request.shiftId());
        Instant now = Instant.now();
        if (request.notes() != null) {
            task.setNotes(request.notes());
        }
        if (request.defectsFound() != null) {
            task.setDefectsFound(request.defectsFound());
        }
        if (request.photoRefs() != null) {
            task.setPhotoRefs(new ArrayList<>(request.photoRefs()));
        }
        List<String> unanswered = task.getCheckItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getRequiresMeasure()) && item.getItemResult() == null)
                .map(AbstractChecklistItem::getCode)
                .toList();
        if (!unanswered.isEmpty()) {
            throw new ValidationException("Check items without result: " + String.join(", ", unanswered));
        }

        task.setShift(shift);
        if (task.getStartedAt() == null) {
            task.setStartedAt(now);
        }
        task.setCompletedAt(now);
        task.setStatus(MaintenanceTaskStatus.COMPLETED);

        List<MaintenanceMaterialUsage> used = new ArrayList<>();
        if (request.materials() != null) {
            for (TaskMaterialRequest material : request.materials()) {
                MaintenanceMaterialUsage line = lineFactory.buildLine(order, task, material.materialId(), material.materialCode(),
                        material.warehouseId(), material.quantity(), material.unit(), true);
                // Lo usado en el perfil ya esta consumido: previsto = consumido, y se reserva al final
                // para que el cierre de la orden lo consuma contra stock.
                line.setConsumedQuantity(material.quantity());
                used.add(lineFactory.saveLine(line));
            }
        }

        if (request.inlineDefects() != null) {
            for (InlineDefectRequest inline : request.inlineDefects()) {
                registerInlineDefect(task, shift, inline, request.isWorkComplete(), request.repairPlannedDate(), now);
            }
        }

        MaintenanceTask saved = repository.save(task);
        // Stock lo ultimo, cuando todo lo de aqui ya esta en la base. Una reserva hecha antes de que
        // fallara otro material de la lista (desconocido, repetido) o un conflicto de version se
        // quedaba ACTIVE en stock con su linea deshecha, y el reintento creaba otra linea y otra
        // reserva: la clave de idempotencia sale del id de la linea, y la linea era nueva.
        repository.flush();
        used.forEach(stock::reserve);

        LOGGER.info("Task {} of order {} completed in shift {}", task.getSequence(), order.getCode(), shift.getCode());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MaintenanceTaskResponse cancel(UUID orderId, UUID taskId, CancelTaskRequest request) {
        MaintenanceTask task = task(orderId, taskId);
        if (!task.isOpen()) {
            throw new InvalidTransitionException("Task " + task.getSequence() + " is " + task.getStatus() + " and cannot be cancelled");
        }
        task.setStatus(MaintenanceTaskStatus.CANCELLED);
        task.setNotes(task.getNotes() == null ? "Cancelled: " + request.reason() : task.getNotes() + "\nCancelled: " + request.reason());
        return mapper.toResponse(repository.save(task));
    }

    @Override
    @Transactional
    public MaintenanceTaskResponse updateCheckItem(UUID orderId, UUID taskId, UUID itemId, CheckItemUpdateRequest request) {
        return patchCheckItem(orderId, taskId, itemId, MergePatch.of(request));
    }

    @Override
    @Transactional
    public MaintenanceTaskResponse patchCheckItem(UUID orderId, UUID taskId, UUID itemId, MergePatch<CheckItemUpdateRequest> patch) {
        MaintenanceTask task = task(orderId, taskId);
        if (!task.isOpen()) {
            throw new InvalidTransitionException("Task " + task.getSequence() + " is " + task.getStatus() + "; its check items are frozen");
        }
        MaintenanceTaskCheckItem item = task.getCheckItems().stream()
                .filter(candidate -> candidate.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Check item", itemId));
        ChecklistRules.apply(item, patch);
        return mapper.toResponse(repository.save(task));
    }

    @Override
    @Transactional
    public MaintenanceTaskResponse assignToShift(UUID shiftId, UUID taskId) {
        MaintenanceTask task = repository.findById(taskId).orElseThrow(() -> new NotFoundException("Maintenance task", taskId));
        MaintenanceShift shift = lookups.shift(shiftId);
        if (!ShiftStateMachine.allowsUpdate(shift.getStatus())) {
            throw new InvalidTransitionException("Shift " + shift.getCode() + " is " + shift.getStatus() + " and does not accept tasks");
        }
        if (task.getStatus() != MaintenanceTaskStatus.PENDING) {
            throw new InvalidTransitionException("Only pending tasks can be assigned to a shift");
        }
        ShiftRules.requireSameTrack(shift, task);
        ShiftRules.requireCompatiblePossession(shift, task);
        task.setShift(shift);
        return mapper.toResponse(repository.save(task));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceTaskResponse> findByShift(UUID shiftId, MaintenanceTaskStatus status) {
        lookups.shift(shiftId);
        return repository.findByShiftIdOrderBySequenceAsc(shiftId).stream()
                .filter(task -> status == null || task.getStatus() == status)
                .map(mapper::toResponse)
                .toList();
    }

    private MaintenanceTask task(UUID orderId, UUID taskId) {
        lookups.order(orderId);
        return repository.findByIdAndOrderId(taskId, orderId).orElseThrow(() -> new NotFoundException("Maintenance task", taskId));
    }

    private void attachChecklist(MaintenanceTask task, CatenaryAsset asset) {
        Optional<InspectionTemplate> template = lookups.activeTemplate(asset);
        template.ifPresent(value -> value.getItems().forEach(item -> task.getCheckItems().add(MaintenanceTaskCheckItem.fromTemplate(task, item))));
    }

    /** El turno en que se trabaja: en curso, de la misma via y con una posesion compatible con la tarea. */
    private MaintenanceShift workingShift(MaintenanceTask task, UUID shiftId) {
        MaintenanceShift shift = lookups.shift(shiftId);
        ShiftRules.requireInProgress(shift);
        ShiftRules.requireSameTrack(shift, task);
        ShiftRules.requireCompatiblePossession(shift, task);
        return shift;
    }

    private void registerInlineDefect(MaintenanceTask task, MaintenanceShift shift, InlineDefectRequest inline,
                                      boolean workComplete, java.time.LocalDate repairPlannedDate, Instant now) {
        CatenaryAsset asset = task.getAsset() != null ? task.getAsset() : task.getOrder().getAsset();
        CatenaryDefect defect = CatenaryDefect.builder()
                .code(codeGenerator.nextDefectCode())
                .asset(asset)
                .order(task.getOrder())
                .foundInTask(task)
                .severity(inline.severity())
                .description(inline.description().trim())
                .technicalNotes(inline.technicalNotes())
                .detectedAt(now)
                .correctionType(inline.correctionType())
                .partsReplaced(inline.partsReplaced())
                .photoRefs(new ArrayList<>(task.getPhotoRefs()))
                .build();
        defect.locateAt(asset);
        if (workComplete) {
            defect.setStatus(DefectStatus.RESOLVED);
            defect.setResolvedAt(now);
            defect.setResolvedInShift(shift);
            defect.setResolutionNotes(inline.correctionType() == null ? "Corrected during the shift" : inline.correctionType());
        } else {
            defect.setStatus(DefectStatus.OPEN);
            defect.setRepairPlannedDate(repairPlannedDate);
        }
        CatenaryDefect saved = defectRepository.save(defect);
        history.recordDefectChange(saved, null, saved.getStatus().name(),
                "Found in task " + task.getSequence() + " of order " + task.getOrder().getCode() + " during shift " + shift.getCode());
    }

    private static void requireAcceptsTasks(MaintenanceOrder order) {
        if (order.isTerminal()) {
            throw new InvalidTransitionException("Order " + order.getCode() + " is " + order.getStatus() + " and does not accept tasks");
        }
    }

    private static void requireOrderInProgress(MaintenanceOrder order) {
        if (!order.isInProgress()) {
            throw new InvalidTransitionException("Order " + order.getCode() + " is " + order.getStatus()
                    + ": tasks can only be started or completed while the order is IN_PROGRESS");
        }
    }
}
