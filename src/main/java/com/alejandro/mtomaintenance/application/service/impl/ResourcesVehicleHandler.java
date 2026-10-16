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

import java.util.List;
import java.util.Objects;

/**
 * Un vehiculo de mto-resources ({@code vehicle.*}): su instantanea en {@code fleet_vehicle}. Que remolca
 * lo dice su modelo ({@code type.capabilities} contiene {@code TOWING}).
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
        List<?> capabilities = event.values().get("type") instanceof java.util.Map<?, ?> map && map.get("capabilities") instanceof List<?> list
                ? list : List.of();
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
                context.sequenceNumber());
        LOGGER.info("Vehicle {} {} from mto-resources: {}", values.string("code"), event.eventName(),
                applied == 0 ? "older than what is stored, ignored" : "applied");
    }
}
