package com.alejandro.mtomaintenance.application.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record MaintenanceTeamRequest(
        @NotBlank @Size(max = 16) String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 120) String baseName,
        @Size(max = 120) String vehicle,
        Boolean active,
        Set<@NotNull Long> executionPackageIds
) {
}
