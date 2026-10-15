package com.alejandro.mtomaintenance.application.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Tarea anadida a mano a una orden, sobre un activo o sin el.
 *
 * <p>Ejemplo, {@code POST /orders/{id}/tasks}:</p>
 * <pre>{@code
 * {"description": "Revision del aislador HER-SI-21/2", "assetId": "…(SIN-311)", "assignedUser": "yossi",
 *  "taskTypeCodes": ["RG-03"], "withChecklist": true}
 * }</pre>
 */
public record MaintenanceTaskRequest(
        @NotBlank @Size(max = 500) String description,
        UUID assetId,
        @Size(max = 100) String assignedUser,
        List<@NotNull String> taskTypeCodes,
        Boolean withChecklist
) {
}
