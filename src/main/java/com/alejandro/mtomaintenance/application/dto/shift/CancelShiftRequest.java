package com.alejandro.mtomaintenance.application.dto.shift;

import jakarta.validation.constraints.NotBlank;

public record CancelShiftRequest(@NotBlank String reason) {
}
