package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CheckItemResult;

import java.math.BigDecimal;
import java.util.UUID;

/** Punto de una checklist (de inspeccion o de tarea). */
public record CheckItemResponse(
        UUID id,
        String code,
        String label,
        String unit,
        BigDecimal minValue,
        BigDecimal maxValue,
        Boolean requiresMeasure,
        BigDecimal measuredValue,
        Boolean adjusted,
        BigDecimal valueAfterAdjustment,
        CheckItemResult itemResult,
        String notes,
        Integer orderIndex,
        Boolean outOfRange
) {
}
