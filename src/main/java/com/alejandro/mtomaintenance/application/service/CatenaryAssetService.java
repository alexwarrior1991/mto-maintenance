package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetRequest;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetResponse;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface CatenaryAssetService {

    CatenaryAssetResponse createTrackSection(CatenaryAssetRequest request);

    CatenaryAssetResponse update(UUID id, CatenaryAssetUpdateRequest request);

    CatenaryAssetResponse findById(UUID id);

    PageResponse<CatenaryAssetResponse> search(CatenaryAssetType type, Long trackId, Long stationId, Long executionPackageId,
                                               Boolean enabled, String code, BigDecimal kpFrom, BigDecimal kpTo,
                                               Instant preventiveDueBefore, Pageable pageable);

    /** DELETE = desactivar. Un activo nunca se borra: ordenes, inspecciones y defectos lo referencian. */
    void disable(UUID id);

    PageResponse<EntityRevisionResponse<CatenaryAssetResponse>> findRevisions(UUID id, Pageable pageable);
}
