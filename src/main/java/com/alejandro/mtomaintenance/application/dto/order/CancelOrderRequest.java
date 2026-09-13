package com.alejandro.mtomaintenance.application.dto.order;

import jakarta.validation.constraints.NotBlank;

public record CancelOrderRequest(@NotBlank String reason) {
}
