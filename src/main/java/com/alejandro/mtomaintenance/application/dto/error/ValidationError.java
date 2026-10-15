package com.alejandro.mtomaintenance.application.dto.error;

/**
 * Describes one field, parameter or object validation failure returned to API clients.
 *
 * <p>Ejemplo, un elemento de {@code validationErrors} en el 400 {@code REQ-VALIDATION} de
 * {@code POST /orders/{id}/tasks/generate} cuando la segunda seleccion trae un {@code fromKp} con cuatro decimales:</p>
 * <pre>{@code
 * {"field": "selections[1].fromKp", "message": "numeric value out of bounds (<9 digits>.<3 digits> expected)"}
 * }</pre>
 */
public record ValidationError(
        String field,
        String message
) {
}