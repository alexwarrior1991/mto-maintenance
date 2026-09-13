package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tarea de una orden. En el preventivo, un perfil concreto del tramo con los tipos de tarea
 * realizados; se ejecuta dentro de un turno y es una fila del informe diario.
 */
@Audited
@Entity
@Table(
        name = "maintenance_task",
        uniqueConstraints = @UniqueConstraint(name = "uq_maintenance_task_order_sequence", columnNames = {"order_id", "sequence"}),
        indexes = {
                @Index(name = "idx_maintenance_task_shift", columnList = "shift_id"),
                @Index(name = "idx_maintenance_task_asset_status", columnList = "asset_id, status"),
                @Index(name = "idx_maintenance_task_order_status", columnList = "order_id, status")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class MaintenanceTask extends AuditableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_maintenance_task_order"))
    private MaintenanceOrder order;

    @NotNull
    @Column(name = "sequence", nullable = false)
    @ToString.Include
    private Integer sequence;

    @NotBlank
    @Size(max = 500)
    @Column(name = "description", nullable = false, length = 500)
    @ToString.Include
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    @Column(name = "status", nullable = false, columnDefinition = "maintenance_task_status")
    @ToString.Include
    private MaintenanceTaskStatus status = MaintenanceTaskStatus.PENDING;

    @Size(max = 100)
    @Column(name = "assigned_user", length = 100)
    private String assignedUser;

    /** Perfil (u otro activo) concreto sobre el que se trabaja. Nulo en tareas genericas. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", foreignKey = @ForeignKey(name = "fk_maintenance_task_asset"))
    private CatenaryAsset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", foreignKey = @ForeignKey(name = "fk_maintenance_task_shift"))
    private MaintenanceShift shift;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "defects_found", columnDefinition = "text")
    private String defectsFound;

    /** Trabajos realizados, tal y como se escriben en el informe diario. */
    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Convert(converter = PhotoReferencesConverter.class)
    @Column(name = "photo_refs", columnDefinition = "text")
    @Builder.Default
    private List<String> photoRefs = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "maintenance_task_task_type",
            joinColumns = @JoinColumn(name = "task_id", foreignKey = @ForeignKey(name = "fk_maintenance_task_task_type_task")),
            inverseJoinColumns = @JoinColumn(name = "task_type_id", foreignKey = @ForeignKey(name = "fk_maintenance_task_task_type_type"))
    )
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @Builder.Default
    private Set<MaintenanceTaskType> taskTypes = new LinkedHashSet<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex asc")
    @Builder.Default
    private List<MaintenanceTaskCheckItem> checkItems = new ArrayList<>();

    public boolean isOpen() {
        return MaintenanceTaskStatus.PENDING == status || MaintenanceTaskStatus.IN_PROGRESS == status;
    }

    public boolean requiresFullPossession() {
        return taskTypes.stream().anyMatch(MaintenanceTaskType::getRequiresFullPossession);
    }

    public boolean isDiagnostic() {
        return taskTypes.stream().anyMatch(MaintenanceTaskType::getDiagnostic);
    }
}
