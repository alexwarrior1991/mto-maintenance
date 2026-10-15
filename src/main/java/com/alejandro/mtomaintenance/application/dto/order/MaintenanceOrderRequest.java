package com.alejandro.mtomaintenance.application.dto.order;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Alta de una orden de mantenimiento sobre un activo; nace en DRAFT.
 *
 * <p>Ejemplo, {@code POST /orders}:</p>
 * <pre>{@code
 * {"title": "Revision preventiva T2 Raanana - Herzliya", "type": "PREVENTIVE", "priority": "MEDIUM",
 *  "assetId": "…(SEC-T2-RAAN-HER)", "plannedDate": "2026-01-27", "teamId": "…(Team A)"}
 * }</pre>
 */
public record MaintenanceOrderRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @NotNull MaintenanceOrderType type,
        MaintenancePriority priority,
        @NotNull UUID assetId,
        LocalDate plannedDate,
        UUID teamId,
        @Size(max = 100) String assignedUser,
        UUID stockProjectId
) {
}
