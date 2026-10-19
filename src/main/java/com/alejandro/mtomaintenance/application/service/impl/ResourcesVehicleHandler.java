package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEvent;
import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEventContext;
import com.alejandro.mtomaintenance.application.service.IncomingEventHandler;
import com.alejandro.mtomaintenance.infrastructure.messaging.rabbitmq.ResourcesRabbitMqNames;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.FleetVehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Un vehiculo de mto-resources ({@code vehicle.*}): su instantanea en {@code fleet_vehicle}. Que remolca
 * lo dice su modelo ({@code type.capabilities} contiene {@code TOWING}).
 *
 * <p>Sus documentos ({@code documents}: {@code kind}, {@code reference}, {@code expiresOn}) y la
 * habilitacion que su modelo exige al conductor ({@code type.requiredDriverQualification}, {@code V23})
 * van en la misma sentencia, y tambien sus periodos no disponibles ({@code unavailable}) y las capacidades
 * de su modelo ({@code type.capabilities}, {@code V24}); un evento sin la clave (un mto-resources
 * anterior) deja lo que habia.</p>
 */
@Service
@RequiredArgsConstructor
class ResourcesVehicleHandler implements IncomingEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcesVehicleHandler.class);

    static final String VEHICLE = "vehicle";

    private final FleetVehicleRepository repository;

    @Override
    public String sourceService() {
        return ResourcesRabbitMqNames.RESOURCES_SOURCE_SERVICE;
    }

    @Override
    public String entityName() {
        return VEHICLE;
    }

    @Override
    public void handle(IncomingEvent event, IncomingEventContext context) {
        MasterDataPayload values = MasterDataPayload.of(event.values());
        MasterDataPayload type = values.nested("type");
        Map<?, ?> typeValues = event.values().get("type") instanceof Map<?, ?> map ? map : Map.of();
        List<?> capabilities = typeValues.get("capabilities") instanceof List<?> list ? list : List.of();
        int applied = repository.upsertFromResources(ResourcesPayloads.uuid(event.entityId(), "vehicle"),
                Objects.requireNonNull(values.string("code"), "vehicle.code"),
                values.string("name"),
                type.string("code"),
                type.string("name"),
                Objects.requireNonNull(type.string("mobility"), "vehicle.type.mobility"),
                Objects.requireNonNull(type.string("category"), "vehicle.type.category"),
                type.bool("selfPropelled", true),
                type.bool("portable", false),
                capabilities.contains("TOWING"),
                type.integerValue("seats"),
                Objects.requireNonNullElse(values.string("operationalStatus"), "OPERATIONAL"),
                values.bool("available", true),
                values.bool("active", true),
                ResourcesPayloads.jsonList(event.values(), "documents", ResourcesVehicleHandler::document),
                type.string("requiredDriverQualification"),
                typeValues.containsKey("requiredDriverQualification"),
                ResourcesPayloads.unavailable(event.values(), "vehicle"),
                ResourcesPayloads.jsonStrings(typeValues, "capabilities"),
                context.sequenceNumber());
        LOGGER.info("Vehicle {} {} from mto-resources: {}", values.string("code"), event.eventName(),
                applied == 0 ? "older than what is stored, ignored" : "applied");
    }

    /** Un documento tal como se guarda; sin su clase no se sabe que es. */
    private static Map<String, Object> document(MasterDataPayload document) {
        String kind = document.string("kind");
        if (kind == null || kind.isBlank()) {
            return null;
        }
        Map<String, Object> kept = new LinkedHashMap<>();
        kept.put("kind", kind);
        kept.put("reference", document.string("reference"));
        LocalDate expiresOn = ResourcesPayloads.dateOrNull(document, "expiresOn", "vehicle");
        kept.put("expiresOn", expiresOn == null ? null : expiresOn.toString());
        return kept;
    }
}
