package com.alejandro.mtomaintenance.application.dto.task;

import jakarta.validation.constraints.NotBlank;

/**
 * Motivo por el que se cancela una tarea de la orden.
 *
 * <p>Ejemplo, {@code POST /orders/{id}/tasks/{taskId}/cancel}:</p>
 * <pre>{@code
 * {"reason": "Perfil 13-2.01 desmontado por la obra de la via 2"}
 * }</pre>
 */
public record CancelTaskRequest(@NotBlank String reason) {
}
