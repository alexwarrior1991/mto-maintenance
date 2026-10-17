package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInstrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Los instrumentos de mto-resources, escritos solo con esta sentencia nativa y su marca de agua
 * ({@code sequenceNumber} del outbox de mto-resources) dentro del {@code WHERE}, como las personas y los
 * vehiculos: lo que llega tarde no pisa lo que ya se aplico. Nunca leer-y-escribir.
 */
public interface MaintenanceInstrumentRepository extends JpaRepository<MaintenanceInstrument, UUID> {

    /** De su propio evento ({@code instrument.*}). Sin marca, se aplica y conserva la guardada. */
    @Modifying
    @Query(value = """
            insert into maintenance_instrument (id, code, name, kind, serial_number, calibration_expires_on, active,
                                                source_sequence_number, created_at, updated_at)
            values (:id, :code, :name, :kind, cast(:serialNumber as varchar), cast(:calibrationExpiresOn as date), :active,
                    cast(:sequenceNumber as bigint), now(), now())
            on conflict (id) do update
               set code = excluded.code,
                   name = excluded.name,
                   kind = excluded.kind,
                   serial_number = excluded.serial_number,
                   calibration_expires_on = excluded.calibration_expires_on,
                   active = excluded.active,
                   source_sequence_number = coalesce(excluded.source_sequence_number, maintenance_instrument.source_sequence_number),
                   updated_at = now()
             where maintenance_instrument.source_sequence_number is null
                or excluded.source_sequence_number is null
                or excluded.source_sequence_number >= maintenance_instrument.source_sequence_number
            """, nativeQuery = true)
    int upsertFromResources(@Param("id") UUID id, @Param("code") String code, @Param("name") String name, @Param("kind") String kind,
                            @Param("serialNumber") String serialNumber, @Param("calibrationExpiresOn") LocalDate calibrationExpiresOn,
                            @Param("active") boolean active, @Param("sequenceNumber") Long sequenceNumber);
}
