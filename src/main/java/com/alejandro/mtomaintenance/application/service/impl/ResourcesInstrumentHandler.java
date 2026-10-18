package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEvent;
import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEventContext;
import com.alejandro.mtomaintenance.application.service.IncomingEventHandler;
import com.alejandro.mtomaintenance.infrastructure.messaging.rabbitmq.ResourcesRabbitMqNames;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceInstrumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Un instrumento de mto-resources ({@code instrument.created/updated/deactivated}): su instantanea en
 * {@code maintenance_instrument}, con la marca de agua dentro del upsert, como una persona. Se leen
 * {@code code}, {@code name}, {@code kind}, {@code serialNumber}, {@code calibrationExpiresOn} y
 * {@code active}; una baja es el mismo upsert con {@code active: false}. Corre dentro de la transaccion
 * del inbox.
 */
@Service
@RequiredArgsConstructor
class ResourcesInstrumentHandler implements IncomingEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcesInstrumentHandler.class);

    static final String INSTRUMENT = "instrument";

    private final MaintenanceInstrumentRepository repository;

    @Override
    public String sourceService() {
        return ResourcesRabbitMqNames.RESOURCES_SOURCE_SERVICE;
    }

    @Override
    public String entityName() {
        return INSTRUMENT;
    }

    @Override
    public void handle(IncomingEvent event, IncomingEventContext context) {
        MasterDataPayload values = MasterDataPayload.of(event.values());
        String code = Objects.requireNonNull(values.string("code"), "instrument.code");
        int applied = repository.upsertFromResources(ResourcesPayloads.uuid(event.entityId(), INSTRUMENT),
                code,
                Objects.requireNonNullElse(values.string("name"), code),
                Objects.requireNonNullElse(values.string("kind"), "OTHER"),
                values.string("serialNumber"),
                ResourcesPayloads.dateOrNull(values, "calibrationExpiresOn", INSTRUMENT),
                values.bool("active", true),
                context.sequenceNumber());
        LOGGER.info("Instrument {} {} from mto-resources: {}", code, event.eventName(),
                applied == 0 ? "older than what is stored, ignored" : "applied");
    }
}
