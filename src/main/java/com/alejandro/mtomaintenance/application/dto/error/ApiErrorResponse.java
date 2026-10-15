package com.alejandro.mtomaintenance.application.dto.error;

import java.time.Instant;
import java.util.List;

/**
 * Standard immutable HTTP error payload used by every API failure response.
 *
 * <p>Ejemplo, {@code POST /orders/{id}/tasks/{taskId}/complete} en un turno de posesion parcial:</p>
 * <pre>{@code
 * {"timestamp": "2026-01-27T23:40:12Z", "status": 409, "error": "CONFLICT", "errorCode": "SHF-001",
 *  "message": "Shift SH-000001 has partial possession; the task includes work that needs full track possession (…)",
 *  "path": "/api/v1/maintenance/orders/…/tasks/…/complete", "method": "POST",
 *  "correlationId": "8c3b8c1a-1111-4222-8333-444444444444", "validationErrors": []}
 * }</pre>
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String method,
        String errorCode,
        String correlationId,
        List<ValidationError> validationErrors
) {

    public ApiErrorResponse {
        validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
    }
}