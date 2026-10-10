package com.alejandro.mtomaintenance.application.service.impl;

import static com.alejandro.mtomaintenance.application.service.impl.DomainGuard.domain;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetRequest;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetResponse;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.exception.AssetDisabledException;
import com.alejandro.mtomaintenance.application.exception.DuplicateCodeException;
import com.alejandro.mtomaintenance.application.exception.StaleVersionException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.application.mapper.CatenaryAssetMapper;
import com.alejandro.mtomaintenance.application.mapper.PageMapper;
import com.alejandro.mtomaintenance.application.service.CatenaryAssetService;
import com.alejandro.mtomaintenance.application.service.DomainEventPublisher;
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
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CatenaryAssetServiceImpl implements CatenaryAssetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatenaryAssetServiceImpl.class);

    /** Lo que un PATCH puede vaciar de un activo; en uno sincronizado, paquete y estacion son del origen. */
    private static final Set<String> CLEARABLE = Set.of("description", "preventiveIntervalDays", "executionPackageId", "stationId");


    private final CatenaryAssetRepository repository;
    private final CatenaryAssetMapper mapper;
    private final MaintenanceLookups lookups;
    private final EntityAuditService auditService;
    private final DomainEventPublisher events;

    @Override
    @Transactional
    public CatenaryAssetResponse createTrackSection(CatenaryAssetRequest request) {
        String code = SpecificationUtils.normalize(request.code());
        if (repository.existsByCode(code)) {
            throw new DuplicateCodeException("Catenary asset", code);
        }
        domain(() -> new KilometricRange(request.startKp(), request.endKp()));

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
        return patch(id, MergePatch.of(request));
    }

    @Override
    @Transactional
    public CatenaryAssetResponse patch(UUID id, MergePatch<CatenaryAssetUpdateRequest> patch) {
        CatenaryAsset asset = lookups.asset(id);
        CatenaryAssetUpdateRequest request = patch.values();
        StaleVersionException.check("Catenary asset " + asset.getCode(), asset.getVersion(), request.version());
        PatchRules.requireClearable(patch, CLEARABLE);

        if (asset.isFromMasterData() && (changesIdentity(asset, request) || clearsIdentity(asset, patch))) {
            // La identidad y la localizacion las decide mto-configuration: cambiarlas aqui dejaria
            // el activo distinto de su origen hasta el siguiente evento, que lo pisaria sin avisar.
            throw new AssetDisabledException("Catenary asset " + asset.getCode()
                    + " comes from master data: only description, enabled and preventiveIntervalDays can be changed here; "
                    + "the other fields may be sent only with the values it already has");
        }

        if (request.name() != null) {
            asset.setName(PatchRules.requireText(request.name(), "name"));
        }
        PatchRules.set(patch, "description", request.description(), asset::setDescription);
        if (request.isDisabling()) {
            boolean alreadyDisabledHere = Boolean.TRUE.equals(asset.getDisabledLocally());
            asset.disableLocally();
            if (!alreadyDisabledHere) {
                events.publish(MaintenanceEvents.assetDisabled(asset));
            }
        } else if (Boolean.TRUE.equals(request.enabled())) {
            if (asset.isDisabledAtSource()) {
                // Reactivarlo aqui no serviria: el siguiente evento de datos maestros diria otra vez que no.
                throw new AssetDisabledException("Catenary asset " + asset.getCode()
                        + " is disabled in mto-configuration: it comes back when the source enables it again");
            }
            asset.enableLocally();
        }
        PatchRules.set(patch, "preventiveIntervalDays", request.preventiveIntervalDays(), asset::setPreventiveIntervalDays);
        if (!asset.isFromMasterData()) {
            PatchRules.set(patch, "executionPackageId", request.executionPackageId(), asset::setExecutionPackageId);
            if (request.trackId() != null) {
                asset.setTrackId(request.trackId());
            }
            PatchRules.set(patch, "stationId", request.stationId(), asset::setStationId);
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
                domain(() -> new KilometricRange(asset.getStartKp(), asset.getEndKp()));
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
                                                      Boolean enabled, String code, String name, BigDecimal kpFrom, BigDecimal kpTo,
                                                      Instant preventiveDueBefore, Long connectedTrackId, String switchCode,
                                                      Pageable pageable) {
        Specification<CatenaryAsset> specification = CatenaryAssetSpecification.typeEquals(type)
                .and(CatenaryAssetSpecification.trackIdEquals(trackId))
                .and(CatenaryAssetSpecification.stationIdEquals(stationId))
                .and(CatenaryAssetSpecification.executionPackageIdEquals(executionPackageId))
                .and(CatenaryAssetSpecification.enabledEquals(enabled))
                .and(CatenaryAssetSpecification.codeContains(code))
                .and(CatenaryAssetSpecification.nameContains(name))
                .and(CatenaryAssetSpecification.kpBetween(kpFrom, kpTo))
                .and(CatenaryAssetSpecification.preventiveDueBefore(preventiveDueBefore))
                .and(CatenaryAssetSpecification.connectedTrackIdEquals(connectedTrackId))
                .and(CatenaryAssetSpecification.hasSwitchCode(switchCode));
        return PageMapper.toPageResponse(repository.findAll(specification, pageable), mapper::toResponse);
    }

    @Override
    @Transactional
    public void disable(UUID id) {
        CatenaryAsset asset = lookups.asset(id);
        // Tambien si el origen ya lo tiene desactivado: la decision de aqui sobrevive a que lo reactive.
        if (asset.getDisabledLocally()) {
            return;
        }
        asset.disableLocally();
        repository.save(asset);
        events.publish(MaintenanceEvents.assetDisabled(asset));
        LOGGER.info("Catenary asset disabled: code={}", asset.getCode());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EntityRevisionResponse<CatenaryAssetResponse>> findRevisions(UUID id, Pageable pageable) {
        return auditService.findRevisions(CatenaryAsset.class, id, mapper::toResponse, pageable);
    }
    /**
     * Si la peticion cambia algo que en un activo de datos maestros decide mto-configuration. Lo que
     * llega igual a lo guardado no es un cambio: un formulario reenvia el activo entero para tocar la
     * descripcion, y eso era un 409. El kp se compara por valor (12847.99 y 12847.990 son el mismo).
     *
     * <p>Las agujas no aparecen porque no estan en la peticion: las escribe el manejador de datos
     * maestros y no hay forma de mandarlas por API.</p>
     */
    private static boolean changesIdentity(CatenaryAsset asset, CatenaryAssetUpdateRequest request) {
        return differs(request.name() == null ? null : request.name().trim(), asset.getName())
                || differs(request.executionPackageId(), asset.getExecutionPackageId())
                || differs(request.trackId(), asset.getTrackId())
                || differs(request.stationId(), asset.getStationId())
                || differsKp(request.startKp(), asset.getStartKp())
                || differsKp(request.endKp(), asset.getEndKp())
                || differs(request.trackKind(), asset.getTrackKind())
                || differs(request.connectedTrackId(), asset.getConnectedTrackId())
                || differs(request.installationType(), asset.getInstallationType());
    }

    /** Vaciar lo que el origen decide, salvo que ya estuviera vacio. */
    private static boolean clearsIdentity(CatenaryAsset asset, MergePatch<CatenaryAssetUpdateRequest> patch) {
        return (patch.clears("executionPackageId") && asset.getExecutionPackageId() != null)
                || (patch.clears("stationId") && asset.getStationId() != null);
    }

    private static boolean differs(Object requested, Object current) {
        return requested != null && !requested.equals(current);
    }

    private static boolean differsKp(BigDecimal requested, BigDecimal current) {
        return requested != null && (current == null || requested.compareTo(current) != 0);
    }
}
