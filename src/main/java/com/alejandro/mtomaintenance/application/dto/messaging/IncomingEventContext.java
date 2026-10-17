package com.alejandro.mtomaintenance.application.dto.messaging;

/**
 * Lo que viaja al lado del evento y no dentro: quien lo publico y su numero en la secuencia del
 * emisor, la marca de agua con la que un manejador descarta lo que llega tarde.
 *
 * @param sourceService  el servicio que lo publico ({@code origin} del sobre)
 * @param sequenceNumber numero del mensaje en la secuencia global del outbox del emisor, o
 *                       {@code null} si no viajaba
 * @param messageId      la clave de idempotencia con la que lo registro el inbox
 */
public record IncomingEventContext(String sourceService, Long sequenceNumber, String messageId) {
}
