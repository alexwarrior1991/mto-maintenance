package com.alejandro.mtomaintenance.application.dto.task;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Cambio de una tarea; el mismo cuerpo sirve al {@code PUT} y al {@code PATCH} (merge-patch).
 *
 * <p>Ejemplo, {@code PUT /orders/{id}/tasks/{taskId}}:</p>
 * <pre>{@code
 * {"assignedUser": "yossi", "taskTypeCodes": ["RG-01", "RG-12"], "notes": "Revisar tambien el vano siguiente", "version": 2}
 * }</pre>
 */
public record MaintenanceTaskUpdateRequest(
        @Size(max = 500) String description,
        @Size(max = 100) String assignedUser,
        List<@NotNull String> taskTypeCodes,
        String notes,
        String defectsFound,
        List<@NotNull String> photoRefs,
        Long version
) {
}
