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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Una persona de mto-resources ({@code person.created/updated/deactivated}): su instantanea en
 * {@code crew_person}, con la marca de agua dentro del upsert. Corre dentro de la transaccion del inbox.
 *
 * <p>Sus cualificaciones ({@code qualifications}, {@code V23}) van en la misma sentencia, reducidas a
 * {@code code}, {@code kind}, {@code drivingLicence} y {@code expiresOn}, y sus periodos no disponibles
 * ({@code unavailable}, {@code V24}) reducidos a {@code from} y {@code to}; un evento sin la clave (un
 * mto-resources anterior) deja lo que habia.</p>
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
                ResourcesPayloads.jsonList(event.values(), "qualifications", ResourcesPersonHandler::qualification),
                ResourcesPayloads.unavailable(event.values(), "person"),
                context.sequenceNumber());
        LOGGER.info("Person {} {} from mto-resources: {}", values.string("code"), event.eventName(),
                applied == 0 ? "older than what is stored, ignored" : "applied");
    }

    /** Una cualificacion tal como se guarda; sin codigo no hay nada que comprobar. */
    private static Map<String, Object> qualification(MasterDataPayload qualification) {
        String code = qualification.string("code");
        if (code == null || code.isBlank()) {
            return null;
        }
        Map<String, Object> kept = new LinkedHashMap<>();
        kept.put("code", code);
        kept.put("kind", qualification.string("kind"));
        kept.put("drivingLicence", qualification.string("drivingLicence"));
        LocalDate expiresOn = ResourcesPayloads.dateOrNull(qualification, "expiresOn", "person");
        kept.put("expiresOn", expiresOn == null ? null : expiresOn.toString());
        return kept;
    }
}
