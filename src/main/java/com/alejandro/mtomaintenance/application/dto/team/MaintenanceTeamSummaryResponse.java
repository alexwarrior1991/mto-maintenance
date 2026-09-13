package com.alejandro.mtomaintenance.application.dto.team;

import java.util.UUID;

public record MaintenanceTeamSummaryResponse(UUID id, String code, String name, String baseName) {
}
