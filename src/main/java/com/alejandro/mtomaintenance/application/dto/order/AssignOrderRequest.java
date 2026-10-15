package com.alejandro.mtomaintenance.application.dto.order;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Asignacion de una orden a un equipo, a un usuario o a los dos.
 *
 * <p>Ejemplo, {@code POST /orders/{id}/assign}:</p>
 * <pre>{@code
 * {"teamId": "…(Team A)", "assignedUser": "jefe.equipo.a", "comment": "Turno SH-000001"}
 * }</pre>
 */
public record AssignOrderRequest(UUID teamId, @Size(max = 100) String assignedUser, String comment) {

    @AssertTrue(message = "teamId or assignedUser is required")
    public boolean hasAssignee() {
        return teamId != null || (assignedUser != null && !assignedUser.isBlank());
    }
}
