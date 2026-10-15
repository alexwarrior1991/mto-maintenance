package com.alejandro.mtomaintenance.application.dto.asset;

import java.time.LocalDate;

/**
 * What the daily preventive check found and whether it published its event.
 *
 * <p>Ejemplo, lo que devuelve {@code PreventiveDueSoonServiceImpl.publishDueSoon()} al planificador diario de
 * {@code PreventiveDueSoonConfiguration}; no sale por la API, y con {@code published} el evento
 * {@code preventive.due-soon} ya esta en el outbox:</p>
 * <pre>{@code
 * PreventiveDueSoonReport[date=2026-01-27, horizonDays=7, dueCount=12, overdueCount=2, published=true]
 * }</pre>
 *
 * @param date         the day of the check (UTC), the identity of the event
 * @param horizonDays  how far ahead it looked
 * @param dueCount     assets whose preventive is due within the horizon, the overdue ones included
 * @param overdueCount of those, the ones already past their due date or never checked
 * @param published    {@code false} when another instance held the lock or there was nothing due
 */
public record PreventiveDueSoonReport(LocalDate date, int horizonDays, int dueCount, int overdueCount, boolean published) {
}
