package com.alejandro.mtomaintenance.application.dto.task;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Un activo de la previsualizacion ({@code dryRun}), en el orden en que se generaria.
 *
 * @param kp             donde empieza el activo; null si no se sabe
 * @param taskTypeCodes  los tipos que llevaria su tarea; vacio con {@code NO_TASK_TYPES}
 * @param otherOrderCode con {@code IN_OTHER_ORDER}, la orden donde ya tiene trabajo pendiente
 */
public record GenerationPreviewLine(
        UUID assetId,
        String code,
        String name,
        CatenaryAssetType type,
        Long trackId,
        Long connectedTrackId,
        BigDecimal kp,
        List<String> taskTypeCodes,
        boolean withChecklist,
        GenerationOutcome outcome,
        String otherOrderCode
) {
}
