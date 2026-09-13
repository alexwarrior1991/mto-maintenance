package com.alejandro.mtomaintenance.application.dto.shift;

import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamSummaryResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MaintenanceShiftResponse(
        UUID id,
        String code,
        LocalDate shiftDate,
        MaintenanceTeamSummaryResponse team,
        String baseName,
        String vehicle,
        PossessionType possessionType,
        Instant plannedStart,
        Instant plannedEnd,
        Instant actualStart,
        Instant actualEnd,
        Instant voltageCutoffAt,
        Integer netWorkMinutes,
        UUID blockADisconnectorId,
        String blockADisconnectorCode,
        UUID blockBDisconnectorId,
        String blockBDisconnectorCode,
        String earthingPoints,
        String parkingPlace,
        Long executionPackageId,
        List<Long> trackIds,
        BigDecimal startKp,
        BigDecimal endKp,
        String personnel,
        String measurementEquipment,
        ShiftStatus status,
        String observations,
        AuditMetadataResponse audit
) {
}
