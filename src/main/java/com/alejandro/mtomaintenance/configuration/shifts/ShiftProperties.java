package com.alejandro.mtomaintenance.configuration.shifts;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Los turnos ({@code app.shifts.*}).
 *
 * <p>{@code minimumRest} es lo que descansa como poco una persona entre dos noches seguidas: del fin de
 * un turno (su fin real, o el previsto) al inicio del siguiente en el que va como jefe o como lineman
 * ({@code ShiftAvailability.restViolation}). Doce horas por defecto; cero lo apaga. Un turno sin horas no
 * se puede medir y no se rechaza.</p>
 *
 * @param minimumRest el descanso minimo entre dos noches ({@code PT12H} por defecto)
 */
@ConfigurationProperties(prefix = "app.shifts")
public record ShiftProperties(Duration minimumRest) {

    public static final Duration DEFAULT_MINIMUM_REST = Duration.ofHours(12);

    public ShiftProperties {
        minimumRest = minimumRest == null ? DEFAULT_MINIMUM_REST : minimumRest;
        if (minimumRest.isNegative()) {
            throw new IllegalArgumentException("app.shifts.minimum-rest cannot be negative: " + minimumRest);
        }
    }
}
