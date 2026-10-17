package com.alejandro.mtomaintenance.application.dto.crew;

import java.util.UUID;

/**
 * Una persona de mto-resources dentro de un equipo o de un turno: el jefe, el supervisor, un lineman.
 *
 * <p>Ejemplo:</p>
 * <pre>{@code
 * {"id": "…", "code": "PER-000003", "fullName": "Yossi Cohen", "username": "equipo.jefe", "category": "TEAM_LEADER"}
 * }</pre>
 */
public record CrewPersonSummary(UUID id, String code, String fullName, String username, String category) {
}
