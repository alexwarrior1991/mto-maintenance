package com.alejandro.mtomaintenance.application.service.impl;

import static com.alejandro.mtomaintenance.application.service.impl.DomainGuard.domain;
import static com.alejandro.mtomaintenance.application.service.impl.PatchRules.requireClearable;
import static com.alejandro.mtomaintenance.application.service.impl.PatchRules.requireText;
import static com.alejandro.mtomaintenance.application.service.impl.PatchRules.set;
import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.dto.order.AssignOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.CancelOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.CompleteOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.order.PlanOrderRequest;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.StaleVersionException;
import com.alejandro.mtomaintenance.application.exception.UnsyncedMaterialsException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceOrderMapper;
import com.alejandro.mtomaintenance.application.mapper.PageMapper;
import com.alejandro.mtomaintenance.application.service.EntityAuditService;
import com.alejandro.mtomaintenance.application.service.MaintenanceCodeGenerator;
import com.alejandro.mtomaintenance.application.service.MaintenanceOrderService;
import com.alejandro.mtomaintenance.application.service.StatusHistoryService;
import com.alejandro.mtomaintenance.application.service.WorkloadEstimator;
import com.alejandro.mtomaintenance.domain.model.KilometricRange;
import com.alejandro.mtomaintenance.domain.model.OrderStateMachine;
import com.alejandro.mtomaintenance.domain.model.WorkloadEstimate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryDefectRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceInspectionRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceOrderRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.specification.MaintenanceOrderSpecification;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Maquina de estados de las ordenes. Cada transicion comprueba la tabla de {@link OrderStateMachine},
 * aplica sus efectos (fechas reales, reservas y consumos de stock, defectos enlazados) y deja una
 * fila de historial.
 */
