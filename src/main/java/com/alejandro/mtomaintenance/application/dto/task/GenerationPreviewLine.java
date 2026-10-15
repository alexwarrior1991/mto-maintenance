package com.alejandro.mtomaintenance.application.dto.task;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Un activo de la previsualizacion ({@code dryRun}), en el orden en que se generaria.
 *
 * <p>Ejemplo, dentro de {@code GeneratePreventiveTasksResponse.assets}:</p>
 * <pre>{@code
 * {"assetId": "…(PRF-4321)", "code": "PRF-4321", "name": "13-2.01", "type": "PROFILE", "trackId": 2, "connectedTrackId": null,
 *  "kp": 13007.290, "taskTypeCodes": ["RG-01", "RG-05", "RG-08", "RG-10", "RG-11", "RG-12", "RG-13", "RP-08"],
 *  "withChecklist": true, "outcome": "CREATE", "otherOrderCode": null}
 * }</pre>
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
