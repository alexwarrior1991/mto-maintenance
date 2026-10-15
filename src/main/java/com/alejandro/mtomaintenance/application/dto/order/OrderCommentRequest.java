package com.alejandro.mtomaintenance.application.dto.order;

/**
 * Cuerpo opcional de start: solo un comentario para el historial.
 *
 * <p>Ejemplo, {@code POST /orders/{id}/start}:</p>
 * <pre>{@code
 * {"comment": "Inicio en el turno SH-000001"}
 * }</pre>
 */
public record OrderCommentRequest(String comment) {
}
