package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import java.util.List;
import java.util.UUID;

/** Orden de mantenimiento. Las transiciones de estado pasan siempre por el servicio. */
@Audited
@Entity
@Table(
        name = "maintenance_order",
        uniqueConstraints = @UniqueConstraint(name = "uq_maintenance_order_code", columnNames = "code"),
        indexes = {
                @Index(name = "idx_maintenance_order_status_planned_date", columnList = "status, planned_date"),
                @Index(name = "idx_maintenance_order_asset_status", columnList = "asset_id, status"),
                @Index(name = "idx_maintenance_order_track_kp", columnList = "track_id, start_kp"),
                @Index(name = "idx_maintenance_order_execution_package", columnList = "execution_package_id"),
                @Index(name = "idx_maintenance_order_type_priority", columnList = "type, priority"),
                @Index(name = "idx_maintenance_order_team", columnList = "team_id")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class MaintenanceOrder extends AuditableEntity {

    @NotBlank
    @Size(max = 32)
    @Column(name = "code", nullable = false, length = 32)
    @ToString.Include
    private String code;

    @NotBlank
    @Size(max = 255)
    @Column(name = "title", nullable = false)
    @ToString.Include
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false, columnDefinition = "maintenance_order_type")
    @ToString.Include
    private MaintenanceOrderType type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    @Column(name = "status", nullable = false, columnDefinition = "maintenance_order_status")
    @ToString.Include
    private MaintenanceOrderStatus status = MaintenanceOrderStatus.DRAFT;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    @Column(name = "priority", nullable = false, columnDefinition = "maintenance_priority")
    private MaintenancePriority priority = MaintenancePriority.MEDIUM;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false, foreignKey = @ForeignKey(name = "fk_maintenance_order_asset"))
    private CatenaryAsset asset;

    @Column(name = "execution_package_id")
    private Long executionPackageId;

    @Column(name = "track_id")
    private Long trackId;

    @Column(name = "station_id")
    private Long stationId;

    @Digits(integer = 9, fraction = 3)
    @Column(name = "start_kp", precision = 12, scale = 3)
    private BigDecimal startKp;

    @Digits(integer = 9, fraction = 3)
    @Column(name = "end_kp", precision = 12, scale = 3)
    private BigDecimal endKp;

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Column(name = "actual_start_date")
    private Instant actualStartDate;

    @Column(name = "actual_end_date")
    private Instant actualEndDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", foreignKey = @ForeignKey(name = "fk_maintenance_order_team"))
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private MaintenanceTeam team;

    @Size(max = 100)
    @Column(name = "assigned_user", length = 100)
    private String assignedUser;

    @Column(name = "closing_notes", columnDefinition = "text")
    private String closingNotes;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_inspection_id", foreignKey = @ForeignKey(name = "fk_maintenance_order_origin_inspection"))
    private MaintenanceInspection originInspection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_defect_id", foreignKey = @ForeignKey(name = "fk_maintenance_order_origin_defect"))
    private CatenaryDefect originDefect;

    /** Proyecto de mto-stock contra el que se reservan los materiales. Nulo si la orden no tiene EP. */
    @Column(name = "stock_project_id")
    private UUID stockProjectId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequence asc")
    @Builder.Default
    private List<MaintenanceTask> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MaintenanceMaterialUsage> materials = new ArrayList<>();

    public boolean isInProgress() {
        return MaintenanceOrderStatus.IN_PROGRESS == status;
    }

    public boolean isTerminal() {
        return MaintenanceOrderStatus.COMPLETED == status || MaintenanceOrderStatus.CANCELLED == status;
    }

    /** Copia la localizacion del activo: la orden queda situada aunque el activo cambie despues. */
    public void locateAt(CatenaryAsset target) {
        this.executionPackageId = target.getExecutionPackageId();
        this.trackId = target.getTrackId();
        this.stationId = target.getStationId();
        this.startKp = target.getStartKp();
        this.endKp = target.getEndKp();
    }
}
