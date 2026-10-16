package com.alejandro.mtomaintenance.application.dto.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * El sobre de un evento propio de otro servicio del dominio: el {@code AsynchronousMessage} comun,
 * fijado a {@link IncomingEvent} para que el convertidor no dependa de inferir genericos.
 *
 * <p>{@code messageHash} no es una firma: la que se comprueba viaja en la cabecera
 * {@code messageSignature}, sobre los bytes reales.</p>
 */
public record IncomingEventMessage(
        UUID operationId,
        String referenceId,
        String origin,
        Instant creationDate,
        String eventType,
        IncomingEvent data,
        String messageHash,
        MessageActor actor,
        String correlationId
) {
}
