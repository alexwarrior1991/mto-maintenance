package com.alejandro.mtomaintenance.application.dto.shift;

import java.time.Instant;

/**
 * Sin actualStart se toma el instante actual.
 *
 * <p>Ejemplo, {@code POST /shifts/{id}/start}:</p>
 * <pre>{@code
 * {"actualStart": "2026-01-27T19:10:00Z"}
 * }</pre>
 */
public record StartShiftRequest(Instant actualStart, Instant voltageCutoffAt) {
}
