package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceTeamRepository extends JpaRepository<MaintenanceTeam, UUID> {

    Optional<MaintenanceTeam> findByCode(String code);

    boolean existsByCode(String code);

    Optional<MaintenanceTeam> findBySourceEntityId(UUID sourceEntityId);

    /**
     * Adopta el equipo local de ese codigo la primera vez que mto-resources publica uno: desde ese
     * momento es suyo. Solo uno que no fuera ya de nadie, y solo si ese equipo de mto-resources no esta
     * ya aqui con otro codigo.
     */
    @Modifying
    @Query(value = """
            update maintenance_team
               set source_service = :sourceService,
                   source_entity_id = :sourceEntityId,
                   base_name = null,
                   updated_at = now(),
                   updated_by = 'system'
             where code = :code
               and source_service is null
               and not exists (select 1 from maintenance_team t where t.source_entity_id = :sourceEntityId)
            """, nativeQuery = true)
    int adoptByCode(@Param("code") String code, @Param("sourceService") String sourceService, @Param("sourceEntityId") UUID sourceEntityId);

    /**
     * El equipo como lo cuenta mto-resources, con la marca de agua dentro del {@code WHERE}. Sin base:
     * {@code vehicle} lleva el codigo del vehiculo por defecto, para el informe y los consumidores de
     * antes. 0 filas: llego tarde y no se aplica.
     */
    @Modifying
    @Query(value = """
            insert into maintenance_team (id, code, name, base_name, vehicle, active, source_service, source_entity_id,
                                          source_sequence_number, leader_person_id, supervisor_person_id, chief_supervisor_person_id,
                                          default_vehicle_id, created_at, updated_at, created_by, updated_by)
            values (gen_random_uuid(), :code, :name, null, cast(:vehicle as varchar), :active, :sourceService, :sourceEntityId,
                    cast(:sequenceNumber as bigint), cast(:leaderId as uuid), cast(:supervisorId as uuid), cast(:chiefSupervisorId as uuid),
                    cast(:defaultVehicleId as uuid), now(), now(), 'system', 'system')
            on conflict (source_entity_id) where source_entity_id is not null do update
               set code = excluded.code,
                   name = excluded.name,
                   base_name = null,
                   vehicle = excluded.vehicle,
                   active = excluded.active,
                   leader_person_id = excluded.leader_person_id,
                   supervisor_person_id = excluded.supervisor_person_id,
                   chief_supervisor_person_id = excluded.chief_supervisor_person_id,
                   default_vehicle_id = excluded.default_vehicle_id,
                   source_sequence_number = coalesce(excluded.source_sequence_number, maintenance_team.source_sequence_number),
                   updated_at = now(),
                   updated_by = 'system'
             where maintenance_team.source_sequence_number is null
                or excluded.source_sequence_number is null
                or excluded.source_sequence_number >= maintenance_team.source_sequence_number
            """, nativeQuery = true)
    int upsertFromResources(@Param("sourceService") String sourceService, @Param("sourceEntityId") UUID sourceEntityId,
                            @Param("code") String code, @Param("name") String name, @Param("vehicle") String vehicle,
                            @Param("active") boolean active, @Param("leaderId") UUID leaderId, @Param("supervisorId") UUID supervisorId,
                            @Param("chiefSupervisorId") UUID chiefSupervisorId, @Param("defaultVehicleId") UUID defaultVehicleId,
                            @Param("sequenceNumber") Long sequenceNumber);

    @Modifying
    @Query(value = "delete from maintenance_team_member where team_id = :teamId", nativeQuery = true)
    int deleteMembers(@Param("teamId") UUID teamId);

    @Modifying
    @Query(value = "insert into maintenance_team_member (team_id, person_id) values (:teamId, :personId) on conflict do nothing",
            nativeQuery = true)
    int insertMember(@Param("teamId") UUID teamId, @Param("personId") UUID personId);

    @Modifying
    @Query(value = "delete from maintenance_team_execution_package where team_id = :teamId", nativeQuery = true)
    int deleteExecutionPackages(@Param("teamId") UUID teamId);

    @Modifying
    @Query(value = """
            insert into maintenance_team_execution_package (team_id, execution_package_id) values (:teamId, :executionPackageId)
            on conflict do nothing
            """, nativeQuery = true)
    int insertExecutionPackage(@Param("teamId") UUID teamId, @Param("executionPackageId") Long executionPackageId);

    /**
     * Los EP de los equipos sincronizados y activos de una persona ({@code MaintenanceScope}): los que dirige
     * como jefe, supervisor o jefe de supervisores ({@code V24}), y en el que va de lineman.
     */
    @Query(value = """
            select distinct tep.execution_package_id
              from maintenance_team t
              join maintenance_team_execution_package tep on tep.team_id = t.id
             where t.active and t.source_entity_id is not null
               and (t.leader_person_id = :personId or t.supervisor_person_id = :personId or t.chief_supervisor_person_id = :personId
                    or exists (select 1 from maintenance_team_member m where m.team_id = t.id and m.person_id = :personId))
            """, nativeQuery = true)
    List<Long> findExecutionPackageIdsOfPerson(@Param("personId") UUID personId);

    /** Los equipos sincronizados y activos de una persona, con el mismo criterio: para un turno sin EP. */
    @Query(value = """
            select t.id
              from maintenance_team t
             where t.active and t.source_entity_id is not null
               and (t.leader_person_id = :personId or t.supervisor_person_id = :personId or t.chief_supervisor_person_id = :personId
                    or exists (select 1 from maintenance_team_member m where m.team_id = t.id and m.person_id = :personId))
            """, nativeQuery = true)
    List<UUID> findTeamIdsOfPerson(@Param("personId") UUID personId);
}
