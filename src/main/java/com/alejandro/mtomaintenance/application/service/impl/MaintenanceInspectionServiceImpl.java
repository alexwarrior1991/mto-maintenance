package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.CheckItemUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.CreateCorrectiveOrderRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.CreateDefectFromInspectionRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.application.exception.InspectionException;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.mapper.CatenaryDefectMapper;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceInspectionMapper;
import com.alejandro.mtomaintenance.application.mapper.PageMapper;
import com.alejandro.mtomaintenance.application.service.EntityAuditService;
import com.alejandro.mtomaintenance.application.service.MaintenanceCodeGenerator;
import com.alejandro.mtomaintenance.application.service.MaintenanceInspectionService;
import com.alejandro.mtomaintenance.application.service.MaintenanceOrderService;
import com.alejandro.mtomaintenance.application.service.StatusHistoryService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CheckItemResult;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspection;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspectionItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryDefectRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceInspectionRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceOrderRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.specification.MaintenanceInspectionSpecification;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class MaintenanceInspectionServiceImpl implements MaintenanceInspectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaintenanceInspectionServiceImpl.class);

    private final MaintenanceInspectionRepository repository;
    private final CatenaryDefectRepository defectRepository;
    private final MaintenanceOrderRepository orderRepository;
    private final MaintenanceInspectionMapper mapper;
    private final CatenaryDefectMapper defectMapper;
    private final MaintenanceLookups lookups;
    private final MaintenanceCodeGenerator codeGenerator;
    private final StatusHistoryService history;
    private final MaintenanceOrderService orderService;
    private final EntityAuditService auditService;

    @Override
    @Transactional
    public MaintenanceInspectionResponse create(MaintenanceInspectionRequest request) {
        CatenaryAsset asset = lookups.enabledAsset(request.assetId());
        MaintenanceOrder originOrder = request.originOrderId() == null ? null : lookups.order(request.originOrderId());
        if (originOrder != null && originOrder.isTerminal()) {
            throw new InvalidTransitionException("Order " + originOrder.getCode() + " is " + originOrder.getStatus() + " and cannot receive inspections");
        }

        MaintenanceInspection inspection = MaintenanceInspection.builder()
                .code(codeGenerator.nextInspectionCode())
                .asset(asset)
                .executionPackageId(asset.getExecutionPackageId())
                .trackId(asset.getTrackId())
                .stationId(asset.getStationId())
                .kp(request.kp() != null ? request.kp() : asset.getStartKp())
                .inspectionDate(request.inspectionDate())
                .inspector(request.inspector())
                .inspectionKind(request.inspectionKind() == null ? com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionKind.VISUAL : request.inspectionKind())
                .result(request.result())
                .description(request.description())
                .detectedDefects(request.detectedDefects())
                .recommendedActions(request.recommendedActions())
                .originOrder(originOrder)
                .shift(request.shiftId() == null ? null : lookups.shift(request.shiftId()))
                .build();

        // La plantilla activa del tipo de activo: un seccionador y un aislador de seccion tienen la
        // suya, distinta de la del perfil.
        lookups.activeTemplate(asset).ifPresent(template -> {
            inspection.setTemplate(template);
            template.getItems().forEach(item -> inspection.getItems().add(MaintenanceInspectionItem.fromTemplate(inspection, item)));
        });

        MaintenanceInspection saved = repository.save(inspection);
        LOGGER.info("Inspection created: code={}, asset={}, result={}", saved.getCode(), asset.getCode(), saved.getResult());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MaintenanceInspectionResponse update(UUID id, MaintenanceInspectionUpdateRequest request) {
        MaintenanceInspection inspection = inspection(id);
        if (request.inspectionDate() != null) {
            inspection.setInspectionDate(request.inspectionDate());
        }
        if (request.inspector() != null) {
            inspection.setInspector(request.inspector());
        }
        if (request.inspectionKind() != null) {
            inspection.setInspectionKind(request.inspectionKind());
        }
        if (request.result() != null) {
            inspection.setResult(request.result());
        }
        if (request.description() != null) {
            inspection.setDescription(request.description());
        }
        if (request.detectedDefects() != null) {
            inspection.setDetectedDefects(request.detectedDefects());
        }
        if (request.recommendedActions() != null) {
            inspection.setRecommendedActions(request.recommendedActions());
        }
        if (request.kp() != null) {
            inspection.setKp(request.kp());
        }
        validateResult(inspection);
        return mapper.toResponse(repository.save(inspection));
    }

    @Override
    @Transactional
    public MaintenanceInspectionResponse updateItem(UUID id, UUID itemId, CheckItemUpdateRequest request) {
        MaintenanceInspection inspection = inspection(id);
        MaintenanceInspectionItem item = inspection.getItems().stream()
                .filter(candidate -> candidate.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Inspection item", itemId));
        ChecklistRules.apply(item, request);
        validateResult(inspection);
        return mapper.toResponse(repository.save(inspection));
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceInspectionResponse findById(UUID id) {
        return mapper.toResponse(inspection(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MaintenanceInspectionResponse> search(InspectionResult result, UUID assetId, CatenaryAssetType assetType, Long trackId,
                                                              Long stationId, Long executionPackageId, LocalDate from, LocalDate to,
                                                              String inspector, UUID originOrderId, Pageable pageable) {
        Specification<MaintenanceInspection> specification = MaintenanceInspectionSpecification.resultEquals(result)
                .and(MaintenanceInspectionSpecification.assetIdEquals(assetId))
                .and(MaintenanceInspectionSpecification.assetTypeEquals(assetType))
                .and(MaintenanceInspectionSpecification.trackIdEquals(trackId))
                .and(MaintenanceInspectionSpecification.stationIdEquals(stationId))
                .and(MaintenanceInspectionSpecification.executionPackageIdEquals(executionPackageId))
                .and(MaintenanceInspectionSpecification.dateBetween(from, to))
                .and(MaintenanceInspectionSpecification.inspectorEquals(inspector))
                .and(MaintenanceInspectionSpecification.originOrderIdEquals(originOrderId));
        return PageMapper.toPageResponse(repository.findAll(specification, pageable), mapper::toResponse);
    }

    @Override
    @Transactional
    public CatenaryDefectResponse createDefect(UUID id, CreateDefectFromInspectionRequest request) {
        MaintenanceInspection inspection = inspection(id);
        if (inspection.getGeneratedDefect() != null) {
            // Idempotente: repetir la llamada devuelve lo ya creado, no un error.
            return defectMapper.toResponse(inspection.getGeneratedDefect());
        }
        if (inspection.getResult() == InspectionResult.OK) {
            throw new InspectionException("Inspection " + inspection.getCode() + " is OK: there is no defect to record");
        }
        if (inspection.getResult() == InspectionResult.MINOR_DEFECT && !request.isForced()) {
            throw new InspectionException("Inspection " + inspection.getCode() + " has a minor defect; pass force=true to record it as a defect");
        }

        CatenaryAsset asset = inspection.getAsset();
        CatenaryDefect defect = CatenaryDefect.builder()
                .code(codeGenerator.nextDefectCode())
                .asset(asset)
                .inspection(inspection)
                .severity(request.severity() != null ? request.severity() : severityFor(inspection.getResult()))
                .description(descriptionFor(inspection, request.description()))
                .technicalNotes(request.technicalNotes() != null ? request.technicalNotes() : inspection.getRecommendedActions())
                .detectedAt(inspection.getInspectionDate().atStartOfDay(ZoneOffset.UTC).toInstant())
                .build();
        defect.locateAt(asset);
        if (inspection.getKp() != null) {
            defect.setStartKp(inspection.getKp());
            defect.setEndKp(inspection.getKp());
        }
        CatenaryDefect saved = defectRepository.save(defect);
        history.recordDefectChange(saved, null, saved.getStatus().name(), "Created from inspection " + inspection.getCode());

        inspection.setGeneratedDefect(saved);
        repository.save(inspection);
        LOGGER.info("Defect {} created from inspection {}", saved.getCode(), inspection.getCode());
        return defectMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MaintenanceOrderResponse createCorrectiveOrder(UUID id, CreateCorrectiveOrderRequest request) {
        MaintenanceInspection inspection = inspection(id);
        if (inspection.getGeneratedOrder() != null) {
            return orderService.findById(inspection.getGeneratedOrder().getId());
        }
        if (inspection.getResult() == InspectionResult.OK) {
            throw new InspectionException("Inspection " + inspection.getCode() + " is OK: there is nothing to correct");
        }

        boolean unsafe = inspection.getResult() == InspectionResult.UNSAFE;
        MaintenanceOrderType type = unsafe ? MaintenanceOrderType.URGENT : MaintenanceOrderType.CORRECTIVE;
        MaintenancePriority priority = unsafe ? MaintenancePriority.CRITICAL
                : request.priority() != null ? request.priority()
                : inspection.getResult() == InspectionResult.MAJOR_DEFECT ? MaintenancePriority.HIGH : MaintenancePriority.MEDIUM;
        String title = request.title() != null && !request.title().isBlank() ? request.title()
                : "Corrective work after inspection " + inspection.getCode() + " on " + inspection.getAsset().getCode();
        String description = request.description() != null ? request.description()
                : joinNonBlank(inspection.getDetectedDefects(), inspection.getRecommendedActions());

        MaintenanceOrderResponse created = orderService.create(new MaintenanceOrderRequest(
                title, description, type, priority, inspection.getAsset().getId(), request.plannedDate(), request.teamId(), null, null));
        MaintenanceOrder order = orderRepository.findById(created.id()).orElseThrow();
        order.setOriginInspection(inspection);

        CatenaryDefect defect = inspection.getGeneratedDefect();
        if (defect != null && !DefectStatus.CLOSED.equals(defect.getStatus()) && !DefectStatus.DISCARDED.equals(defect.getStatus())) {
            order.setOriginDefect(defect);
            if (defect.getStatus() == DefectStatus.OPEN) {
                defect.setOrder(order);
                defect.setStatus(DefectStatus.IN_PROGRESS);
                defectRepository.save(defect);
                history.recordDefectChange(defect, DefectStatus.OPEN.name(), DefectStatus.IN_PROGRESS.name(), "Linked to corrective order " + order.getCode());
            }
        }
        orderRepository.save(order);
        inspection.setGeneratedOrder(order);
        repository.save(inspection);
        LOGGER.info("Corrective order {} created from inspection {}", order.getCode(), inspection.getCode());
        return orderService.findById(order.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EntityRevisionResponse<MaintenanceInspectionResponse>> findRevisions(UUID id, Pageable pageable) {
        return auditService.findRevisions(MaintenanceInspection.class, id, mapper::toResponse, pageable);
    }

    private MaintenanceInspection inspection(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Maintenance inspection", id));
    }

    /** Un resultado OK no puede convivir con un punto en DEFECT. */
    private static void validateResult(MaintenanceInspection inspection) {
        if (inspection.getResult() == InspectionResult.OK) {
            List<String> defective = inspection.getItems().stream()
                    .filter(item -> item.getItemResult() == CheckItemResult.DEFECT)
                    .map(MaintenanceInspectionItem::getCode)
                    .toList();
            if (!defective.isEmpty()) {
                throw new InspectionException("Inspection " + inspection.getCode() + " cannot be OK with defective items: "
                        + String.join(", ", defective));
            }
        }
    }

    private static DefectSeverity severityFor(InspectionResult result) {
        return switch (result) {
            case UNSAFE -> DefectSeverity.CRITICAL;
            case MAJOR_DEFECT -> DefectSeverity.HIGH;
            case MINOR_DEFECT -> DefectSeverity.MEDIUM;
            case OK -> DefectSeverity.LOW;
        };
    }

    private static String descriptionFor(MaintenanceInspection inspection, String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        if (inspection.getDetectedDefects() != null && !inspection.getDetectedDefects().isBlank()) {
            return inspection.getDetectedDefects().trim();
        }
        return "Defect detected by inspection " + inspection.getCode() + " (" + inspection.getResult() + ")";
    }

    private static String joinNonBlank(String first, String second) {
        boolean hasFirst = first != null && !first.isBlank();
        boolean hasSecond = second != null && !second.isBlank();
        if (hasFirst && hasSecond) {
            return first.trim() + "\n\nRecommended actions: " + second.trim();
        }
        return hasFirst ? first.trim() : hasSecond ? second.trim() : null;
    }
}
