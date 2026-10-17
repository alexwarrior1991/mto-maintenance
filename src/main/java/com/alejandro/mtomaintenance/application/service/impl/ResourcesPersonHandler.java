package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEvent;
import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEventContext;
import com.alejandro.mtomaintenance.application.service.IncomingEventHandler;
import com.alejandro.mtomaintenance.infrastructure.messaging.rabbitmq.ResourcesRabbitMqNames;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CrewPersonRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Una persona de mto-resources ({@code person.created/updated/deactivated}): su instantanea en
 * {@code crew_person}, con la marca de agua dentro del upsert. Corre dentro de la transaccion del inbox.
 */
@Service
@RequiredArgsConstructor
class ResourcesPersonHandler implements IncomingEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcesPersonHandler.class);

    static final String PERSON = "person";

    private final CrewPersonRepository repository;

    @Override
    public String sourceService() {
        return ResourcesRabbitMqNames.RESOURCES_SOURCE_SERVICE;
    }

    @Override
    public String entityName() {
        return PERSON;
    }

    @Override
    public void handle(IncomingEvent event, IncomingEventContext context) {
        MasterDataPayload values = MasterDataPayload.of(event.values());
        int applied = repository.upsertFromResources(ResourcesPayloads.uuid(event.entityId(), "person"),
                Objects.requireNonNull(values.string("code"), "person.code"),
                Objects.requireNonNull(values.string("fullName"), "person.fullName"),
                values.string("username"),
                Objects.requireNonNull(values.string("category"), "person.category"),
                values.bool("active", true),
                context.sequenceNumber());
        LOGGER.info("Person {} {} from mto-resources: {}", values.string("code"), event.eventName(),
                applied == 0 ? "older than what is stored, ignored" : "applied");
    }
}
