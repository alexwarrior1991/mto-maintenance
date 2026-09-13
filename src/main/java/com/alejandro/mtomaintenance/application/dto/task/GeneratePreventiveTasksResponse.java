package com.alejandro.mtomaintenance.application.dto.task;

import java.math.BigDecimal;

public record GeneratePreventiveTasksResponse(
        int createdTasks,
        int skippedProfiles,
        int totalTasks,
        BigDecimal estimatedMinutes,
        int estimatedShifts
) {
}
