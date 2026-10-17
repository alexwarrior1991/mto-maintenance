package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
public class MaintenanceShift extends VersionedEntity {

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

    /**
     * Seccionadores abiertos para bloquear la zona de trabajo. Son todos los que aislan la zona, no
     * dos fijos: con varias vias por turno pueden ser bastantes. Siempre activos de tipo
     * DISCONNECTOR; lo comprueba el servicio al resolver cada id.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "maintenance_shift_disconnector",
            joinColumns = @JoinColumn(name = "shift_id", foreignKey = @ForeignKey(name = "fk_maintenance_shift_disconnector_shift")),
            inverseJoinColumns = @JoinColumn(name = "disconnector_id", foreignKey = @ForeignKey(name = "fk_maintenance_shift_disconnector_asset"))
    )
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @Builder.Default
    private Set<CatenaryAsset> blockingDisconnectors = new LinkedHashSet<>();

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

    /** El jefe de equipo (foreman) de esa noche, de mto-resources: el del equipo o un suplente. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_leader_person_id", foreignKey = @ForeignKey(name = "fk_maintenance_shift_team_leader"))
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private CrewPerson teamLeader;

    /** El supervisor que tiene el turno a su cargo esa noche; puede llevar varios a la vez. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_person_id", foreignKey = @ForeignKey(name = "fk_maintenance_shift_supervisor"))
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private CrewPerson supervisor;

    /**
     * Los linemen de esa noche (de 2 a 6 para iniciarlo, {@code CrewComposition}). Un conjunto que se
     * sustituye entero, como los seccionadores; las personas se leen como estan ahora.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "maintenance_shift_lineman",
            joinColumns = @JoinColumn(name = "shift_id", foreignKey = @ForeignKey(name = "fk_maintenance_shift_lineman_shift")),
            inverseJoinColumns = @JoinColumn(name = "person_id", foreignKey = @ForeignKey(name = "fk_maintenance_shift_lineman_person"))
    )
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @Builder.Default
    private Set<CrewPerson> linemen = new LinkedHashSet<>();

    /**
     * Los vehiculos de esa noche, cada uno con donde se dejo al cerrar. Se ordenan por {@code id} y no
     * por {@code createdAt}: Envers aplica el {@code @OrderBy} a {@code maintenance_shift_vehicle_aud},
     * que no tiene las columnas de auditoria. Lo que se ensena sale de {@link #vehiclesByCode()}.
     */
    @OneToMany(mappedBy = "shift", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<MaintenanceShiftVehicle> vehicles = new ArrayList<>();

    /**
     * Los instrumentos que se llevan esa noche, de mto-resources ({@code V22}): un conjunto que se
     * sustituye entero, como los linemen; el instrumento se lee como esta ahora. Ninguno retirado ni con
     * la calibracion caducada a la fecha del turno ({@code ShiftQualifications}). Lo que se ensena sale
     * de {@link #instrumentsByCode()}.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "maintenance_shift_instrument",
            joinColumns = @JoinColumn(name = "shift_id", foreignKey = @ForeignKey(name = "fk_maintenance_shift_instrument_shift")),
            inverseJoinColumns = @JoinColumn(name = "instrument_id", foreignKey = @ForeignKey(name = "fk_maintenance_shift_instrument_instrument"))
    )
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @Builder.Default
    private Set<MaintenanceInstrument> instruments = new LinkedHashSet<>();

    /** El texto de antes: lo que llevaba un turno sin instrumentos de mto-resources. Se queda para esos. */
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

    /** Los vehiculos por su codigo de flota: el orden de la respuesta, del evento y del parte. */
    public List<MaintenanceShiftVehicle> vehiclesByCode() {
        return vehicles.stream()
                .sorted(java.util.Comparator.comparing((MaintenanceShiftVehicle vehicle) -> vehicle.getVehicle().getCode())
                        .thenComparing(vehicle -> vehicle.getVehicle().getId()))
                .toList();
    }

    /** Los instrumentos por su codigo: el orden de la respuesta, del evento y del parte. */
    public List<MaintenanceInstrument> instrumentsByCode() {
        return instruments.stream()
                .sorted(java.util.Comparator.comparing(MaintenanceInstrument::getCode).thenComparing(MaintenanceInstrument::getId))
                .toList();
    }

    /** Si se le exige la dotacion entera para iniciarse: un equipo sincronizado, o uno que ya la lleva. */
    public boolean hasCrew() {
        return teamLeader != null || !linemen.isEmpty() || (team != null && team.isSynchronized());
    }

    public boolean isOpen() {
        return status == ShiftStatus.PLANNED || status == ShiftStatus.IN_PROGRESS;
    }

    public boolean worksOn(Long trackId) {
        return trackId != null && trackIds.contains(trackId);
    }
}
