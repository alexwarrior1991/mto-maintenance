package com.alejandro.mtomaintenance.application.dto.team;

import java.util.UUID;

/**
 * El equipo tal como lo llevan una orden y un turno.
 *
 * <p>Ejemplo, dentro de {@code MaintenanceOrderResponse.team} y {@code MaintenanceShiftResponse.team}:</p>
 * <pre>{@code
 * {"id": "0195a000-0000-7000-8000-000000000001", "code": "A", "name": "Team A - Rishpon", "baseName": "Rishpon"}
 * }</pre>
 */
public record MaintenanceTeamSummaryResponse(UUID id, String code, String name, String baseName) {
}
