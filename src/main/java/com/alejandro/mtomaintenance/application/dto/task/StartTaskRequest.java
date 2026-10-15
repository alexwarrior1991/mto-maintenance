package com.alejandro.mtomaintenance.application.dto.task;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Inicio de una tarea en un turno en curso que trabaja en su via.
 *
 * <p>Ejemplo, {@code POST /orders/{id}/tasks/{taskId}/start}:</p>
 * <pre>{@code
 * {"shiftId": "…(SH-000001)", "assignedUser": "yossi"}
 * }</pre>
 */
public record StartTaskRequest(@NotNull UUID shiftId, @Size(max = 100) String assignedUser) {
}
