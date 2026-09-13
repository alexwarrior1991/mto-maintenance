package com.alejandro.mtomaintenance.application.dto.order;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PlanOrderRequest(@NotNull LocalDate plannedDate, String comment) {
}
