package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FleetVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Los vehiculos de mto-resources, escritos solo con esta sentencia nativa y su marca de agua dentro
 * del {@code WHERE}, como las personas.
 */
public interface FleetVehicleRepository extends JpaRepository<FleetVehicle, UUID> {

    @Modifying
    @Query(value = """
            insert into fleet_vehicle (id, code, name, type_code, type_name, mobility, category, self_propelled, portable, towing, seats,
                                       operational_status, available, active, source_sequence_number, created_at, updated_at)
            values (:id, :code, cast(:name as varchar), cast(:typeCode as varchar), cast(:typeName as varchar), :mobility, :category, :selfPropelled, :portable, :towing, cast(:seats as integer),
                    :operationalStatus, :available, :active, cast(:sequenceNumber as bigint), now(), now())
            on conflict (id) do update
               set code = excluded.code,
                   name = excluded.name,
                   type_code = excluded.type_code,
                   type_name = excluded.type_name,
                   mobility = excluded.mobility,
                   category = excluded.category,
                   self_propelled = excluded.self_propelled,
                   portable = excluded.portable,
                   towing = excluded.towing,
                   seats = excluded.seats,
                   operational_status = excluded.operational_status,
                   available = excluded.available,
                   active = excluded.active,
                   source_sequence_number = coalesce(excluded.source_sequence_number, fleet_vehicle.source_sequence_number),
                   updated_at = now()
             where fleet_vehicle.source_sequence_number is null
                or excluded.source_sequence_number is null
                or excluded.source_sequence_number >= fleet_vehicle.source_sequence_number
            """, nativeQuery = true)
    int upsertFromResources(@Param("id") UUID id, @Param("code") String code, @Param("name") String name,
                            @Param("typeCode") String typeCode, @Param("typeName") String typeName, @Param("mobility") String mobility,
                            @Param("category") String category, @Param("selfPropelled") boolean selfPropelled,
                            @Param("portable") boolean portable, @Param("towing") boolean towing, @Param("seats") Integer seats,
                            @Param("operationalStatus") String operationalStatus, @Param("available") boolean available,
                            @Param("active") boolean active, @Param("sequenceNumber") Long sequenceNumber);
}
