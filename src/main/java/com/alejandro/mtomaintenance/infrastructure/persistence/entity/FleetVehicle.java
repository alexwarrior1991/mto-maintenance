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
 * Un vehiculo de mto-resources, tal como lo necesita un turno: por donde va ({@code mobility}), si se
 * mueve solo, si remolca, cuantas plazas tiene y si esta disponible. Solo se lee: la escribe
 * {@code FleetVehicleRepository} con SQL nativo y la marca de agua dentro de la sentencia. No se
 * audita. El id es el de mto-resources; sus enumerados van como texto.
 *
 * <p>Desde {@code V22}, tambien sus documentos con su caducidad (inspecciones, seguro, certificados) y
 * la habilitacion que su modelo exige a quien lo conduce: lo que mira {@code ShiftQualifications}.</p>
 */
@Entity
@Immutable
@Table(name = "fleet_vehicle")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class FleetVehicle {

    public static final String RAIL_ONLY = "RAIL_ONLY";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @ToString.Include
    private UUID id;

    @Column(name = "code", nullable = false, length = 32)
    @ToString.Include
    private String code;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "type_code", length = 32)
    private String typeCode;

    @Column(name = "type_name", length = 150)
    private String typeName;

    @Column(name = "mobility", nullable = false, length = 20)
    private String mobility;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "self_propelled", nullable = false)
    private Boolean selfPropelled;

    @Column(name = "portable", nullable = false)
    private Boolean portable;

    @Column(name = "towing", nullable = false)
    private Boolean towing;

    @Column(name = "seats")
    private Integer seats;

    @Column(name = "operational_status", nullable = false, length = 30)
    private String operationalStatus;

    @Column(name = "available", nullable = false)
    private Boolean available;

    @Column(name = "active", nullable = false)
    private Boolean active;

    /** Los de {@code vehicle.*.documents}, tambien los caducados; vacia si no tiene ninguno. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "documents", nullable = false, columnDefinition = "jsonb")
    private List<Document> documents;

    /** El codigo del tipo de cualificacion que exige su modelo al conductor ({@code RR-OPERATOR}); {@code null}, ninguna. */
    @Column(name = "required_driver_qualification", length = 32)
    private String requiredDriverQualification;

    @Column(name = "source_sequence_number")
    private Long sourceSequenceNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isRailOnly() {
        return RAIL_ONLY.equals(mobility);
    }

    /** Nunca nula: una fila escrita antes de {@code V22}, o un vehiculo construido a mano, no tiene ninguno. */
    public List<Document> getDocuments() {
        return documents == null ? List.of() : documents;
    }

    /**
     * Un documento del vehiculo, como lo publica mto-resources: su clase ({@code ROAD_INSPECTION}), su
     * referencia y hasta cuando vale (siempre caduca).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(String kind, String reference, LocalDate expiresOn) {
    }
}
