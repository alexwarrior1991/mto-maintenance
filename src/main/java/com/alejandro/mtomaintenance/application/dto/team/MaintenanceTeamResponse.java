package com.alejandro.mtomaintenance.application.dto.team;

import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;

import java.util.Set;
import java.util.UUID;

public record MaintenanceTeamResponse(
        UUID id,
        String code,
        String name,
        String baseName,
        String vehicle,
        Boolean active,
        Set<Long> executionPackageIds,
        AuditMetadataResponse audit
) {
}
