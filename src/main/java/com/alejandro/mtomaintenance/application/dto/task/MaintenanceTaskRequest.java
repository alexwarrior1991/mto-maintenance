package com.alejandro.mtomaintenance.application.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record MaintenanceTaskRequest(
        @NotBlank @Size(max = 500) String description,
        UUID assetId,
        @Size(max = 100) String assignedUser,
        List<String> taskTypeCodes,
        Boolean withChecklist
) {
}
