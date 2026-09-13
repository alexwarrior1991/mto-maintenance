package com.alejandro.mtomaintenance.application.dto.order;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

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
