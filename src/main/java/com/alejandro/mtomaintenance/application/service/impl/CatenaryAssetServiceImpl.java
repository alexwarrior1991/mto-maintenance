package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetRequest;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetResponse;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.exception.AssetDisabledException;
import com.alejandro.mtomaintenance.application.exception.DuplicateCodeException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.application.mapper.CatenaryAssetMapper;
import com.alejandro.mtomaintenance.application.mapper.PageMapper;
import com.alejandro.mtomaintenance.application.service.CatenaryAssetService;
import com.alejandro.mtomaintenance.application.service.EntityAuditService;
import com.alejandro.mtomaintenance.domain.model.KilometricRange;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.specification.CatenaryAssetSpecification;
import com.alejandro.mtomaintenance.infrastructure.persistence.specification.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CatenaryAssetServiceImpl implements CatenaryAssetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatenaryAssetServiceImpl.class);

    private final CatenaryAssetRepository repository;
    private final CatenaryAssetMapper mapper;
    private final MaintenanceLookups lookups;
    private final EntityAuditService auditService;

    @Override
    @Transactional
    public CatenaryAssetResponse createTrackSection(CatenaryAssetRequest request) {
        String code = SpecificationUtils.normalize(request.code());
        if (repository.existsByCode(code)) {
            throw new DuplicateCodeException("Catenary asset", code);
        }
        new KilometricRange(request.startKp(), request.endKp());

        CatenaryAsset asset = CatenaryAsset.builder()
                .code(code)
                .name(request.name().trim())
                .type(CatenaryAssetType.TRACK_SECTION)
                .description(request.description())
                .executionPackageId(request.executionPackageId())
                .trackId(request.trackId())
                .stationId(request.stationId())
                .startKp(request.startKp())
                .endKp(request.endKp())
                .trackKind(request.trackKind())
                .preventiveIntervalDays(request.preventiveIntervalDays())
                .build();

        CatenaryAsset saved = repository.save(asset);
        LOGGER.info("Track section created: code={}, trackId={}, kp={}..{}", saved.getCode(), saved.getTrackId(), saved.getStartKp(), saved.getEndKp());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CatenaryAssetResponse update(UUID id, CatenaryAssetUpdateRequest request) {
        CatenaryAsset asset = lookups.asset(id);

        if (asset.isFromMasterData() && request.touchesIdentity()) {
            // La identidad y la localizacion las decide mto-configuration: cambiarlas aqui dejaria
            // el activo distinto de su origen hasta el siguiente evento, que lo pisaria sin avisar.
            throw new AssetDisabledException("Catenary asset " + asset.getCode()
                    + " comes from master data: only description, enabled and preventiveIntervalDays can be changed here");
        }

        if (request.name() != null) {
            asset.setName(request.name().trim());
        }
        if (request.description() != null) {
            asset.setDescription(request.description());
        }
        if (request.enabled() != null) {
            asset.setEnabled(request.enabled());
        }
        if (request.preventiveIntervalDays() != null) {
            asset.setPreventiveIntervalDays(request.preventiveIntervalDays());
        }
        if (!asset.isFromMasterData()) {
            if (request.executionPackageId() != null) {
                asset.setExecutionPackageId(request.executionPackageId());
            }
            if (request.trackId() != null) {
                asset.setTrackId(request.trackId());
            }
            if (request.stationId() != null) {
                asset.setStationId(request.stationId());
            }
            if (request.startKp() != null) {
                asset.setStartKp(request.startKp());
            }
            if (request.endKp() != null) {
                asset.setEndKp(request.endKp());
            }
            if (request.trackKind() != null) {
                if (asset.getType() != CatenaryAssetType.TRACK_SECTION) {
                    throw new ValidationException("trackKind only applies to track sections");
                }
                asset.setTrackKind(request.trackKind());
            }
            if (asset.getStartKp() != null && asset.getEndKp() != null) {
                new KilometricRange(asset.getStartKp(), asset.getEndKp());
            }
        }

        return mapper.toResponse(repository.save(asset));
    }

    @Override
    @Transactional(readOnly = true)
    public CatenaryAssetResponse findById(UUID id) {
        return mapper.toResponse(lookups.asset(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CatenaryAssetResponse> search(CatenaryAssetType type, Long trackId, Long stationId, Long executionPackageId,
                                                      Boolean enabled, String code, BigDecimal kpFrom, BigDecimal kpTo,
                                                      Instant preventiveDueBefore, Pageable pageable) {
        Specification<CatenaryAsset> specification = CatenaryAssetSpecification.typeEquals(type)
                .and(CatenaryAssetSpecification.trackIdEquals(trackId))
                .and(CatenaryAssetSpecification.stationIdEquals(stationId))
                .and(CatenaryAssetSpecification.executionPackageIdEquals(executionPackageId))
                .and(CatenaryAssetSpecification.enabledEquals(enabled))
                .and(CatenaryAssetSpecification.codeContains(code))
                .and(CatenaryAssetSpecification.kpBetween(kpFrom, kpTo))
                .and(CatenaryAssetSpecification.preventiveDueBefore(preventiveDueBefore));
        return PageMapper.toPageResponse(repository.findAll(specification, pageable), mapper::toResponse);
    }

    @Override
    @Transactional
    public void disable(UUID id) {
        CatenaryAsset asset = lookups.asset(id);
        if (!asset.isEnabled()) {
            return;
        }
        asset.setEnabled(false);
        repository.save(asset);
        LOGGER.info("Catenary asset disabled: code={}", asset.getCode());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EntityRevisionResponse<CatenaryAssetResponse>> findRevisions(UUID id, Pageable pageable) {
        return auditService.findRevisions(CatenaryAsset.class, id, mapper::toResponse, pageable);
    }
}
