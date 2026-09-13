package com.alejandro.mtomaintenance.application.dto.task;

import jakarta.validation.constraints.NotBlank;

public record CancelTaskRequest(@NotBlank String reason) {
}
