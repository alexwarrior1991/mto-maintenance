package com.alejandro.mtomaintenance.application.dto.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Avance: activos revisados sobre el total y kilometros cubiertos, con una fila por paquete, via y tipo de activo.
 *
 * <p>Ejemplo, {@code GET /reports/progress?executionPackageId=6&trackId=2&assetType=PROFILE}:</p>
 * <pre>{@code
 * {"from": null, "to": null, "totalAssets": 23, "checkedAssets": 21, "completionRatio": 0.9130, "coveredKm": 1.071,
 *  "totalKm": 1.176, "rows": [{"executionPackageId": 6, "trackId": 2, "assetType": "PROFILE", "totalAssets": 23, …}]}
 * }</pre>
 */
public record ProgressReportResponse(
        Instant from,
        Instant to,
        long totalAssets,
        long checkedAssets,
        BigDecimal completionRatio,
        BigDecimal coveredKm,
        BigDecimal totalKm,
        List<ProgressRowResponse> rows
) {
}
