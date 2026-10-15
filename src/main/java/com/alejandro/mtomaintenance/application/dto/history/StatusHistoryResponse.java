package com.alejandro.mtomaintenance.application.dto.history;

import java.time.Instant;
import java.util.UUID;

/**
 * Un cambio de estado de una orden o de un defecto, tal como lo guarda su historial.
 *
 * <p>Ejemplo, un elemento de {@code GET /orders/{id}/history} (igual en {@code GET /defects/{id}/history}):</p>
 * <pre>{@code
 * {"id": "0195a…", "previousStatus": "DRAFT", "newStatus": "PLANNED", "changedAt": "2026-01-20T08:15:00Z",
 *  "changedBy": "mantenimiento.responsable", "comment": "Semana 5, equipo A"}
 * }</pre>
 */
public record StatusHistoryResponse(
        UUID id,
        String previousStatus,
        String newStatus,
        Instant changedAt,
        String changedBy,
        String comment
) {
}
