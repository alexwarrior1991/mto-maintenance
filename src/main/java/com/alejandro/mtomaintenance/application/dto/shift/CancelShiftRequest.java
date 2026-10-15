package com.alejandro.mtomaintenance.application.dto.shift;

import jakarta.validation.constraints.NotBlank;

/**
 * Cancelacion de un turno; el motivo se anade a sus observaciones.
 *
 * <p>Ejemplo, {@code POST /shifts/{id}/cancel}:</p>
 * <pre>{@code
 * {"reason": "Posesion denegada por el centro de control"}
 * }</pre>
 */
public record CancelShiftRequest(@NotBlank String reason) {
}
