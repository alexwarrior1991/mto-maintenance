package com.alejandro.mtomaintenance.application.dto.task;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.CheckItemResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        AuditMetadataResponse audit
) {
}
