package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Punto a analizar o ajustar, copiado de una plantilla: la misma forma para los items de una
 * inspeccion y para los de una tarea de preventivo. Dos tablas, una forma: evita el FK polimorfico
 * sin duplicar la logica de medidas.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AbstractChecklistItem extends AuditableEntity {

    @NotBlank
    @Size(max = 32)
    @Column(name = "code", nullable = false, length = 32)
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
    @Column(name = "requires_measure", nullable = false)
    private Boolean requiresMeasure = false;

    @Column(name = "measured_value", precision = 12, scale = 3)
    private BigDecimal measuredValue;

    /** El punto se ajusto durante la intervencion (el "ajuste de varios puntos" del preventivo). */
    @NotNull
    @Column(name = "adjusted", nullable = false)
    private Boolean adjusted = false;

    @Column(name = "value_after_adjustment", precision = 12, scale = 3)
    private BigDecimal valueAfterAdjustment;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "item_result", columnDefinition = "check_item_result")
    private CheckItemResult itemResult;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @NotNull
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;

    protected AbstractChecklistItem() {
    }

    /** Copia los datos de plantilla; la medida y el resultado se rellenan despues. */
    protected void copyFrom(InspectionTemplateItem item) {
        this.code = item.getCode();
        this.label = item.getLabel();
        this.unit = item.getUnit();
        this.minValue = item.getMinValue();
        this.maxValue = item.getMaxValue();
        this.requiresMeasure = item.getRequiresMeasure();
        this.orderIndex = item.getOrderIndex();
    }

    /** Valor efectivo: el ajustado si lo hay, si no el medido. */
    public BigDecimal effectiveValue() {
        return valueAfterAdjustment != null ? valueAfterAdjustment : measuredValue;
    }

    /** Verdadero si hay un valor efectivo fuera de [min, max]. Sin medida no se puede decir nada. */
    public boolean isOutOfRange() {
        BigDecimal value = effectiveValue();
        if (value == null) {
            return false;
        }
        return (minValue != null && value.compareTo(minValue) < 0)
                || (maxValue != null && value.compareTo(maxValue) > 0);
    }
}
