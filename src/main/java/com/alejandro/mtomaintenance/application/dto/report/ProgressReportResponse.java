package com.alejandro.mtomaintenance.application.dto.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
