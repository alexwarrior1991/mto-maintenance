package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Una persona de mto-resources, tal como la necesita un turno: su codigo, su nombre, su usuario y su
 * categoria. Solo se lee: la escribe {@code CrewPersonRepository} con SQL nativo y la marca de
 * agua dentro de la sentencia, como un activo de datos maestros. No se audita (Envers no veria esas
 * escrituras). El id es el de mto-resources.
 *
 * <p>Sus cualificaciones ({@code V23}) son la lista de {@code person.*.qualifications} tal cual llega,
 * tambien las caducadas: quien decide por la fecha es el turno ({@code ShiftQualifications}). Lo mismo
 * sus periodos no disponibles ({@code V24}, {@code ShiftAvailability}).</p>
 */
@Entity
@Immutable
@Table(name = "crew_person")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class CrewPerson {

    /** La categoria minima para dirigir un equipo (la de mto-resources); un supervisor o mas tambien vale. */
    public static final String TEAM_LEADER = "TEAM_LEADER";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @ToString.Include
    private UUID id;

    @Column(name = "code", nullable = false, length = 16)
    @ToString.Include
    private String code;

    @Column(name = "full_name", nullable = false, length = 260)
    private String fullName;

    @Column(name = "username", length = 100)
    @ToString.Include
    private String username;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "active", nullable = false)
    private Boolean active;

    /** Todas las de la persona, con su caducidad; vacia si no tiene ninguna o aun no llego su evento. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "qualifications", nullable = false, columnDefinition = "jsonb")
    private List<Qualification> qualifications;

    /**
     * Cuando no esta disponible ({@code V24}, {@code person.*.unavailable}): vacaciones, bajas y demas
     * ausencias de mto-resources, sin su clase; vacia si no tiene ninguna o aun no llego su evento.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unavailable", nullable = false, columnDefinition = "jsonb")
    private List<UnavailablePeriod> unavailable;

    @Column(name = "source_sequence_number")
    private Long sourceSequenceNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Si puede dirigir un equipo en un turno: jefe de equipo o mas. Una categoria que este servicio no
     * conoce (llego despues) no se da por buena ni por mala: decide mto-resources, que la publico.
     */
    public boolean canLeadATeam() {
        return switch (category) {
            case "WORKER" -> false;
            default -> true;
        };
    }

    /** Nunca nula: una fila escrita antes de {@code V23}, o una persona construida a mano, no tiene ninguna. */
    public List<Qualification> getQualifications() {
        return qualifications == null ? List.of() : qualifications;
    }

    /** Nunca nula: una fila escrita antes de {@code V24}, o una persona construida a mano, no tiene ninguno. */
    public List<UnavailablePeriod> getUnavailable() {
        return unavailable == null ? List.of() : unavailable;
    }

    /**
     * Una cualificacion de la persona, como la publica mto-resources: el codigo de su tipo
     * ({@code RAIL-DRIVER}), su clase, el permiso de conducir si lo es, y hasta cuando vale
     * ({@code null}: no caduca). Lo que no se usa aqui se ignora al leer.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Qualification(String code, String kind, String drivingLicence, LocalDate expiresOn) {
    }
}
