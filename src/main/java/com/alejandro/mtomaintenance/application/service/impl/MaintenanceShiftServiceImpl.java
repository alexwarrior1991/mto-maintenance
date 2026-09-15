package com.alejandro.mtomaintenance.application.service.impl;

import static com.alejandro.mtomaintenance.application.service.impl.DomainGuard.domain;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.dto.shift.CancelShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.CloseShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftResponse;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportRowResponse;
import com.alejandro.mtomaintenance.application.dto.shift.StartShiftRequest;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.application.mapper.CatenaryAssetMapper;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceShiftMapper;
import com.alejandro.mtomaintenance.application.mapper.PageMapper;
import com.alejandro.mtomaintenance.application.service.EntityAuditService;
import com.alejandro.mtomaintenance.application.service.MaintenanceCodeGenerator;
import com.alejandro.mtomaintenance.application.service.MaintenanceShiftService;
import com.alejandro.mtomaintenance.domain.model.KilometricRange;
import com.alejandro.mtomaintenance.domain.model.ShiftStateMachine;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryDefectRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceMaterialUsageRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceShiftRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.specification.MaintenanceShiftSpecification;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class MaintenanceShiftServiceImpl implements MaintenanceShiftService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaintenanceShiftServiceImpl.class);

    private final MaintenanceShiftRepository repository;
    private final MaintenanceTaskRepository taskRepository;
    private final CatenaryDefectRepository defectRepository;
    private final MaintenanceMaterialUsageRepository materialRepository;
    private final MaintenanceShiftMapper mapper;
    private final CatenaryAssetMapper assetMapper;
    private final MaintenanceLookups lookups;
    private final MaintenanceCodeGenerator codeGenerator;
    private final EntityAuditService auditService;

    @Override
    @Transactional
    public MaintenanceShiftResponse create(MaintenanceShiftRequest request) {
        MaintenanceShift shift = MaintenanceShift.builder()
                .code(codeGenerator.nextShiftCode())
                .shiftDate(request.shiftDate())
                .team(request.teamId() == null ? null : lookups.team(request.teamId()))
                .baseName(request.baseName())
                .vehicle(request.vehicle())
                .possessionType(request.possessionType())
                .plannedStart(request.plannedStart())
                .plannedEnd(request.plannedEnd())
                .blockingDisconnectors(disconnectors(request.blockingDisconnectorIds()))
                .earthingPoints(request.earthingPoints())
                .parkingPlace(request.parkingPlace())
                .executionPackageId(request.executionPackageId())
                .trackIds(new LinkedHashSet<>(request.trackIds()))
                .startKp(request.startKp())
                .endKp(request.endKp())
                .personnel(request.personnel())
                .measurementEquipment(request.measurementEquipment())
                .observations(request.observations())
                .build();
        if (shift.getTeam() != null) {
            if (shift.getBaseName() == null) {
                shift.setBaseName(shift.getTeam().getBaseName());
            }
            if (shift.getVehicle() == null) {
                shift.setVehicle(shift.getTeam().getVehicle());
            }
        }
        validate(shift);
        MaintenanceShift saved = repository.save(shift);
        LOGGER.info("Shift created: code={}, date={}, tracks={}, possession={}", saved.getCode(), saved.getShiftDate(), saved.getTrackIds(), saved.getPossessionType());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MaintenanceShiftResponse update(UUID id, MaintenanceShiftUpdateRequest request) {
        MaintenanceShift shift = lookups.shift(id);
        if (!ShiftStateMachine.allowsUpdate(shift.getStatus())) {
            throw new InvalidTransitionException("Shift " + shift.getCode() + " is " + shift.getStatus() + " and cannot be changed");
        }
        if (request.shiftDate() != null) {
            shift.setShiftDate(request.shiftDate());
        }
        if (request.teamId() != null) {
            shift.setTeam(lookups.team(request.teamId()));
        }
        if (request.baseName() != null) {
            shift.setBaseName(request.baseName());
        }
        if (request.vehicle() != null) {
            shift.setVehicle(request.vehicle());
        }
        if (request.possessionType() != null) {
            shift.setPossessionType(request.possessionType());
        }
        if (request.plannedStart() != null) {
            shift.setPlannedStart(request.plannedStart());
        }
        if (request.plannedEnd() != null) {
            shift.setPlannedEnd(request.plannedEnd());
        }
        if (request.blockingDisconnectorIds() != null) {
            // Se sustituye el conjunto entero: null = no tocar, vacio = ninguno abierto.
            shift.getBlockingDisconnectors().clear();
            shift.getBlockingDisconnectors().addAll(disconnectors(request.blockingDisconnectorIds()));
        }
        if (request.earthingPoints() != null) {
            shift.setEarthingPoints(request.earthingPoints());
        }
        if (request.parkingPlace() != null) {
            shift.setParkingPlace(request.parkingPlace());
        }
        if (request.executionPackageId() != null) {
            shift.setExecutionPackageId(request.executionPackageId());
        }
        if (request.trackIds() != null) {
            if (request.trackIds().isEmpty()) {
                throw new ValidationException("A shift must cover at least one track");
            }
            shift.getTrackIds().clear();
            shift.getTrackIds().addAll(request.trackIds());
        }
        if (request.startKp() != null) {
            shift.setStartKp(request.startKp());
        }
        if (request.endKp() != null) {
            shift.setEndKp(request.endKp());
        }
        if (request.personnel() != null) {
            shift.setPersonnel(request.personnel());
        }
        if (request.measurementEquipment() != null) {
            shift.setMeasurementEquipment(request.measurementEquipment());
        }
        if (request.observations() != null) {
            shift.setObservations(request.observations());
        }
        validate(shift);
        return mapper.toResponse(repository.save(shift));
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceShiftResponse findById(UUID id) {
        return mapper.toResponse(lookups.shift(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MaintenanceShiftResponse> search(LocalDate date, LocalDate dateFrom, LocalDate dateTo, UUID teamId, Long trackId,
                                                         Long executionPackageId, ShiftStatus status, PossessionType possessionType,
                                                         Pageable pageable) {
        LocalDate from = date != null ? date : dateFrom;
        LocalDate to = date != null ? date : dateTo;
        Specification<MaintenanceShift> specification = MaintenanceShiftSpecification.dateBetween(from, to)
                .and(MaintenanceShiftSpecification.teamIdEquals(teamId))
                .and(MaintenanceShiftSpecification.worksOnTrack(trackId))
                .and(MaintenanceShiftSpecification.executionPackageIdEquals(executionPackageId))
                .and(MaintenanceShiftSpecification.statusEquals(status))
                .and(MaintenanceShiftSpecification.possessionTypeEquals(possessionType));
        return PageMapper.toPageResponse(repository.findAll(specification, pageable), mapper::toResponse);
    }

    @Override
    @Transactional
    public MaintenanceShiftResponse start(UUID id, StartShiftRequest request) {
        MaintenanceShift shift = lookups.shift(id);
        if (!ShiftStateMachine.canStart(shift.getStatus())) {
            throw new InvalidTransitionException("Shift " + shift.getCode() + " is " + shift.getStatus() + " and cannot be started");
        }
        shift.setActualStart(request.actualStart() != null ? request.actualStart() : Instant.now());
        if (request.voltageCutoffAt() != null) {
            shift.setVoltageCutoffAt(request.voltageCutoffAt());
        }
        shift.setStatus(ShiftStatus.IN_PROGRESS);
        LOGGER.info("Shift {} started at {}", shift.getCode(), shift.getActualStart());
        return mapper.toResponse(repository.save(shift));
    }

    @Override
    @Transactional
    public MaintenanceShiftResponse close(UUID id, CloseShiftRequest request) {
        MaintenanceShift shift = lookups.shift(id);
        if (!ShiftStateMachine.canClose(shift.getStatus())) {
            throw new InvalidTransitionException("Shift " + shift.getCode() + " is " + shift.getStatus() + " and cannot be closed");
        }
        Instant end = request.actualEnd() != null ? request.actualEnd() : Instant.now();
        if (shift.getActualStart() == null || end.isBefore(shift.getActualStart())) {
            throw new ValidationException("actualEnd must be after the actual start of the shift");
        }
        if (request.voltageCutoffAt() != null) {
            shift.setVoltageCutoffAt(request.voltageCutoffAt());
        }
        shift.setActualEnd(end);
        // Tiempo neto: desde el corte de tension (o el inicio real si no consta) hasta el final.
        Instant workStart = shift.getVoltageCutoffAt() != null && !shift.getVoltageCutoffAt().isBefore(shift.getActualStart())
                ? shift.getVoltageCutoffAt() : shift.getActualStart();
        shift.setNetWorkMinutes(request.netWorkMinutes() != null
                ? request.netWorkMinutes()
                : (int) Duration.between(workStart, end).toMinutes());
        if (request.observations() != null) {
            shift.setObservations(request.observations());
        }

        // Lo que no se termino en este turno vuelve a la cola de la orden, sin cancelarlo.
        releaseOpenTasks(shift);
        shift.setStatus(ShiftStatus.CLOSED);
        LOGGER.info("Shift {} closed: net minutes={}", shift.getCode(), shift.getNetWorkMinutes());
        return mapper.toResponse(repository.save(shift));
    }

    @Override
    @Transactional
    public MaintenanceShiftResponse cancel(UUID id, CancelShiftRequest request) {
        MaintenanceShift shift = lookups.shift(id);
        if (!ShiftStateMachine.canCancel(shift.getStatus())) {
            throw new InvalidTransitionException("Shift " + shift.getCode() + " is " + shift.getStatus() + " and cannot be cancelled");
        }
        releaseOpenTasks(shift);
        shift.setObservations(shift.getObservations() == null ? "Cancelled: " + request.reason() : shift.getObservations() + "\nCancelled: " + request.reason());
        shift.setStatus(ShiftStatus.CANCELLED);
        return mapper.toResponse(repository.save(shift));
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftReportResponse report(UUID id) {
        MaintenanceShift shift = lookups.shift(id);
        List<MaintenanceTask> tasks = taskRepository.findByShiftIdOrderBySequenceAsc(id).stream()
                .sorted(Comparator
                        .comparing((MaintenanceTask task) -> task.getAsset() == null ? BigDecimal.ZERO : nvl(task.getAsset().getStartKp()))
                        .thenComparing(MaintenanceTask::getSequence))
                .toList();
        List<UUID> taskIds = tasks.stream().map(MaintenanceTask::getId).toList();
        Map<UUID, List<CatenaryDefect>> defectsByTask = taskIds.isEmpty() ? Map.of()
                : defectRepository.findByFoundInTaskIdIn(taskIds).stream().collect(Collectors.groupingBy(defect -> defect.getFoundInTask().getId()));
        Map<UUID, List<MaintenanceMaterialUsage>> materialsByTask = taskIds.isEmpty() ? Map.of()
                : materialRepository.findByTaskIdIn(taskIds).stream().collect(Collectors.groupingBy(usage -> usage.getTask().getId()));

        List<ShiftReportRowResponse> rows = new java.util.ArrayList<>();
        int number = 0;
        for (MaintenanceTask task : tasks) {
            CatenaryAsset asset = task.getAsset();
            List<CatenaryDefect> defects = defectsByTask.getOrDefault(task.getId(), List.of());
            CatenaryDefect pending = defects.stream()
                    .filter(defect -> defect.getStatus() == DefectStatus.OPEN || defect.getStatus() == DefectStatus.IN_PROGRESS)
                    .findFirst().orElse(null);
            rows.add(new ShiftReportRowResponse(
                    ++number,
                    task.getId(),
                    task.getOrder().getCode(),
                    task.getOrder().getExecutionPackageId(),
                    asset != null && asset.getTrackId() != null ? asset.getTrackId() : task.getOrder().getTrackId(),
                    asset == null ? null : asset.getCode(),
                    asset == null ? null : asset.getName(),
                    asset == null ? null : asset.getStartKp(),
                    asset == null ? null : asset.getSectioning(),
                    task.getTaskTypes().stream().sorted(Comparator.comparing(MaintenanceTaskType::getOrderIndex)).map(MaintenanceTaskType::getCode).toList(),
                    task.getNotes(),
                    task.getDefectsFound(),
                    materialsByTask.getOrDefault(task.getId(), List.of()).stream()
                            .map(usage -> usage.getConsumedQuantity() + " " + usage.getUnit() + " " + usage.getMaterialCode())
                            .toList(),
                    task.getStartedAt(),
                    task.getCompletedAt(),
                    task.getStatus(),
                    task.getStatus() == MaintenanceTaskStatus.COMPLETED && pending == null,
                    pending == null ? null : pending.getRepairPlannedDate(),
                    task.getPhotoRefs()
            ));
        }
        int completed = (int) tasks.stream().filter(task -> task.getStatus() == MaintenanceTaskStatus.COMPLETED).count();
        int pendingTasks = (int) tasks.stream().filter(MaintenanceTask::isOpen).count();
        int defectsFound = defectsByTask.values().stream().mapToInt(List::size).sum();
        int defectsResolved = (int) defectRepository.countByResolvedInShiftId(id);
        int profilesReviewed = (int) profileAssets(tasks, MaintenanceTaskStatus.COMPLETED).count();
        return new ShiftReportResponse(mapper.toResponse(shift), completed, pendingTasks, profilesReviewed, defectsFound, defectsResolved, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatenaryAssetSummaryResponse> profiles(UUID id, MaintenanceTaskStatus status) {
        lookups.shift(id);
        return profileAssets(taskRepository.findByShiftIdOrderBySequenceAsc(id), status == null ? MaintenanceTaskStatus.COMPLETED : status)
                .map(assetMapper::toSummary)
                .toList();
    }

    /** Perfiles de las tareas en un estado, por kp y sin repetir (dos tareas sobre el mismo perfil cuentan una vez). */
    private static Stream<CatenaryAsset> profileAssets(List<MaintenanceTask> tasks, MaintenanceTaskStatus status) {
        return tasks.stream()
                .filter(task -> task.getStatus() == status)
                .map(MaintenanceTask::getAsset)
                .filter(asset -> asset != null && asset.getType() == CatenaryAssetType.PROFILE)
                .collect(Collectors.toMap(CatenaryAsset::getId, asset -> asset, (first, second) -> first, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing((CatenaryAsset asset) -> nvl(asset.getStartKp())).thenComparing(CatenaryAsset::getCode));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EntityRevisionResponse<MaintenanceShiftResponse>> findRevisions(UUID id, Pageable pageable) {
        return auditService.findRevisions(MaintenanceShift.class, id, mapper::toResponse, pageable);
    }

    private void releaseOpenTasks(MaintenanceShift shift) {
        for (MaintenanceTask task : taskRepository.findByShiftIdAndStatusIn(shift.getId(), EnumSet.of(MaintenanceTaskStatus.PENDING, MaintenanceTaskStatus.IN_PROGRESS))) {
            task.setShift(null);
            task.setStatus(MaintenanceTaskStatus.PENDING);
            taskRepository.save(task);
        }
    }

    /** Resuelve los ids a activos y exige que todos sean seccionadores. */
    private Set<CatenaryAsset> disconnectors(Set<UUID> ids) {
        Set<CatenaryAsset> resolved = new LinkedHashSet<>();
        if (ids == null) {
            return resolved;
        }
        for (UUID id : ids) {
            if (id == null) {
                continue;
            }
            CatenaryAsset asset = lookups.asset(id);
            if (asset.getType() != CatenaryAssetType.DISCONNECTOR) {
                throw new ValidationException("Asset " + asset.getCode() + " is not a disconnector");
            }
            resolved.add(asset);
        }
        return resolved;
    }

    private static void validate(MaintenanceShift shift) {
        if (shift.getStartKp() != null && shift.getEndKp() != null) {
            domain(() -> new KilometricRange(shift.getStartKp(), shift.getEndKp()));
        }
        if (shift.getPlannedStart() != null && shift.getPlannedEnd() != null && !shift.getPlannedStart().isBefore(shift.getPlannedEnd())) {
            throw new ValidationException("plannedStart must be before plannedEnd");
        }
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
