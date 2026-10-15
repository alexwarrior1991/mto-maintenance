package com.alejandro.mtomaintenance.application.dto.common;

import java.time.Instant;

/**
 * Response DTO containing audit metadata common to persisted API resources.
 *
 * <p>Ejemplo, el {@code audit} que trae {@code GET /orders/{id}} para la orden MO-000001:</p>
 * <pre>{@code
 * {"createdAt": "2026-01-20T08:15:00Z", "updatedAt": "2026-01-27T19:40:00Z",
 *  "createdBy": "mantenimiento.responsable", "updatedBy": "mantenimiento.responsable"}
 * }</pre>
 */
public record AuditMetadataResponse(
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
) {
}