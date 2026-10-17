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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un instrumento de mto-resources (una pertiga de puesta a tierra, un detector de tension, un aparato de
 * medida), tal como lo necesita un turno: su codigo, su tipo, si sigue activo y hasta cuando vale su
 * calibracion. Solo se lee: la escribe {@code MaintenanceInstrumentRepository} con SQL nativo y la marca
 * de agua dentro de la sentencia, como las personas y los vehiculos. No se audita. El id es el de
 * mto-resources; su tipo va como texto.
 */
@Entity
@Immutable
@Table(name = "maintenance_instrument")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class MaintenanceInstrument {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @ToString.Include
    private UUID id;

    @Column(name = "code", nullable = false, length = 32)
    @ToString.Include
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(name = "serial_number", length = 60)
    private String serialNumber;

    /** {@code null}: no tiene calibracion que caduque. */
    @Column(name = "calibration_expires_on")
    private LocalDate calibrationExpiresOn;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "source_sequence_number")
    private Long sourceSequenceNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
