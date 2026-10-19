package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CrewPerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Las personas de mto-resources. Se escriben solo con estas sentencias nativas, con la marca de agua
 * ({@code sequenceNumber} del outbox de mto-resources) dentro del {@code WHERE}: lo que llega tarde no
 * pisa lo que ya se aplico. Nunca leer-y-escribir.
 */
public interface CrewPersonRepository extends JpaRepository<CrewPerson, UUID> {

    /**
     * De su propio evento ({@code person.*}). Sin marca, se aplica y conserva la guardada.
     *
     * <p>{@code qualifications} es la lista de la persona ya en JSON ({@code [{code, kind, drivingLicence,
     * expiresOn}]}), y {@code unavailable} la de sus periodos no disponibles ({@code [{from, to}]},
     * {@code V24}), las dos escritas en la misma sentencia y con la misma marca; {@code null} (un evento de
     * un mto-resources anterior, sin la clave) deja la que habia.</p>
     */
    @Modifying
    @Query(value = """
            insert into crew_person (id, code, full_name, username, category, active, qualifications, unavailable, source_sequence_number,
                                     created_at, updated_at)
            values (:id, :code, :fullName, cast(:username as varchar), :category, :active,
                    coalesce(cast(:qualifications as jsonb), '[]'::jsonb), coalesce(cast(:unavailable as jsonb), '[]'::jsonb),
                    cast(:sequenceNumber as bigint), now(), now())
            on conflict (id) do update
               set code = excluded.code,
                   full_name = excluded.full_name,
                   username = excluded.username,
                   category = excluded.category,
                   active = excluded.active,
                   qualifications = coalesce(cast(:qualifications as jsonb), crew_person.qualifications),
                   unavailable = coalesce(cast(:unavailable as jsonb), crew_person.unavailable),
                   source_sequence_number = coalesce(excluded.source_sequence_number, crew_person.source_sequence_number),
                   updated_at = now()
             where crew_person.source_sequence_number is null
                or excluded.source_sequence_number is null
                or excluded.source_sequence_number >= crew_person.source_sequence_number
            """, nativeQuery = true)
    int upsertFromResources(@Param("id") UUID id, @Param("code") String code, @Param("fullName") String fullName,
                            @Param("username") String username, @Param("category") String category, @Param("active") boolean active,
                            @Param("qualifications") String qualifications, @Param("unavailable") String unavailable,
                            @Param("sequenceNumber") Long sequenceNumber);

    /**
     * De dentro del evento de un equipo, que puede llegar antes que el de la persona: solo si no estaba,
     * y sin marca, para que su propio evento la pise sea cual sea su numero.
     */
    @Modifying
    @Query(value = """
            insert into crew_person (id, code, full_name, username, category, active, source_sequence_number, created_at, updated_at)
            values (:id, :code, :fullName, cast(:username as varchar), :category, true, null, now(), now())
            on conflict (id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("code") String code, @Param("fullName") String fullName,
                       @Param("username") String username, @Param("category") String category);

    /**
     * La persona de un usuario de Keycloak ({@code MaintenanceScope}), sin distinguir mayusculas como
     * Keycloak, por el indice {@code idx_crew_person_username}. Si dos fichas dijeran el mismo usuario,
     * la activa primero.
     */
    @Query(value = """
            select * from crew_person
             where lower(username) = lower(:username)
             order by active desc, code
             limit 1
            """, nativeQuery = true)
    Optional<CrewPerson> findByUsername(@Param("username") String username);
}
