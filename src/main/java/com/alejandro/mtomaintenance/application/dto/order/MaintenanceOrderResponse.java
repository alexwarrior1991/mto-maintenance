package com.alejandro.mtomaintenance.application.dto.order;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamSummaryResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceOrderResponse(
        UUID id,
        String code,
        String title,
        String description,
        MaintenanceOrderType type,
        MaintenanceOrderStatus status,
        MaintenancePriority priority,
        CatenaryAssetSummaryResponse asset,
        Long executionPackageId,
        Long trackId,
        Long stationId,
        BigDecimal startKp,
        BigDecimal endKp,
        LocalDate plannedDate,
        Instant actualStartDate,
        Instant actualEndDate,
        MaintenanceTeamSummaryResponse team,
        String assignedUser,
        String closingNotes,
        String cancellationReason,
        UUID originInspectionId,
        UUID originDefectId,
        UUID stockProjectId,
        int taskCount,
        int completedTaskCount,
        BigDecimal estimatedMinutes,
        int estimatedShifts,
        AuditMetadataResponse audit
) {
}
