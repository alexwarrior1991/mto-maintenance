package com.alejandro.mtomaintenance.domain.model;

import java.util.Optional;

/**
 * La composicion de un equipo que trabaja en un vehiculo: un jefe de equipo (foreman), siempre, y de 2 a
 * 6 linemen; de 3 a 7 personas. Es la misma regla en el organigrama de {@code mto-resources} y en la
 * dotacion de un turno de {@code mto-maintenance}: esta es la copia de mantenimiento, que no se aparta
 * de la de {@code mto-resources}.
 *
 * <p>Devuelve el motivo en vez de lanzar: quien llama decide si es un 400 (una peticion mal formada) o un
 * 409 (un cambio que dejaria al equipo fuera de la regla).</p>
 */
public final class CrewComposition {

    public static final int MIN_LINEMEN = 2;
    public static final int MAX_LINEMEN = 6;

    private CrewComposition() {
    }

    /** Un equipo completo: con jefe y con 2 a 6 linemen. */
    public static Optional<String> completeViolation(String team, boolean hasLeader, int linemen) {
        if (!hasLeader) {
            return Optional.of(team + " has no team leader: a team always works with one");
        }
        if (linemen < MIN_LINEMEN) {
            return Optional.of(team + " has " + linemen + " linemen; a team needs at least " + MIN_LINEMEN);
        }
        return limitViolation(team, linemen);
    }

    /** Como mucho 6 linemen, lo que vale tambien para un equipo todavia incompleto. */
    public static Optional<String> limitViolation(String team, int linemen) {
        if (linemen > MAX_LINEMEN) {
            return Optional.of(team + " has " + linemen + " linemen; a team has at most " + MAX_LINEMEN);
        }
        return Optional.empty();
    }

    /** Anadir uno a un equipo que ya tiene {@code linemen}. */
    public static Optional<String> addViolation(String team, int linemen) {
        if (linemen >= MAX_LINEMEN) {
            return Optional.of(team + " already has " + MAX_LINEMEN + " linemen, the most a team can have; take one out first");
        }
        return Optional.empty();
    }

    /** Quitar uno a un equipo que tiene {@code linemen}. */
    public static Optional<String> removeViolation(String team, int linemen) {
        if (linemen <= MIN_LINEMEN) {
            return Optional.of(team + " has " + MIN_LINEMEN + " linemen, the fewest a team can work with; add another one first "
                    + "or replace the list in one go");
        }
        return Optional.empty();
    }

    /** Las personas que van en el vehiculo: el jefe y sus linemen. */
    public static int crewSize(boolean hasLeader, int linemen) {
        return (hasLeader ? 1 : 0) + linemen;
    }
}
