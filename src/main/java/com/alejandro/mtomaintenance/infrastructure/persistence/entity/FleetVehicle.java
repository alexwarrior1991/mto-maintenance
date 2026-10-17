package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Un vehiculo de mto-resources, tal como lo necesita un turno: por donde va ({@code mobility}), si se
 * mueve solo, si remolca, cuantas plazas tiene y si esta disponible. Solo se lee: la escribe
 * {@code FleetVehicleRepository} con SQL nativo y la marca de agua dentro de la sentencia. No se
 * audita. El id es el de mto-resources; sus enumerados van como texto.
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

    @Column(name = "source_sequence_number")
    private Long sourceSequenceNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isRailOnly() {
        return RAIL_ONLY.equals(mobility);
    }
}
