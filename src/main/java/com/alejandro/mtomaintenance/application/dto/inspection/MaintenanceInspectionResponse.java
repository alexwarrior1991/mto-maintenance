package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Una inspeccion, con sus puntos de checklist y lo que ha generado (defecto y orden correctiva).
 *
 * <p>Ejemplo, {@code GET /inspections/{id}}:</p>
 * <pre>{@code
 * {"code": "INS-000004", "asset": {"code": "DSC-77", "name": "HSA-NS5", …}, "trackId": 2, "kp": 14025.600,
 *  "inspectionDate": "2026-01-28", "inspectionKind": "TECHNICAL", "result": "UNSAFE", "shiftId": "…(SH-000001)",
 *  "generatedDefectId": "…(DEF-000003)", "generatedOrderId": "…(MO-000002)",
 *  "items": [{"code": "CONTINUITY", "maxValue": 100.000, "measuredValue": 180.000, "itemResult": "DEFECT", …}], …}
 * }</pre>
 */
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
        Long version,
        AuditMetadataResponse audit
) {
}
