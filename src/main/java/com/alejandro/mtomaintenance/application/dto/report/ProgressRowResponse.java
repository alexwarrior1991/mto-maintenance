package com.alejandro.mtomaintenance.application.dto.report;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;

import java.math.BigDecimal;

/** Una fila del avance: por paquete de ejecucion, via y tipo de activo. */
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
