package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

/**
 * Un periodo en el que una persona o un vehiculo de mto-resources no esta disponible ({@code V24}), tal
 * como llega en {@code person.*.unavailable} y {@code vehicle.*.unavailable}: desde {@code from} hasta
 * {@code to}, los dos incluidos; {@code to} nulo es que no se sabe cuando acaba (una reparacion abierta).
 * Sin el motivo: una baja medica no sale de mto-resources.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnavailablePeriod(LocalDate from, LocalDate to) {

    /** Si ese dia cae dentro; sin {@code from} no hay periodo. */
    public boolean covers(LocalDate date) {
        return from != null && date != null && !date.isBefore(from) && (to == null || !date.isAfter(to));
    }
}
