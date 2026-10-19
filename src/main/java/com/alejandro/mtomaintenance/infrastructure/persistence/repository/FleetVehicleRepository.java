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

    /**
     * De su propio evento ({@code vehicle.*}). {@code documents} es la lista ya en JSON ({@code [{kind,
     * reference, expiresOn}]}) y {@code requiredDriverQualification} el codigo que exige su modelo al
     * conductor, los dos en la misma sentencia y con la misma marca. Un evento sin la clave (un
     * mto-resources anterior) deja lo que habia: {@code documents} a {@code null}, y
     * {@code requiredDriverQualificationKnown} a {@code false}, porque alli {@code null} es «no exige».
     * Lo mismo {@code unavailable} ({@code [{from, to}]}) y {@code capabilities} ({@code ["TOWING", ...]}),
     * de {@code V24}: {@code null} deja lo que habia.
     */
    @Modifying
    @Query(value = """
            insert into fleet_vehicle (id, code, name, type_code, type_name, mobility, category, self_propelled, portable, towing, seats,
                                       operational_status, available, active, documents, required_driver_qualification,
                                       unavailable, capabilities, source_sequence_number, created_at, updated_at)
            values (:id, :code, cast(:name as varchar), cast(:typeCode as varchar), cast(:typeName as varchar), :mobility, :category, :selfPropelled, :portable, :towing, cast(:seats as integer),
                    :operationalStatus, :available, :active, coalesce(cast(:documents as jsonb), '[]'::jsonb),
                    cast(:requiredDriverQualification as varchar), coalesce(cast(:unavailable as jsonb), '[]'::jsonb),
                    coalesce(cast(:capabilities as jsonb), '[]'::jsonb), cast(:sequenceNumber as bigint), now(), now())
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
                   documents = coalesce(cast(:documents as jsonb), fleet_vehicle.documents),
                   required_driver_qualification = case when :requiredDriverQualificationKnown then excluded.required_driver_qualification
                                                        else fleet_vehicle.required_driver_qualification end,
                   unavailable = coalesce(cast(:unavailable as jsonb), fleet_vehicle.unavailable),
                   capabilities = coalesce(cast(:capabilities as jsonb), fleet_vehicle.capabilities),
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
                            @Param("active") boolean active, @Param("documents") String documents,
                            @Param("requiredDriverQualification") String requiredDriverQualification,
                            @Param("requiredDriverQualificationKnown") boolean requiredDriverQualificationKnown,
                            @Param("unavailable") String unavailable, @Param("capabilities") String capabilities,
                            @Param("sequenceNumber") Long sequenceNumber);
}
