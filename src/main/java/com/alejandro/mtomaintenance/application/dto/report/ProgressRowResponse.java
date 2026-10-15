package com.alejandro.mtomaintenance.application.dto.report;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;

import java.math.BigDecimal;

/**
 * Una fila del avance: por paquete de ejecucion, via y tipo de activo.
 *
 * <p>Ejemplo, dentro de {@code ProgressReportResponse.rows}:</p>
 * <pre>{@code
 * {"executionPackageId": 6, "trackId": 2, "assetType": "PROFILE", "totalAssets": 23, "checkedAssets": 21,
 *  "completionRatio": 0.9130, "coveredKm": 1.071, "totalKm": 1.176}
 * }</pre>
 */
public record ProgressRowResponse(
        Long executionPackageId,
        Long trackId,
        CatenaryAssetType assetType,
        long totalAssets,
        long checkedAssets,
        BigDecimal completionRatio,
        BigDecimal coveredKm,
        BigDecimal totalKm
) {
}
