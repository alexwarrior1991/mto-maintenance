package com.alejandro.mtomaintenance.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Carga de trabajo estimada a partir de los tiempos estandar del plan OCS. El tiempo efectivo por
 * turno (4,5 h) sale de la seccion 4 del plan: la ventana contractual es de 8 horas, pero entre
 * permisos, desplazamiento de la maquina, briefing y tierras quedan de 4 a 5 horas reales.
 */
public record WorkloadEstimate(BigDecimal estimatedMinutes, BigDecimal effectiveMinutesPerShift) {

    public static final BigDecimal DEFAULT_EFFECTIVE_MINUTES_PER_SHIFT = BigDecimal.valueOf(270);

    public WorkloadEstimate {
        DomainValidations.requireNonNull(estimatedMinutes, "estimatedMinutes");
        DomainValidations.require(estimatedMinutes.signum() >= 0, "estimatedMinutes must not be negative");
        DomainValidations.requireNonNull(effectiveMinutesPerShift, "effectiveMinutesPerShift");
        DomainValidations.require(effectiveMinutesPerShift.signum() > 0, "effectiveMinutesPerShift must be positive");
    }

    public static WorkloadEstimate ofMinutes(BigDecimal minutes) {
        return new WorkloadEstimate(minutes, DEFAULT_EFFECTIVE_MINUTES_PER_SHIFT);
    }

    /** Turnos necesarios, redondeando hacia arriba: medio turno sigue ocupando una noche. */
    public int estimatedShifts() {
        if (estimatedMinutes.signum() == 0) {
            return 0;
        }
        return estimatedMinutes.divide(effectiveMinutesPerShift, 0, RoundingMode.CEILING).intValueExact();
    }
}
