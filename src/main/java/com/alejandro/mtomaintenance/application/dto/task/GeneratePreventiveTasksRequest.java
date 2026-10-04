package com.alejandro.mtomaintenance.application.dto.task;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Sin codigos se usan los de los grupos 1, 2 y 4 (lo que se hace en cada perfil de via principal). */
public record GeneratePreventiveTasksRequest(List<@NotNull String> taskTypeCodes, Boolean withChecklist) {
}
