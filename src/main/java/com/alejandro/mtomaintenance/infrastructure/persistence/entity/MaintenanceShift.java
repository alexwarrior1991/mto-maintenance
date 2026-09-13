package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Turno nocturno: la unidad real de ejecucion y el origen del informe diario. Cubre UNA via; con
 * posesion parcial esa via es principal y la contigua sigue en tension.
 */
@Audited
@Entity
@Table(
        name = "maintenance_shift",
        uniqueConstraints = @UniqueConstraint(name = "uq_maintenance_shift_code", columnNames = "code"),
        indexes = {
                @Index(name = "idx_maintenance_shift_date_team", columnList = "shift_date, team_id"),
                @Index(name = "idx_maintenance_shift_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class MaintenanceShift extends AuditableEntity {

    @NotBlank
    @Size(max = 32)
    @Column(name = "code", nullable = false, length = 32)
    @ToString.Include
    private String code;

    @NotNull
    @Column(name = "shift_date", nullable = false)
    @ToString.Include
    private LocalDate shiftDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", foreignKey = @ForeignKey(name = "fk_maintenance_shift_team"))
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private MaintenanceTeam team;

    @Size(max = 120)
    @Column(name = "base_name", length = 120)
    private String baseName;

    @Size(max = 120)
    @Column(name = "vehicle", length = 120)
    private String vehicle;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "possession_type", nullable = false, columnDefinition = "possession_type")
    @ToString.Include
    private PossessionType possessionType;

    @Column(name = "planned_start")
    private Instant plannedStart;

    @Column(name = "planned_end")
    private Instant plannedEnd;

    @Column(name = "actual_start")
    private Instant actualStart;

    @Column(name = "actual_end")
    private Instant actualEnd;

    @Column(name = "voltage_cutoff_at")
    private Instant voltageCutoffAt;

    @PositiveOrZero
    @Column(name = "net_work_minutes")
    private Integer netWorkMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_a_disconnector_id", foreignKey = @ForeignKey(name = "fk_maintenance_shift_block_a"))
    private CatenaryAsset blockADisconnector;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_b_disconnector_id", foreignKey = @ForeignKey(name = "fk_maintenance_shift_block_b"))
    private CatenaryAsset blockBDisconnector;

    @Size(max = 500)
    @Column(name = "earthing_points", length = 500)
    private String earthingPoints;

    @Size(max = 255)
    @Column(name = "parking_place")
    private String parkingPlace;

    @Column(name = "execution_package_id")
    private Long executionPackageId;

    /**
     * Vias (ids de mto-configuration) que recorre el turno. Una noche puede cubrir mas de una via;
     * mas de un EP es raro y se registra como dos turnos. La tarea de un perfil solo entra en el turno
     * si su via esta aqui.
     */
    @NotEmpty
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "maintenance_shift_track",
            joinColumns = @JoinColumn(name = "shift_id", foreignKey = @ForeignKey(name = "fk_maintenance_shift_track_shift"))
    )
    @Column(name = "track_id", nullable = false)
    @Builder.Default
    @ToString.Include
    private Set<Long> trackIds = new LinkedHashSet<>();

    @Digits(integer = 9, fraction = 3)
    @Column(name = "start_kp", precision = 12, scale = 3)
    private BigDecimal startKp;

    @Digits(integer = 9, fraction = 3)
    @Column(name = "end_kp", precision = 12, scale = 3)
    private BigDecimal endKp;

    @Column(name = "personnel", columnDefinition = "text")
    private String personnel;

    @Size(max = 500)
    @Column(name = "measurement_equipment", length = 500)
    private String measurementEquipment;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    @Column(name = "status", nullable = false, columnDefinition = "shift_status")
    @ToString.Include
    private ShiftStatus status = ShiftStatus.PLANNED;

    @Column(name = "observations", columnDefinition = "text")
    private String observations;

    public boolean isInProgress() {
        return ShiftStatus.IN_PROGRESS == status;
    }

    public boolean worksOn(Long trackId) {
        return trackId != null && trackIds.contains(trackId);
    }
}
