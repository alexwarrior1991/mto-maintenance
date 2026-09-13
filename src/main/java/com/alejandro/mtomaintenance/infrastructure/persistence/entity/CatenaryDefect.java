package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Defecto o anomalia detectada en un activo, con su ciclo de vida hasta el cierre. */
@Audited
@Entity
@Table(
        name = "catenary_defect",
        uniqueConstraints = @UniqueConstraint(name = "uq_catenary_defect_code", columnNames = "code"),
        indexes = {
                @Index(name = "idx_catenary_defect_severity_status", columnList = "severity, status"),
                @Index(name = "idx_catenary_defect_asset", columnList = "asset_id"),
                @Index(name = "idx_catenary_defect_order", columnList = "order_id"),
                @Index(name = "idx_catenary_defect_detected_at", columnList = "detected_at")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class CatenaryDefect extends AuditableEntity {

    @NotBlank
    @Size(max = 32)
    @Column(name = "code", nullable = false, length = 32)
    @ToString.Include
    private String code;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false, foreignKey = @ForeignKey(name = "fk_catenary_defect_asset"))
    private CatenaryAsset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", foreignKey = @ForeignKey(name = "fk_catenary_defect_inspection"))
    private MaintenanceInspection inspection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", foreignKey = @ForeignKey(name = "fk_catenary_defect_order"))
    private MaintenanceOrder order;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "severity", nullable = false, columnDefinition = "defect_severity")
    @ToString.Include
    private DefectSeverity severity;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    @Column(name = "status", nullable = false, columnDefinition = "defect_status")
    @ToString.Include
    private DefectStatus status = DefectStatus.OPEN;

    @NotBlank
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "technical_notes", columnDefinition = "text")
    private String technicalNotes;

    @NotNull
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_notes", columnDefinition = "text")
    private String resolutionNotes;

    @Column(name = "discard_reason", columnDefinition = "text")
    private String discardReason;

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

    @Size(max = 120)
    @Column(name = "correction_type", length = 120)
    private String correctionType;

    @Column(name = "parts_replaced", columnDefinition = "text")
    private String partsReplaced;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_in_shift_id", foreignKey = @ForeignKey(name = "fk_catenary_defect_resolved_in_shift"))
    private MaintenanceShift resolvedInShift;

    @Column(name = "repair_planned_date")
    private LocalDate repairPlannedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "found_in_task_id", foreignKey = @ForeignKey(name = "fk_catenary_defect_found_in_task"))
    private MaintenanceTask foundInTask;

    @Convert(converter = PhotoReferencesConverter.class)
    @Column(name = "photo_refs", columnDefinition = "text")
    @Builder.Default
    private List<String> photoRefs = new ArrayList<>();

    /** Copia la localizacion del activo. */
    public void locateAt(CatenaryAsset target) {
        this.executionPackageId = target.getExecutionPackageId();
        this.trackId = target.getTrackId();
        this.stationId = target.getStationId();
        this.startKp = target.getStartKp();
        this.endKp = target.getEndKp();
    }
}
