package com.alejandro.mtomaintenance.application.dto.task;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.CheckItemResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Una tarea de la orden, con su activo, su turno y su checklist.
 *
 * <p>Ejemplo, {@code GET /orders/{id}/tasks/{taskId}}:</p>
 * <pre>{@code
 * {"id": "…", "orderId": "…(MO-000001)", "sequence": 3,
 *  "description": "Preventive maintenance of profile 13-2.01 (kp 13007.290)", "status": "COMPLETED",
 *  "asset": {"code": "PRF-4321", "name": "13-2.01", "type": "PROFILE", "trackId": 2, "startKp": 13007.290, …},
 *  "shiftId": "…(SH-000001)", "completedAt": "2026-01-27T22:15:00Z", "taskTypeCodes": ["RG-01", "RG-12"], "version": 3, …}
 * }</pre>
 */
public record MaintenanceTaskResponse(
        UUID id,
        UUID orderId,
        Integer sequence,
        String description,
        MaintenanceTaskStatus status,
        String assignedUser,
        CatenaryAssetSummaryResponse asset,
        UUID shiftId,
        Instant startedAt,
        Instant completedAt,
        String defectsFound,
        String notes,
        List<String> photoRefs,
        List<String> taskTypeCodes,
        List<CheckItemResponse> checkItems,
        Long version,
        AuditMetadataResponse audit
) {
}
