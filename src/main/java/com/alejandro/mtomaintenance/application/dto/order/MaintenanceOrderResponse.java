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

/**
 * Una orden de mantenimiento con su activo, su ubicacion, su equipo y el avance de sus tareas.
 *
 * <p>Ejemplo, {@code GET /orders/{id}}:</p>
 * <pre>{@code
 * {"id": "0195a…", "code": "MO-000001", "title": "Revision preventiva T2 Raanana - Herzliya", "type": "PREVENTIVE",
 *  "status": "COMPLETED", "priority": "MEDIUM", "asset": {"code": "SEC-T2-RAAN-HER", "type": "TRACK_SECTION", …},
 *  "executionPackageId": 6, "trackId": 2, "startKp": 12847.990, "endKp": 14078.090, "plannedDate": "2026-01-27",
 *  "team": {"code": "A", "name": "Team A - Rishpon", …}, "closingNotes": "Perfiles 12-2.27 a 14-2.02 revisados",
 *  "stockProjectId": "…(EP-6)", "taskCount": 23, "completedTaskCount": 23, "version": 7, …}
 * }</pre>
 */
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
        Long version,
        AuditMetadataResponse audit
) {
}
