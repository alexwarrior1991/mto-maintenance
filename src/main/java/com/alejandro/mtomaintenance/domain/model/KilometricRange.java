package com.alejandro.mtomaintenance.domain.model;

import java.math.BigDecimal;

/** Rango de puntos kilometricos en metros; el fin no puede quedar antes del inicio. */
public record KilometricRange(BigDecimal startKp, BigDecimal endKp) {

    public KilometricRange {
        DomainValidations.requireNonNull(startKp, "startKp");
        DomainValidations.requireNonNull(endKp, "endKp");
        DomainValidations.require(startKp.compareTo(endKp) <= 0, "startKp must not be after endKp");
    }

    public boolean contains(BigDecimal kp) {
        return kp != null && kp.compareTo(startKp) >= 0 && kp.compareTo(endKp) <= 0;
    }

    /** Longitud en kilometros (los kp se guardan en metros). */
    public BigDecimal lengthKm() {
        return endKp.subtract(startKp).movePointLeft(3);
    }
}
