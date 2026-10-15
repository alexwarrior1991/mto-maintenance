package com.alejandro.mtomaintenance.application.dto.team;

import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;

import java.util.Set;
import java.util.UUID;

/**
 * Un equipo de mantenimiento con su base, su vehiculo y los EP que atiende.
 *
 * <p>Ejemplo, {@code GET /teams/{id}}:</p>
 * <pre>{@code
 * {"id": "0195a000-0000-7000-8000-000000000001", "code": "A", "name": "Team A - Rishpon", "baseName": "Rishpon",
 *  "vehicle": "Maintenance vehicle A", "active": true, "executionPackageIds": [6], "audit": {…}}
 * }</pre>
 */
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
