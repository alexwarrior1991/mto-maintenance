package com.alejandro.mtomaintenance.application.dto.audit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Quién hizo un cambio, cuándo y desde dónde.
 *
 * <p>Ejemplo, el {@code revision} de una entrada de {@code GET /orders/{id}/revisions}:</p>
 * <pre>{@code
 * {"revision": 42, "revisionAt": "2026-01-26T09:40:00Z", "operation": "UPDATED", "author": "mantenimiento.responsable",
 *  "source": "HTTP", "correlationId": "8c3b8c1a-1111-4222-8333-444444444444"}
 * }</pre>
 *
 * @param revision      número de revisión, creciente y compartido por todas las entidades que
 *                      cambiaron en la misma transacción
 * @param revisionAt    momento del cambio
 * @param operation     qué le pasó a la entidad
 * @param author        el mismo actor que va a {@code updated_by}: puede ser {@code system} (proceso
 *                      interno) o {@code unknown} (una escritura que perdió la identidad por el
 *                      camino), y la diferencia entre los dos es deliberada
 * @param source        canal del que vino la escritura; dice a qué espacio pertenece
 *                      {@code correlationId}
 * @param correlationId la cabecera {@code X-Correlation-Id} de la petición, o el identificador del
 *                      mensaje de RabbitMQ que la provocó
 */
@Schema(description = "Metadata of one change: who made it, when, and through which channel")
public record RevisionMetadataResponse(
        long revision,
        Instant revisionAt,
        RevisionOperation operation,
        String author,
        String source,
        String correlationId
) {
}
