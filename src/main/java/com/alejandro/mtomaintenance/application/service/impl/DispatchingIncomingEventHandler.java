package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEvent;
import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEventContext;
import com.alejandro.mtomaintenance.application.service.IncomingEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lleva cada evento de otro servicio al {@link IncomingEventHandler} de su emisor y su entidad. Lo que
 * no tiene manejador se registra y se ignora: la cola esta atada a lo que interesa, pero el emisor puede
 * publicar una entidad nueva sin coordinarse, y eso no es un error.
 */
@Component
class DispatchingIncomingEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DispatchingIncomingEventHandler.class);

    private final Map<String, IncomingEventHandler> handlers = new HashMap<>();

    DispatchingIncomingEventHandler(List<IncomingEventHandler> handlers) {
        for (IncomingEventHandler handler : handlers) {
            String key = key(handler.sourceService(), handler.entityName());
            IncomingEventHandler previous = this.handlers.putIfAbsent(key, handler);
            if (previous != null) {
                throw new IllegalStateException("Two handlers for the events of " + key + ": "
                        + previous.getClass().getSimpleName() + " and " + handler.getClass().getSimpleName());
            }
        }
    }

    void handle(IncomingEvent event, IncomingEventContext context) {
        IncomingEventHandler handler = handlers.get(key(context.sourceService(), event.entityName()));
        if (handler == null) {
            LOGGER.info("Incoming event without a handler, ignored: {} {}.{} {}", context.sourceService(),
                    event.entityName(), event.eventName(), event.entityId());
            return;
        }
        handler.handle(event, context);
    }

    private static String key(String sourceService, String entityName) {
        return String.valueOf(sourceService).trim().toLowerCase(Locale.ROOT) + "/" + String.valueOf(entityName).trim().toLowerCase(Locale.ROOT);
    }
}
