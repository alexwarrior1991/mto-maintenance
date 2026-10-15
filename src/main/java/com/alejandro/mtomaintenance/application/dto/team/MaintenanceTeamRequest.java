package com.alejandro.mtomaintenance.application.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Alta o cambio de un equipo de mantenimiento y de los EP que atiende.
 *
 * <p>Ejemplo, {@code PUT /teams/{id}} (el mismo cuerpo que {@code POST /teams}):</p>
 * <pre>{@code
 * {"code": "A", "name": "Team A - Rishpon", "baseName": "Rishpon", "vehicle": "Maintenance vehicle A", "active": true,
 *  "executionPackageIds": [6]}
 * }</pre>
 */
public record MaintenanceTeamRequest(
        @NotBlank @Size(max = 16) String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 120) String baseName,
        @Size(max = 120) String vehicle,
        Boolean active,
        Set<@NotNull Long> executionPackageIds
) {
}
