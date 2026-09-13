package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

import java.math.BigDecimal;

/** Punto de una plantilla. Con min/max es de medida; sin ellos es de estado (OK / DEFECT). */
@Entity
@Table(name = "inspection_template_item", uniqueConstraints = @UniqueConstraint(name = "uq_inspection_template_item_code", columnNames = {"template_id", "code"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class InspectionTemplateItem extends AuditableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, foreignKey = @ForeignKey(name = "fk_inspection_template_item_template"))
    private InspectionTemplate template;

    @NotBlank
    @Size(max = 32)
    @Column(name = "code", nullable = false, length = 32)
    @ToString.Include
    private String code;

    @NotBlank
    @Size(max = 255)
    @Column(name = "label", nullable = false)
    private String label;

    @Size(max = 32)
    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "min_value", precision = 12, scale = 3)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 12, scale = 3)
    private BigDecimal maxValue;

    @NotNull
    @Builder.Default
    @Column(name = "requires_measure", nullable = false)
    private Boolean requiresMeasure = false;

    @NotNull
    @Builder.Default
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;
}
