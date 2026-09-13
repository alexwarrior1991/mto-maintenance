package com.alejandro.mtomaintenance.application.dto.shift;

import java.time.Instant;

/** Sin actualStart se toma el instante actual. */
public record StartShiftRequest(Instant actualStart, Instant voltageCutoffAt) {
}
