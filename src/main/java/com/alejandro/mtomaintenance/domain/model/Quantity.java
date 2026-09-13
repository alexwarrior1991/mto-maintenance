package com.alejandro.mtomaintenance.domain.model;

import java.math.BigDecimal;

/** Cantidad de material con su unidad; nunca negativa. */
public record Quantity(BigDecimal amount, String unit) {

    public Quantity {
        DomainValidations.requireNonNull(amount, "amount");
        DomainValidations.require(amount.signum() >= 0, "amount must not be negative");
        DomainValidations.require(unit != null && !unit.isBlank(), "unit is required");
    }

    public boolean exceeds(Quantity other) {
        return amount.compareTo(other.amount) > 0;
    }
}
