package com.alejandro.mtomaintenance.application.dto.shift;

import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/**
 * Sin actualEnd se toma el instante actual; sin netWorkMinutes se calcula desde el corte de tension (o el inicio).
 *
 * <p>Ejemplo, {@code POST /shifts/{id}/close}:</p>
 * <pre>{@code
 * {"actualEnd": "2026-01-28T02:30:00Z", "voltageCutoffAt": "2026-01-27T21:40:00Z", "netWorkMinutes": 290,
 *  "observations": "Corte de tension retrasado por trafico"}
 * }</pre>
 */
public record CloseShiftRequest(Instant actualEnd, Instant voltageCutoffAt, @PositiveOrZero Integer netWorkMinutes, String observations) {
}
