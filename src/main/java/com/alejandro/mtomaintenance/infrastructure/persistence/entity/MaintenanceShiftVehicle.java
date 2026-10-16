package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Un vehiculo de mto-resources en un turno, y donde se ha podido dejar al cerrarlo: no hay base fija.
 * El aparcamiento es un sitio del catalogo de mto-resources, una via y un kp, o una descripcion. El
 * vehiculo se lee como esta ahora ({@code NOT_AUDITED}): es una instantanea escrita con SQL nativo.
 */
@Audited
@Entity
@Table(name = "maintenance_shift_vehicle",
        uniqueConstraints = @UniqueConstraint(name = "uq_maintenance_shift_vehicle", columnNames = {"shift_id", "vehicle_id"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class MaintenanceShiftVehicle extends AuditableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_maintenance_shift_vehicle_shift"))
    private MaintenanceShift shift;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_maintenance_shift_vehicle_vehicle"))
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ToString.Include
    private FleetVehicle vehicle;

    @Column(name = "parking_site_id")
    private UUID parkingSiteId;

    @Column(name = "parking_track_id")
    private Long parkingTrackId;

    @Digits(integer = 9, fraction = 3)
    @Column(name = "parking_kp", precision = 12, scale = 3)
    private BigDecimal parkingKp;

    @Size(max = 500)
    @Column(name = "parking_description", length = 500)
    private String parkingDescription;

    public static MaintenanceShiftVehicle of(MaintenanceShift shift, FleetVehicle vehicle) {
        MaintenanceShiftVehicle shiftVehicle = new MaintenanceShiftVehicle();
        shiftVehicle.shift = shift;
        shiftVehicle.vehicle = vehicle;
        return shiftVehicle;
    }

    public boolean hasParking() {
        return parkingSiteId != null || parkingTrackId != null || parkingDescription != null;
    }
}
