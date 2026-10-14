package com.alejandro.mtomaintenance.application.dto.task;

import java.math.BigDecimal;

/**
 * @param skippedProfiles los perfiles que ya tenian tarea en la orden
 * @param skippedAssets   todos los activos que ya tenian tarea, perfiles y aisladores: con solo
 *                        perfiles es lo mismo que {@code skippedProfiles}
 */
public record GeneratePreventiveTasksResponse(
        int createdTasks,
        int skippedProfiles,
        int totalTasks,
        BigDecimal estimatedMinutes,
        int estimatedShifts,
        int skippedAssets
) {
}
