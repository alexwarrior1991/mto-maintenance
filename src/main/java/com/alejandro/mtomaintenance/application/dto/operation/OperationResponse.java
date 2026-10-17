package com.alejandro.mtomaintenance.application.dto.operation;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Una operacion de fondo: lo que responde un {@code 202 Accepted} y {@code GET /operations/{id}}.
 *
 * <p>Ejemplo, una generacion de tareas de un EP que todavia corre:</p>
 * <pre>{@code
 * {"id": "…", "kind": "GENERATE_TASKS", "scope": "EP-14", "status": "RUNNING", "requestedBy": "mantenimiento.responsable",
 *  "createdAt": "2026-10-16T21:00:00Z", "startedAt": "2026-10-16T21:00:00Z"}
 * }</pre>
 *
 * @param resultStatus el estado HTTP que habria dado la llamada sincrona (200, 201) al terminar bien
 * @param result       el cuerpo que habria dado la llamada sincrona, tal cual
 * @param error        al terminar mal, el estado, codigo y mensaje de esa misma llamada
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperationResponse(
        UUID id,
        String kind,
        String scope,
        OperationStatus status,
        String requestedBy,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Integer resultStatus,
        Object result,
        OperationError error
) {
}
