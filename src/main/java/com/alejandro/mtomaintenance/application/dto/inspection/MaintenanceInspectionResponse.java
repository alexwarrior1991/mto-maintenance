package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MaintenanceInspectionResponse(
        UUID id,
        String code,
        CatenaryAssetSummaryResponse asset,
        Long executionPackageId,
        Long trackId,
        Long stationId,
        BigDecimal kp,
        LocalDate inspectionDate,
        String inspector,
        InspectionKind inspectionKind,
        UUID templateId,
        InspectionResult result,
        String description,
        String detectedDefects,
        String recommendedActions,
        UUID generatedDefectId,
        UUID generatedOrderId,
        UUID originOrderId,
        UUID shiftId,
        List<CheckItemResponse> items,
        AuditMetadataResponse audit
) {
}
