package com.alejandro.mtomaintenance.application.dto.order;

import jakarta.validation.constraints.NotBlank;

/**
 * Cancelacion de una orden, con su motivo obligatorio.
 *
 * <p>Ejemplo, {@code POST /orders/{id}/cancel}:</p>
 * <pre>{@code
 * {"reason": "Posesion denegada por el centro de control; se replanifica en la semana 6"}
 * }</pre>
 */
public record CancelOrderRequest(@NotBlank String reason) {
}
