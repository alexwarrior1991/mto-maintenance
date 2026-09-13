package com.alejandro.mtomaintenance.application.dto.task;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StartTaskRequest(@NotNull UUID shiftId, @Size(max = 100) String assignedUser) {
}
