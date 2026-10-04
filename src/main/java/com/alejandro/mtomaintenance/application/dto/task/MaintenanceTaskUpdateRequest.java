package com.alejandro.mtomaintenance.application.dto.task;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

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
