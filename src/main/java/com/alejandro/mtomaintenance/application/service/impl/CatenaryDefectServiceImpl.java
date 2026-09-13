package com.alejandro.mtomaintenance.application.service.impl;

import static com.alejandro.mtomaintenance.application.service.impl.DomainGuard.domain;
import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectRequest;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectResponse;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.defect.DefectCommentRequest;
import com.alejandro.mtomaintenance.application.dto.defect.ResolveDefectRequest;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.mapper.CatenaryDefectMapper;
import com.alejandro.mtomaintenance.application.mapper.PageMapper;
import com.alejandro.mtomaintenance.application.service.CatenaryDefectService;
import com.alejandro.mtomaintenance.application.service.EntityAuditService;
import com.alejandro.mtomaintenance.application.service.MaintenanceCodeGenerator;
import com.alejandro.mtomaintenance.application.service.StatusHistoryService;
import com.alejandro.mtomaintenance.domain.model.DefectStateMachine;
import com.alejandro.mtomaintenance.domain.model.KilometricRange;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryDefectRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceInspectionRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.specification.CatenaryDefectSpecification;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CatenaryDefectServiceImpl implements CatenaryDefectService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatenaryDefectServiceImpl.class);

    private final CatenaryDefectRepository repository;
    private final MaintenanceInspectionRepository inspectionRepository;
    private final CatenaryDefectMapper mapper;
    private final MaintenanceLookups lookups;
    private final MaintenanceCodeGenerator codeGenerator;
    private final StatusHistoryService history;
    private final EntityAuditService auditService;

    @Override
    @Transactional
    public CatenaryDefectResponse create(CatenaryDefectRequest request) {
        CatenaryAsset asset = lookups.enabledAsset(request.assetId());
        MaintenanceOrder order = request.orderId() == null ? null : lookups.order(request.orderId());
        if (order != null && order.isTerminal()) {
            throw new InvalidTransitionException("Order " + order.getCode() + " is " + order.getStatus() + " and cannot take new defects");
        }

        CatenaryDefect defect = CatenaryDefect.builder()
                .code(codeGenerator.nextDefectCode())
                .asset(asset)
                .inspection(request.inspectionId() == null ? null : inspectionRepository.findById(request.inspectionId())
                        .orElseThrow(() -> new NotFoundException("Maintenance inspection", request.inspectionId())))
                .order(order)
                .severity(request.severity())
                .status(order == null ? DefectStatus.OPEN : DefectStatus.IN_PROGRESS)
                .description(request.description().trim())
                .technicalNotes(request.technicalNotes())
                .detectedAt(request.detectedAt() != null ? request.detectedAt() : Instant.now())
                .correctionType(request.correctionType())
                .partsReplaced(request.partsReplaced())
                .repairPlannedDate(request.repairPlannedDate())
                .photoRefs(request.photoRefs() == null ? new ArrayList<>() : new ArrayList<>(request.photoRefs()))
                .build();
        defect.locateAt(asset);
        if (request.startKp() != null || request.endKp() != null) {
            defect.setStartKp(request.startKp() != null ? request.startKp() : request.endKp());
            defect.setEndKp(request.endKp() != null ? request.endKp() : request.startKp());
            domain(() -> new KilometricRange(defect.getStartKp(), defect.getEndKp()));
        }

        CatenaryDefect saved = repository.save(defect);
        history.recordDefectChange(saved, null, saved.getStatus().name(),
                order == null ? "Defect recorded" : "Defect recorded and linked to order " + order.getCode());
        LOGGER.info("Defect created: code={}, asset={}, severity={}", saved.getCode(), asset.getCode(), saved.getSeverity());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CatenaryDefectResponse update(UUID id, CatenaryDefectUpdateRequest request) {
        CatenaryDefect defect = defect(id);
        if (DefectStateMachine.isTerminal(defect.getStatus())) {
            throw new InvalidTransitionException("Defect " + defect.getCode() + " is " + defect.getStatus() + " and cannot be changed");
        }
        if (request.severity() != null) {
            defect.setSeverity(request.severity());
        }
        if (request.description() != null) {
            defect.setDescription(request.description().trim());
        }
        if (request.technicalNotes() != null) {
            defect.setTechnicalNotes(request.technicalNotes());
        }
        if (request.correctionType() != null) {
            defect.setCorrectionType(request.correctionType());
        }
        if (request.partsReplaced() != null) {
            defect.setPartsReplaced(request.partsReplaced());
        }
        if (request.repairPlannedDate() != null) {
            defect.setRepairPlannedDate(request.repairPlannedDate());
        }
        if (request.photoRefs() != null) {
            defect.setPhotoRefs(new ArrayList<>(request.photoRefs()));
        }
        return mapper.toResponse(repository.save(defect));
    }

    @Override
    @Transactional(readOnly = true)
    public CatenaryDefectResponse findById(UUID id) {
        return mapper.toResponse(defect(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CatenaryDefectResponse> search(DefectSeverity severity, DefectStatus status, UUID assetId, UUID orderId, Long trackId,
                                                       Long stationId, Long executionPackageId, Instant detectedFrom, Instant detectedTo,
                                                       Pageable pageable) {
        Specification<CatenaryDefect> specification = CatenaryDefectSpecification.severityEquals(severity)
                .and(CatenaryDefectSpecification.statusEquals(status))
                .and(CatenaryDefectSpecification.assetIdEquals(assetId))
                .and(CatenaryDefectSpecification.orderIdEquals(orderId))
                .and(CatenaryDefectSpecification.trackIdEquals(trackId))
                .and(CatenaryDefectSpecification.stationIdEquals(stationId))
                .and(CatenaryDefectSpecification.executionPackageIdEquals(executionPackageId))
                .and(CatenaryDefectSpecification.detectedBetween(detectedFrom, detectedTo));
        return PageMapper.toPageResponse(repository.findAll(specification, pageable), mapper::toResponse);
    }

    @Override
    @Transactional
    public CatenaryDefectResponse resolve(UUID id, ResolveDefectRequest request) {
        CatenaryDefect defect = defect(id);
        if (!DefectStateMachine.canResolve(defect.getStatus())) {
            throw new InvalidTransitionException("Defect " + defect.getCode() + " is " + defect.getStatus() + " and cannot be resolved");
        }
        MaintenanceOrder order = defect.getOrder();
        if (order != null && order.getStatus() != MaintenanceOrderStatus.COMPLETED && request.resolvedInShiftId() == null) {
            // Con orden enlazada la resolucion llega con el cierre de la orden; la excepcion es la
            // correccion in situ dentro de un turno, que se declara con resolvedInShiftId.
            throw new InvalidTransitionException("Defect " + defect.getCode() + " is linked to order " + order.getCode()
                    + " which is " + order.getStatus() + "; complete the order or declare the shift where it was corrected");
        }
        defect.setResolvedAt(Instant.now());
        defect.setResolutionNotes(request.resolutionNotes().trim());
        if (request.resolvedInShiftId() != null) {
            defect.setResolvedInShift(lookups.shift(request.resolvedInShiftId()));
        }
        if (request.correctionType() != null) {
            defect.setCorrectionType(request.correctionType());
        }
        if (request.partsReplaced() != null) {
            defect.setPartsReplaced(request.partsReplaced());
        }
        transition(defect, DefectStatus.RESOLVED, request.resolutionNotes());
        return mapper.toResponse(repository.save(defect));
    }

    @Override
    @Transactional
    public CatenaryDefectResponse close(UUID id, DefectCommentRequest request) {
        CatenaryDefect defect = defect(id);
        if (!DefectStateMachine.canClose(defect.getStatus())) {
            throw new InvalidTransitionException("Defect " + defect.getCode() + " is " + defect.getStatus() + "; only resolved defects are closed");
        }
        transition(defect, DefectStatus.CLOSED, request.reason());
        return mapper.toResponse(repository.save(defect));
    }

    @Override
    @Transactional
    public CatenaryDefectResponse discard(UUID id, DefectCommentRequest request) {
        CatenaryDefect defect = defect(id);
        if (!DefectStateMachine.canDiscard(defect.getStatus())) {
            throw new InvalidTransitionException("Defect " + defect.getCode() + " is " + defect.getStatus() + "; only open defects are discarded");
        }
        defect.setDiscardReason(request.reason().trim());
        transition(defect, DefectStatus.DISCARDED, request.reason());
        return mapper.toResponse(repository.save(defect));
    }

    @Override
    @Transactional
    public CatenaryDefectResponse linkOrder(UUID id, UUID orderId) {
        CatenaryDefect defect = defect(id);
        MaintenanceOrder order = lookups.order(orderId);
        if (!DefectStateMachine.canLinkOrder(defect.getStatus())) {
            throw new InvalidTransitionException("Defect " + defect.getCode() + " is " + defect.getStatus() + " and cannot be linked to an order");
        }
        if (order.isTerminal()) {
            throw new InvalidTransitionException("Order " + order.getCode() + " is " + order.getStatus() + " and cannot take defects");
        }
        defect.setOrder(order);
        if (defect.getStatus() == DefectStatus.OPEN) {
            transition(defect, DefectStatus.IN_PROGRESS, "Linked to order " + order.getCode());
        } else {
            history.recordDefectChange(defect, defect.getStatus().name(), defect.getStatus().name(), "Re-linked to order " + order.getCode());
        }
        return mapper.toResponse(repository.save(defect));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EntityRevisionResponse<CatenaryDefectResponse>> findRevisions(UUID id, Pageable pageable) {
        return auditService.findRevisions(CatenaryDefect.class, id, mapper::toResponse, pageable);
    }

    private CatenaryDefect defect(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Catenary defect", id));
    }

    private void transition(CatenaryDefect defect, DefectStatus target, String comment) {
        DefectStatus previous = defect.getStatus();
        defect.setStatus(target);
        history.recordDefectChange(defect, previous.name(), target.name(), comment);
        LOGGER.info("Defect {}: {} -> {}", defect.getCode(), previous, target);
    }
}
