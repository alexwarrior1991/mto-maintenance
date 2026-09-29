package com.alejandro.mtomaintenance.infrastructure.messaging.outbox;

import com.alejandro.mtomaintenance.application.dto.messaging.AsynchronousMessage;
import com.alejandro.mtomaintenance.application.dto.messaging.DomainEvent;
import com.alejandro.mtomaintenance.application.service.DomainEventPublisher;
import com.alejandro.mtomaintenance.configuration.rabbitmq.MaintenanceEventsProperties;
import com.alejandro.mtomaintenance.infrastructure.messaging.rabbitmq.MaintenanceRabbitMqNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Escribe cada evento propio en el outbox, envuelto en el sobre comun, para que el relay lo publique
 * en {@code mto.maintenance.exchange} con la clave {@code mto.maintenance.<entidad>.<evento>}.
 *
 * <p>El agregado del outbox es la entidad del evento ({@code order}-{@code <id>}), de modo que el
 * orden estricto por agregado del relay conserva {@code created} antes que {@code status-changed}
 * de la misma orden aunque el primero falle y se reintente.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final AsynchronousMessageFactory messageFactory;
    private final OutboxService outboxService;
    private final MaintenanceEventsProperties properties;

    @Override
    public void publish(DomainEvent event) {
        publish(UUID.randomUUID(), event);
    }

    @Override
    public void publish(UUID operationId, DomainEvent event) {
        String eventType = MaintenanceRabbitMqNames.eventType(event.entityName(), event.eventName());

        AsynchronousMessage<DomainEvent> message = messageFactory.create(
                operationId,
                event.entityName() + "-" + event.entityId(),
                eventType,
                event
        );

        outboxService.save(
                event.entityName(),
                event.entityId(),
                eventType,
                properties.exchange(),
                MaintenanceRabbitMqNames.routingKey(event.entityName(), event.eventName()),
                message
        );

        log.debug("Domain event in the outbox: {} {} {} (operationId={}, actor={})",
                event.entityName(), event.entityId(), event.eventName(), operationId, message.actor());
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
