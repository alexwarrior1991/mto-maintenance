package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CheckItemResult;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/**
 * Medida y resultado de un punto de checklist, de una tarea o de una inspeccion.
 *
 * <p>Ejemplo, {@code PATCH /orders/{id}/tasks/{taskId}/check-items/{itemId}} (o {@code /inspections/{id}/items/{itemId}})
 * con {@code Content-Type: application/merge-patch+json}:</p>
 * <pre>{@code
 * {"measuredValue": 320, "adjusted": true, "valueAfterAdjustment": 260, "itemResult": "OK", "version": 0}
 * }</pre>
 */
public record CheckItemUpdateRequest(
        @Digits(integer = 9, fraction = 3) BigDecimal measuredValue,
        Boolean adjusted,
        @Digits(integer = 9, fraction = 3) BigDecimal valueAfterAdjustment,
        CheckItemResult itemResult,
        String notes,
        Long version
) {
}
