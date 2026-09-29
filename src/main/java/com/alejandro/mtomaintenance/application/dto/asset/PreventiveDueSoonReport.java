package com.alejandro.mtomaintenance.application.dto.asset;

import java.time.LocalDate;

/**
 * What the daily preventive check found and whether it published its event.
 *
 * @param date         the day of the check (UTC), the identity of the event
 * @param horizonDays  how far ahead it looked
 * @param dueCount     assets whose preventive is due within the horizon, the overdue ones included
 * @param overdueCount of those, the ones already past their due date or never checked
 * @param published    {@code false} when another instance held the lock or there was nothing due
 */
public record PreventiveDueSoonReport(LocalDate date, int horizonDays, int dueCount, int overdueCount, boolean published) {
}
