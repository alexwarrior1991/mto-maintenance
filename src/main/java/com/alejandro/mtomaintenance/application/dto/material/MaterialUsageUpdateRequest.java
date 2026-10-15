package com.alejandro.mtomaintenance.application.dto.material;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Modificacion de una linea de material: cantidades y permiso de sobreconsumo.
 *
 * <p>Ejemplo, {@code PATCH /orders/{id}/materials/{usageId}} con {@code Content-Type: application/merge-patch+json}:</p>
 * <pre>{@code
 * {"plannedQuantity": 8, "version": 1}
 * }</pre>
 */
public record MaterialUsageUpdateRequest(
        @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal plannedQuantity,
        @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal consumedQuantity,
        Boolean allowOverConsumption,
        Long version
) {
}
