package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
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
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Tipo de tarea del plan OCS (RG-xx revision general, RP-xx revision particular) con su tiempo
 * estandar: {@code fixedMinutes + standardMinutesPerUnit * unidades}. Sembrado por V3.
 */
@Entity
@Table(name = "maintenance_task_type", uniqueConstraints = @UniqueConstraint(name = "uq_maintenance_task_type_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class MaintenanceTaskType extends AuditableEntity {

    @NotBlank
    @Size(max = 16)
    @Column(name = "code", nullable = false, length = 16)
    @ToString.Include
    private String code;

    @NotBlank
    @Size(max = 255)
    @Column(name = "description", nullable = false)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    @Column(name = "functional_group", nullable = false, columnDefinition = "functional_group")
    private FunctionalGroup functionalGroup = FunctionalGroup.NONE;

    @NotNull
    @PositiveOrZero
    @Builder.Default
    @Column(name = "standard_minutes_per_unit", nullable = false, precision = 8, scale = 2)
    private BigDecimal standardMinutesPerUnit = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    @Column(name = "unit", nullable = false, columnDefinition = "task_unit")
    private TaskUnit unit = TaskUnit.UNIT;

    @NotNull
    @PositiveOrZero
    @Builder.Default
    @Column(name = "fixed_minutes", nullable = false, precision = 8, scale = 2)
    private BigDecimal fixedMinutes = BigDecimal.ZERO;

    /** Solo con posesion total de via (grupos 3 y 5 y RP-12). */
    @NotNull
    @Builder.Default
    @Column(name = "requires_full_possession", nullable = false)
    private Boolean requiresFullPossession = false;

    /** Grupo 6: campanas de medida con su propia planificacion y con checklist de medidas. */
    @NotNull
    @Builder.Default
    @Column(name = "is_diagnostic", nullable = false)
    private Boolean diagnostic = false;

    @NotNull
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @NotNull
    @Builder.Default
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;
}
