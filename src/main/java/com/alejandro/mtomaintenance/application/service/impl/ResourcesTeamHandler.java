package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEvent;
import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEventContext;
import com.alejandro.mtomaintenance.application.service.IncomingEventHandler;
import com.alejandro.mtomaintenance.infrastructure.messaging.rabbitmq.ResourcesRabbitMqNames;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CrewPersonRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTeamRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Un equipo de mto-resources ({@code team.*}): el equipo sincronizado, su jefe, su supervisor, su jefe de
 * supervisores ({@code V24}: cuenta para el ambito por EP), sus linemen y sus EP. Solo los de disciplina {@code MAINTENANCE}; los de construccion no son de aqui.
 *
 * <ol>
 *   <li>Las personas que trae se insertan si no estaban ({@code ON CONFLICT DO NOTHING}): el evento del
 *       equipo puede llegar antes que el de cada persona, que luego las pisa.</li>
 *   <li>La primera vez, el equipo local de ese codigo se adopta: desde entonces es de mto-resources.</li>
 *   <li>El upsert con la marca de agua dentro del {@code WHERE}; si llego tarde, no se toca nada mas.</li>
 *   <li>Linemen y EP se sustituyen enteros: el evento trae la lista completa.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
class ResourcesTeamHandler implements IncomingEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcesTeamHandler.class);

    static final String TEAM = "team";
    static final String MAINTENANCE = "MAINTENANCE";

    private final MaintenanceTeamRepository teamRepository;
    private final CrewPersonRepository personRepository;

    @Override
    public String sourceService() {
        return ResourcesRabbitMqNames.RESOURCES_SOURCE_SERVICE;
    }

    @Override
    public String entityName() {
        return TEAM;
    }

    @Override
    public void handle(IncomingEvent event, IncomingEventContext context) {
        MasterDataPayload values = MasterDataPayload.of(event.values());
        String code = Objects.requireNonNull(values.string("code"), "team.code").trim().toUpperCase(Locale.ROOT);
        if (!MAINTENANCE.equals(values.string("discipline"))) {
            LOGGER.info("Team {} of mto-resources is of {}, not of maintenance: ignored", code, values.string("discipline"));
            return;
        }
        UUID sourceId = ResourcesPayloads.uuid(event.entityId(), "team");

        UUID leader = person(values.nested("leader"));
        UUID supervisor = person(values.nested("supervisor"));
        UUID chiefSupervisor = person(values.nested("chiefSupervisor"));
        List<UUID> members = values.nestedList("members").stream().map(this::person).filter(Objects::nonNull).toList();

        teamRepository.adoptByCode(code, ResourcesRabbitMqNames.RESOURCES_SOURCE_SERVICE, sourceId);
        MasterDataPayload vehicle = values.nested("defaultVehicle");
        int applied = teamRepository.upsertFromResources(ResourcesRabbitMqNames.RESOURCES_SOURCE_SERVICE, sourceId, code,
                Objects.requireNonNullElse(values.string("name"), code), vehicle.string("code"), values.bool("active", true),
                leader, supervisor, chiefSupervisor, ResourcesPayloads.uuidOrNull(vehicle.string("id")), context.sequenceNumber());
        if (applied == 0) {
            LOGGER.info("Team {} {} from mto-resources is older than what is stored: ignored", code, event.eventName());
            return;
        }

        UUID teamId = teamRepository.findBySourceEntityId(sourceId).orElseThrow().getId();
        teamRepository.deleteMembers(teamId);
        members.forEach(member -> teamRepository.insertMember(teamId, member));
        teamRepository.deleteExecutionPackages(teamId);
        if (event.values().get("executionPackageIds") instanceof List<?> packages) {
            for (Object executionPackage : packages) {
                Long id = ResourcesPayloads.longOrNull(executionPackage);
                if (id != null) {
                    teamRepository.insertExecutionPackage(teamId, id);
                }
            }
        }
        LOGGER.info("Team {} {} from mto-resources applied: leader={}, linemen={}", code, event.eventName(), leader, members.size());
    }

    /** La persona tal como viaja dentro del equipo; se inserta si no estaba. */
    private UUID person(MasterDataPayload person) {
        UUID id = ResourcesPayloads.uuidOrNull(person.string("personId"));
        if (id == null) {
            return null;
        }
        personRepository.insertIfAbsent(id, Objects.requireNonNullElse(person.string("code"), id.toString().substring(0, 16)),
                Objects.requireNonNullElse(person.string("fullName"), "?"), person.string("username"),
                Objects.requireNonNullElse(person.string("category"), "WORKER"));
        return id;
    }
}
