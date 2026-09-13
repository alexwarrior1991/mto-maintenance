package com.alejandro.mtomaintenance.application.dto.shift;

import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/** Sin actualEnd se toma el instante actual; sin netWorkMinutes se calcula desde el corte de tension (o el inicio). */
public record CloseShiftRequest(Instant actualEnd, Instant voltageCutoffAt, @PositiveOrZero Integer netWorkMinutes, String observations) {
}
