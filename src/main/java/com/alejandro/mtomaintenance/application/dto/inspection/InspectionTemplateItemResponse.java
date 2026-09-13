package com.alejandro.mtomaintenance.application.dto.inspection;

import java.math.BigDecimal;
import java.util.UUID;

public record InspectionTemplateItemResponse(
        UUID id,
        String code,
        String label,
        String unit,
        BigDecimal minValue,
        BigDecimal maxValue,
        Boolean requiresMeasure,
        Integer orderIndex
) {
}
