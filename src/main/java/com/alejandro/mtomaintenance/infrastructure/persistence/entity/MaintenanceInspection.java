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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Inspeccion visual o tecnica de un activo, con sus puntos medidos. */
@Audited
@Entity
@Table(
        name = "maintenance_inspection",
        uniqueConstraints = @UniqueConstraint(name = "uq_maintenance_inspection_code", columnNames = "code"),
        indexes = {
                @Index(name = "idx_maintenance_inspection_result_date", columnList = "result, inspection_date"),
                @Index(name = "idx_maintenance_inspection_asset", columnList = "asset_id"),
                @Index(name = "idx_maintenance_inspection_track", columnList = "track_id"),
                @Index(name = "idx_maintenance_inspection_origin_order", columnList = "origin_order_id")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class MaintenanceInspection extends AuditableEntity {

    @NotBlank
    @Size(max = 32)
    @Column(name = "code", nullable = false, length = 32)
    @ToString.Include
    private String code;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false, foreignKey = @ForeignKey(name = "fk_maintenance_inspection_asset"))
    private CatenaryAsset asset;

    @Column(name = "execution_package_id")
    private Long executionPackageId;

    @Column(name = "track_id")
    private Long trackId;

    @Column(name = "station_id")
    private Long stationId;

    @Digits(integer = 9, fraction = 3)
    @Column(name = "kp", precision = 12, scale = 3)
    private BigDecimal kp;

    @NotNull
    @Column(name = "inspection_date", nullable = false)
    @ToString.Include
    private LocalDate inspectionDate;

    @Size(max = 100)
    @Column(name = "inspector", length = 100)
    private String inspector;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    @Column(name = "inspection_kind", nullable = false, columnDefinition = "inspection_kind")
    private InspectionKind inspectionKind = InspectionKind.VISUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", foreignKey = @ForeignKey(name = "fk_maintenance_inspection_template"))
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private InspectionTemplate template;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "result", nullable = false, columnDefinition = "inspection_result")
    @ToString.Include
    private InspectionResult result;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "detected_defects", columnDefinition = "text")
    private String detectedDefects;

    @Column(name = "recommended_actions", columnDefinition = "text")
    private String recommendedActions;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_defect_id", foreignKey = @ForeignKey(name = "fk_maintenance_inspection_generated_defect"))
    private CatenaryDefect generatedDefect;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_order_id", foreignKey = @ForeignKey(name = "fk_maintenance_inspection_generated_order"))
    private MaintenanceOrder generatedOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_order_id", foreignKey = @ForeignKey(name = "fk_maintenance_inspection_origin_order"))
    private MaintenanceOrder originOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", foreignKey = @ForeignKey(name = "fk_maintenance_inspection_shift"))
    private MaintenanceShift shift;

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex asc")
    @Builder.Default
    private List<MaintenanceInspectionItem> items = new ArrayList<>();

    public boolean isSevere() {
        return InspectionResult.MAJOR_DEFECT == result || InspectionResult.UNSAFE == result;
    }
}
