package com.alejandro.mtomaintenance.application.dto.task;

import jakarta.validation.constraints.Size;

import java.util.List;

public record MaintenanceTaskUpdateRequest(
        @Size(max = 500) String description,
        @Size(max = 100) String assignedUser,
        List<String> taskTypeCodes,
        String notes,
        String defectsFound,
        List<String> photoRefs
) {
}
