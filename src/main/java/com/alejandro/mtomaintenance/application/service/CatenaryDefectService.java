package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectRequest;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectResponse;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.defect.DefectCommentRequest;
import com.alejandro.mtomaintenance.application.dto.defect.ResolveDefectRequest;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

public interface CatenaryDefectService {

    CatenaryDefectResponse create(CatenaryDefectRequest request);

    CatenaryDefectResponse update(UUID id, CatenaryDefectUpdateRequest request);

    CatenaryDefectResponse findById(UUID id);

    PageResponse<CatenaryDefectResponse> search(DefectSeverity severity, DefectStatus status, UUID assetId, UUID orderId, Long trackId,
                                                Long stationId, Long executionPackageId, Instant detectedFrom, Instant detectedTo,
                                                Pageable pageable);

    CatenaryDefectResponse resolve(UUID id, ResolveDefectRequest request);

    CatenaryDefectResponse close(UUID id, DefectCommentRequest request);

    CatenaryDefectResponse discard(UUID id, DefectCommentRequest request);

    CatenaryDefectResponse linkOrder(UUID id, UUID orderId);

    PageResponse<EntityRevisionResponse<CatenaryDefectResponse>> findRevisions(UUID id, Pageable pageable);
}
