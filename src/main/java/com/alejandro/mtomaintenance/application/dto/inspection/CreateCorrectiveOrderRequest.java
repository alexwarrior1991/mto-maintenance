package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateCorrectiveOrderRequest(
        @Size(max = 255) String title,
        String description,
        MaintenancePriority priority,
        LocalDate plannedDate,
        UUID teamId
) {
}