@Service
@RequiredArgsConstructor
class MaintenanceOrderServiceImpl implements MaintenanceOrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaintenanceOrderServiceImpl.class);

    /** Lo que un PATCH puede vaciar; titulo y prioridad son obligatorios. */
    private static final Set<String> CLEARABLE = Set.of("description", "plannedDate", "teamId", "assignedUser", "closingNotes",
            "executionPackageId", "trackId", "stationId", "startKp", "endKp", "stockProjectId");

    /** Lo que solo se toca en DRAFT o PLANNED, tambien para vaciarlo. */
    private static final Set<String> RESTRICTED = Set.of("plannedDate", "teamId", "assignedUser", "executionPackageId", "trackId",
            "stationId", "startKp", "endKp", "stockProjectId");

    private final MaintenanceOrderRepository repository;
    private final CatenaryAssetRepository assetRepository;
    private final CatenaryDefectRepository defectRepository;
    private final MaintenanceInspectionRepository inspectionRepository;
    private final MaintenanceOrderMapper mapper;
    private final MaintenanceLookups lookups;
    private final MaintenanceCodeGenerator codeGenerator;
    private final StatusHistoryService history;
    private final WorkloadEstimator workloadEstimator;
    private final MaterialStockSynchronizer stock;
    private final EntityAuditService auditService;

    @Override
    @Transactional
    public MaintenanceOrderResponse create(MaintenanceOrderRequest request) {
        CatenaryAsset asset = lookups.enabledAsset(request.assetId());

        MaintenanceOrder order = MaintenanceOrder.builder()
                .code(codeGenerator.nextOrderCode())
                .title(request.title().trim())
                .description(request.description())
                .type(request.type())
                .priority(priorityFor(request.type(), request.priority()))
                .asset(asset)
                .plannedDate(request.plannedDate())
                .team(request.teamId() == null ? null : lookups.team(request.teamId()))
                .assignedUser(request.assignedUser())
                .stockProjectId(request.stockProjectId())
                .build();
        order.locateAt(asset);

        MaintenanceOrder saved = repository.save(order);
        history.recordOrderChange(saved, null, saved.getStatus().name(), "Order created");
        LOGGER.info("Maintenance order created: code={}, type={}, asset={}", saved.getCode(), saved.getType(), asset.getCode());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public MaintenanceOrderResponse update(UUID id, MaintenanceOrderUpdateRequest request) {
        return patch(id, MergePatch.of(request));
    }

    @Override
    @Transactional
    public MaintenanceOrderResponse patch(UUID id, MergePatch<MaintenanceOrderUpdateRequest> patch) {
        MaintenanceOrder order = lookups.order(id);
        MaintenanceOrderUpdateRequest request = patch.values();
        StaleVersionException.check("Order " + order.getCode(), order.getVersion(), request.version());
        requireClearable(patch, CLEARABLE);
        boolean full = OrderStateMachine.allowsFullUpdate(order.getStatus());

        if (!full && (request.touchesRestrictedFields() || patch.cleared().stream().anyMatch(RESTRICTED::contains))) {
            throw new InvalidTransitionException("Order " + order.getCode() + " is " + order.getStatus()
                    + ": only description, priority and closingNotes can be changed now");
        }
        if (order.isTerminal()) {
            throw new InvalidTransitionException("Order " + order.getCode() + " is " + order.getStatus() + " and cannot be changed");
        }

        set(patch, "description", request.description(), order::setDescription);
        if (request.priority() != null) {
            order.setPriority(priorityFor(order.getType(), request.priority()));
        }
        set(patch, "closingNotes", request.closingNotes(), order::setClosingNotes);
        if (full) {
            if (request.title() != null) {
                order.setTitle(requireText(request.title(), "title"));
            }
            set(patch, "plannedDate", request.plannedDate(), order::setPlannedDate);
            if (patch.clears("teamId")) {
                order.setTeam(null);
            } else if (request.teamId() != null) {
                order.setTeam(lookups.team(request.teamId()));
            }
            set(patch, "assignedUser", request.assignedUser(), order::setAssignedUser);
            set(patch, "executionPackageId", request.executionPackageId(), order::setExecutionPackageId);
            set(patch, "trackId", request.trackId(), order::setTrackId);
            set(patch, "stationId", request.stationId(), order::setStationId);
            set(patch, "startKp", request.startKp(), order::setStartKp);
            set(patch, "endKp", request.endKp(), order::setEndKp);
            set(patch, "stockProjectId", request.stockProjectId(), order::setStockProjectId);
            if (order.getStartKp() != null && order.getEndKp() != null) {
                domain(() -> new KilometricRange(order.getStartKp(), order.getEndKp()));
            }
        }
        return toResponse(repository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceOrderResponse findById(UUID id) {
        return toResponse(lookups.order(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MaintenanceOrderResponse> search(MaintenanceOrderStatus status, MaintenanceOrderType type, MaintenancePriority priority,
                                                         UUID assetId, CatenaryAssetType assetType, Long trackId, Long stationId,
                                                         Long executionPackageId, LocalDate plannedFrom, LocalDate plannedTo,
                                                         Instant actualStartFrom, Instant actualStartTo, String assignedUser,
                                                         UUID teamId, String code, Pageable pageable) {
        Specification<MaintenanceOrder> specification = MaintenanceOrderSpecification.statusEquals(status)
                .and(MaintenanceOrderSpecification.typeEquals(type))
                .and(MaintenanceOrderSpecification.priorityEquals(priority))
                .and(MaintenanceOrderSpecification.assetIdEquals(assetId))
                .and(MaintenanceOrderSpecification.assetTypeEquals(assetType))
                .and(MaintenanceOrderSpecification.trackIdEquals(trackId))
                .and(MaintenanceOrderSpecification.stationIdEquals(stationId))
                .and(MaintenanceOrderSpecification.executionPackageIdEquals(executionPackageId))
                .and(MaintenanceOrderSpecification.plannedBetween(plannedFrom, plannedTo))
                .and(MaintenanceOrderSpecification.actualStartBetween(actualStartFrom, actualStartTo))
                .and(MaintenanceOrderSpecification.assignedUserEquals(assignedUser))
                .and(MaintenanceOrderSpecification.teamIdEquals(teamId))
                .and(MaintenanceOrderSpecification.codeContains(code));
        return PageMapper.toPageResponse(repository.findAll(specification, pageable), this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MaintenanceOrderResponse> findByAsset(UUID assetId, Pageable pageable) {
        lookups.asset(assetId);
        return PageMapper.toPageResponse(repository.findByAssetId(assetId, pageable), this::toResponse);
    }

    @Override
    @Transactional
    public MaintenanceOrderResponse plan(UUID id, PlanOrderRequest request) {
        MaintenanceOrder order = lookups.order(id);
        requireTransition(OrderStateMachine.canPlan(order.getStatus()), order, "planned");
        if (request.plannedDate().isBefore(LocalDate.now(ZoneOffset.UTC))) {
            throw new ValidationException("plannedDate cannot be in the past");
        }
        order.setPlannedDate(request.plannedDate());
        transition(order, MaintenanceOrderStatus.PLANNED, request.comment());

        // Planificar es el momento de bloquear existencias: en DRAFT no se reserva nada.
        stock.reserveAll(order);

        return toResponse(repository.save(order));
    }

    @Override
    @Transactional
    public MaintenanceOrderResponse assign(UUID id, AssignOrderRequest request) {
        MaintenanceOrder order = lookups.order(id);
        requireTransition(OrderStateMachine.canAssign(order.getStatus()), order, "assigned");
        if (request.teamId() != null) {
            order.setTeam(lookups.team(request.teamId()));
        }
        if (request.assignedUser() != null && !request.assignedUser().isBlank()) {
            order.setAssignedUser(request.assignedUser().trim());
        }
        if (order.getStatus() == MaintenanceOrderStatus.IN_PROGRESS) {
            // Reasignar a media ejecucion no cambia de estado; queda en el historial con comentario.
            history.recordOrderChange(order, order.getStatus().name(), order.getStatus().name(),
                    request.comment() == null ? "Reassigned" : "Reassigned: " + request.comment());
        } else {
            transition(order, MaintenanceOrderStatus.ASSIGNED, request.comment());
        }
        return toResponse(repository.save(order));
    }

    @Override
    @Transactional
    public MaintenanceOrderResponse start(UUID id, String comment) {
        MaintenanceOrder order = lookups.order(id);
        requireTransition(OrderStateMachine.canStart(order.getStatus(), order.getType()), order, "started");
        if (order.getActualStartDate() == null) {
            order.setActualStartDate(Instant.now());
        }
        // URGENT arranca sin planificar: sus materiales se reservan aqui.
        transition(order, MaintenanceOrderStatus.IN_PROGRESS, comment);
        stock.reserveAll(order);
        return toResponse(repository.save(order));
    }

    /**
     * {@code noRollbackFor}: cuando se rechaza por materiales sin sincronizar, stock ya ha consumido o
     * liberado las otras lineas, y lo que cada una guarda de eso tiene que quedar. Hasta ese punto no
     * se toca nada mas de la orden, asi que el rechazo no deja nada a medias en ella.
     */
    @Override
    @Transactional(noRollbackFor = UnsyncedMaterialsException.class)
    public MaintenanceOrderResponse complete(UUID id, CompleteOrderRequest request) {
        MaintenanceOrder order = lookups.order(id);
        requireTransition(OrderStateMachine.canComplete(order.getStatus()), order, "completed");

        if (order.getActualStartDate() == null) {
            throw new InvalidTransitionException("Order " + order.getCode() + " has no actual start date and cannot be completed");
        }
        List<MaintenanceTask> tasks = order.getTasks();
        if (tasks.stream().anyMatch(MaintenanceTask::isOpen)) {
            throw new InvalidTransitionException("Order " + order.getCode() + " still has pending or in-progress tasks");
        }
        // Las notas se aplican al final: un rechazo por materiales no deshace la transaccion.
        String closingNotes = request.closingNotes() != null && !request.closingNotes().isBlank()
                ? request.closingNotes().trim() : order.getClosingNotes();
        boolean anyCompletedTask = tasks.stream().anyMatch(task -> task.getStatus() == MaintenanceTaskStatus.COMPLETED);
        if (!anyCompletedTask && (closingNotes == null || closingNotes.isBlank())) {
            throw new InvalidTransitionException("Order " + order.getCode()
                    + " needs at least one completed task or closing notes to be completed");
        }
        if (order.getType() == MaintenanceOrderType.INSPECTION && !inspectionRepository.existsByOriginOrderId(order.getId())) {
            throw new InvalidTransitionException("Inspection order " + order.getCode() + " has no inspection recorded yet");
        }

        // Consumo de materiales contra stock. Una linea que sigue FAILED o REJECTED tras intentarlo
        // bloquea el cierre salvo force (supervision), que lo deja escrito en las notas de cierre. Lo
        // que stock hizo con las demas queda aunque se rechace: repetir no lo vuelve a consumir.
        order.getMaterials().forEach(stock::consume);
        List<MaintenanceMaterialUsage> unsynced = order.getMaterials().stream().filter(MaintenanceMaterialUsage::isSyncFailed).toList();
        if (!unsynced.isEmpty()) {
            if (!request.isForced()) {
                throw new UnsyncedMaterialsException("Order " + order.getCode() + " has " + unsynced.size()
                        + " material line(s) not synchronized with stock; retry /sync or complete with force");
            }
            String note = "Completed with " + unsynced.size() + " material line(s) pending stock synchronization";
            closingNotes = closingNotes == null ? note : closingNotes + "\n" + note;
        }
        order.setClosingNotes(closingNotes);

        if (order.getActualEndDate() == null) {
            order.setActualEndDate(Instant.now());
        }
        transition(order, MaintenanceOrderStatus.COMPLETED, request.comment());

        if (order.getType() == MaintenanceOrderType.PREVENTIVE) {
            CatenaryAsset asset = order.getAsset();
            asset.setLastPreventiveCompletedAt(order.getActualEndDate());
            assetRepository.save(asset);
            // Los perfiles trabajados tambien quedan revisados: es lo que mide el avance.
            tasks.stream()
                    .filter(task -> task.getStatus() == MaintenanceTaskStatus.COMPLETED && task.getAsset() != null)
                    .forEach(task -> {
                        task.getAsset().setLastPreventiveCompletedAt(task.getCompletedAt());
                        assetRepository.save(task.getAsset());
                    });
        }
        return toResponse(repository.save(order));
    }

    @Override
    @Transactional
    public MaintenanceOrderResponse cancel(UUID id, CancelOrderRequest request) {
        MaintenanceOrder order = lookups.order(id);
        requireTransition(OrderStateMachine.canCancel(order.getStatus()), order, "cancelled");

        order.setCancellationReason(request.reason().trim());
        order.getTasks().stream().filter(MaintenanceTask::isOpen).forEach(task -> task.setStatus(MaintenanceTaskStatus.CANCELLED));
        order.getMaterials().forEach(stock::release);

        // Un defecto que esperaba a esta orden vuelve a estar abierto; conserva el enlace como historia.
        for (CatenaryDefect defect : defectRepository.findByOrderIdAndStatus(order.getId(), DefectStatus.IN_PROGRESS)) {
            defect.setStatus(DefectStatus.OPEN);
            defectRepository.save(defect);
            history.recordDefectChange(defect, DefectStatus.IN_PROGRESS.name(), DefectStatus.OPEN.name(),
                    "Order " + order.getCode() + " cancelled: " + request.reason());
        }

        transition(order, MaintenanceOrderStatus.CANCELLED, request.reason());
        return toResponse(repository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EntityRevisionResponse<MaintenanceOrderResponse>> findRevisions(UUID id, Pageable pageable) {
        return auditService.findRevisions(MaintenanceOrder.class, id, this::toResponse, pageable);
    }

    private void transition(MaintenanceOrder order, MaintenanceOrderStatus target, String comment) {
        MaintenanceOrderStatus previous = order.getStatus();
        order.setStatus(target);
        history.recordOrderChange(order, previous.name(), target.name(), comment);
        LOGGER.info("Maintenance order {}: {} -> {}", order.getCode(), previous, target);
    }

    private static void requireTransition(boolean allowed, MaintenanceOrder order, String action) {
        if (!allowed) {
            throw new InvalidTransitionException("Order " + order.getCode() + " is " + order.getStatus() + " and cannot be " + action);
        }
    }

    /** URGENT nace y se queda CRITICAL: es el correctivo de emergencia. */
    private static MaintenancePriority priorityFor(MaintenanceOrderType type, MaintenancePriority requested) {
        if (type == MaintenanceOrderType.URGENT) {
            return MaintenancePriority.CRITICAL;
        }
        return requested == null ? MaintenancePriority.MEDIUM : requested;
    }

    MaintenanceOrderResponse toResponse(MaintenanceOrder order) {
        List<MaintenanceTask> tasks = order.getTasks();
        int completed = (int) tasks.stream().filter(task -> task.getStatus() == MaintenanceTaskStatus.COMPLETED).count();
        WorkloadEstimate estimate = workloadEstimator.estimate(order);
        return mapper.toResponse(order, tasks.size(), completed, estimate.estimatedMinutes(), estimate.estimatedShifts());
    }
}
