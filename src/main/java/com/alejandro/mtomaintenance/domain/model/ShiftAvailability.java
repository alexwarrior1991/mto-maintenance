package com.alejandro.mtomaintenance.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Quien no esta disponible no entra en un turno, y quien trabaja dos noches seguidas descansa entre ellas.
 * Cuando no esta disponible cada persona (vacaciones, bajas y demas ausencias) y cada vehiculo (una
 * reparacion que lo inmoviliza) lo decide {@code mto-resources}, que lo publica sin el motivo; aqui solo se
 * mira contra la fecha del turno ({@code shiftDate}), como las caducidades de {@link ShiftQualifications}.
 *
 * <ul>
 *   <li><b>Disponibilidad</b> ({@link #unavailableViolation}): ninguna persona del turno (el jefe, el
 *       supervisor o un lineman) ni ningun vehiculo con un periodo no disponible que cubra la
 *       {@code shiftDate}. Un periodo va de {@code from} a {@code to}, los dos incluidos; {@code to} nulo no
 *       tiene fin.</li>
 *   <li><b>Descanso</b> ({@link #restViolation}): entre el fin de una noche y el inicio de la siguiente de la
 *       misma persona pasa al menos el descanso minimo. Sin una de las dos horas no se puede medir, y no se
 *       rechaza.</li>
 * </ul>
 *
 * <p>Devuelve el motivo en vez de lanzar, como {@link ShiftQualifications}: quien llama lo convierte en un
 * 409. Sin framework.</p>
 */
public final class ShiftAvailability {

    private ShiftAvailability() {
    }

    /** Un periodo no disponible, con las dos fechas incluidas; {@code to} nulo no tiene fin. */
    public record Period(LocalDate from, LocalDate to) {

        public boolean covers(LocalDate date) {
            return from != null && date != null && !date.isBefore(from) && (to == null || !date.isAfter(to));
        }

        @Override
        public String toString() {
            return to == null ? "from " + from : from + " to " + to;
        }
    }

    /** Una persona o un vehiculo del turno, nombrado como saldra en el motivo, con sus periodos. */
    public record Subject(String name, List<Period> periods) {

        public Subject {
            periods = periods == null ? List.of() : List.copyOf(periods);
        }
    }

    /** Una noche de trabajo: el codigo del turno y sus horas, si se saben. */
    public record Night(String code, Instant start, Instant end) {
    }

    /** Lo que no esta disponible a la fecha del turno, todo en un motivo; vacio si no hay nada. */
    public static Optional<String> unavailableViolation(String shift, LocalDate shiftDate, List<Subject> people, List<Subject> vehicles) {
        if (shiftDate == null) {
            return Optional.empty();
        }
        List<String> found = new ArrayList<>();
        collect(found, people, shiftDate, "");
        collect(found, vehicles, shiftDate, "vehicle ");
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(shift + " on " + shiftDate + " cannot take who or what is not available that day: " + String.join("; ", found)
                + ". It is recorded in mto-resources");
    }

    /**
     * Si entre {@code earlier} y {@code later} (dos noches de la misma persona, en ese orden) descansa menos
     * que {@code minimumRest}; vacio si descansa bastante, si falta una de las dos horas o si el descanso
     * minimo es cero.
     */
    public static Optional<String> restViolation(String person, Duration minimumRest, Night earlier, Night later) {
        if (minimumRest == null || minimumRest.isZero() || minimumRest.isNegative() || earlier.end() == null || later.start() == null) {
            return Optional.empty();
        }
        Duration rest = Duration.between(earlier.end(), later.start());
        if (rest.compareTo(minimumRest) >= 0) {
            return Optional.empty();
        }
        return Optional.of(person + " would rest " + hours(rest) + " between shift " + earlier.code() + " (ends " + earlier.end()
                + ") and shift " + later.code() + " (starts " + later.start() + "): at least " + hours(minimumRest)
                + " between two nights");
    }

    private static void collect(List<String> found, List<Subject> subjects, LocalDate date, String prefix) {
        for (Subject subject : subjects == null ? List.<Subject>of() : subjects) {
            subject.periods().stream().filter(period -> period.covers(date)).findFirst()
                    .ifPresent(period -> found.add(prefix + subject.name() + " (unavailable " + period + ")"));
        }
    }

    private static String hours(Duration duration) {
        long minutes = Math.max(0, duration.toMinutes());
        return minutes % 60 == 0 ? minutes / 60 + "h" : minutes / 60 + "h" + String.format("%02d", minutes % 60);
    }
}
