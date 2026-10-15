package com.alejandro.mtomaintenance.application.dto.inspection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Punto de una plantilla de inspeccion; se copia a cada checklist de su tipo de activo.
 *
 * <p>Ejemplo, dentro de {@code InspectionTemplateResponse.items}:</p>
 * <pre>{@code
 * {"id": "0195a…", "code": "CONTINUITY", "label": "Electrical continuity / contact resistance", "unit": "uOhm",
 *  "minValue": null, "maxValue": 100.000, "requiresMeasure": true, "orderIndex": 5}
 * }</pre>
 */
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
