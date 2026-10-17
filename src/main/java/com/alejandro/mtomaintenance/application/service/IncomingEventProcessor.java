package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.messaging.InboxMessageCommand;
import com.alejandro.mtomaintenance.application.dto.messaging.InboxProcessingResult;
import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEventMessage;

/**
 * Aplica una sola vez un evento propio de otro servicio. Es lo unico que conoce el consumidor de
 * RabbitMQ: que la idempotencia la ponga el inbox y el trabajo un {@link IncomingEventHandler} son
 * detalles de la implementacion.
 */
public interface IncomingEventProcessor {

    InboxProcessingResult process(InboxMessageCommand command, IncomingEventMessage message);
}
