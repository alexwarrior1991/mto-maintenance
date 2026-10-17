package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.InboxMessageCommand;
import com.alejandro.mtomaintenance.application.dto.messaging.InboxProcessingResult;
import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEventContext;
import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEventMessage;
import com.alejandro.mtomaintenance.application.service.InboxMessageService;
import com.alejandro.mtomaintenance.application.service.IncomingEventProcessor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Une el inbox y el despachador: cada evento de otro servicio se aplica una vez, nunca dos.
 *
 * <p>No es transaccional a proposito, como su gemelo de datos maestros
 * ({@link IdempotentMasterDataEventProcessor}): la
 * transaccion del intento la abre {@link InboxMessageService#process}, y cuando el {@code catch} se
 * ejecuta ya ha revertido y soltado sus bloqueos, que es lo unico que deja escribir el fallo sin que
 * las dos transacciones se esperen. La excepcion se relanza siempre: el contenedor decide reintentar
 * o mandar a la DLQ.</p>
 */
@Service
@RequiredArgsConstructor
class IdempotentIncomingEventProcessor implements IncomingEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotentIncomingEventProcessor.class);

    private final InboxMessageService inboxMessageService;
    private final DispatchingIncomingEventHandler dispatcher;

    @Override
    public InboxProcessingResult process(InboxMessageCommand command, IncomingEventMessage message) {
        try {
            IncomingEventContext context = new IncomingEventContext(command.sourceService(), command.sequenceNumber(), command.messageId());
            return inboxMessageService.process(command, () -> dispatcher.handle(message.data(), context));
        } catch (RuntimeException failure) {
            LOGGER.error("Incoming event failed and was recorded as failed in the inbox: messageId={}, sourceService={}, eventType={}",
                    command.messageId(), command.sourceService(), command.eventType(), failure);
            inboxMessageService.recordFailure(command, failure);
            throw failure;
        }
    }
}
