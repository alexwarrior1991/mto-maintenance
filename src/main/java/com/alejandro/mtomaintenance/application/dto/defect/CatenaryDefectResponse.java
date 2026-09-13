package com.alejandro.mtomaintenance.application.dto.defect;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CatenaryDefectResponse(
        UUID id,
        String code,
        CatenaryAssetSummaryResponse asset,
        UUID inspectionId,
        UUID orderId,
        DefectSeverity severity,
        DefectStatus status,
        String description,
        String technicalNotes,
        Instant detectedAt,
        Instant resolvedAt,
        String resolutionNotes,
        String discardReason,
        Long executionPackageId,
        Long trackId,
        Long stationId,
        BigDecimal startKp,
        BigDecimal endKp,
        String correctionType,
        String partsReplaced,
        UUID resolvedInShiftId,
        LocalDate repairPlannedDate,
        UUID foundInTaskId,
        List<String> photoRefs,
        AuditMetadataResponse audit
) {
}
