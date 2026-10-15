package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CheckItemResult;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Punto de una checklist (de inspeccion o de tarea).
 *
 * <p>Ejemplo, dentro de {@code MaintenanceTaskResponse.checkItems} (y de {@code MaintenanceInspectionResponse.items}):</p>
 * <pre>{@code
 * {"id": "0195a…", "code": "CW_STAGGER", "label": "Contact wire stagger at the pole (signed)", "unit": "mm",
 *  "minValue": -300.000, "maxValue": 300.000, "requiresMeasure": true, "measuredValue": 320.000, "adjusted": true,
 *  "valueAfterAdjustment": 260.000, "itemResult": "OK", "notes": null, "orderIndex": 2, "outOfRange": false, "version": 1}
 * }</pre>
 */
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
        Boolean outOfRange,
        Long version
) {
}
