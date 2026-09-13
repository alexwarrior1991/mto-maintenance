package com.alejandro.mtomaintenance.application.dto.shift;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ShiftReportRowResponse(
        int number,
        UUID taskId,
        String orderCode,
        Long executionPackageId,
        Long trackId,
        String profileCode,
        String profileName,
        BigDecimal kp,
        String sectioning,
        List<String> taskTypeCodes,
        String worksPerformed,
        String defectsFound,
        List<String> materials,
        Instant startedAt,
        Instant completedAt,
        MaintenanceTaskStatus status,
        boolean workComplete,
        LocalDate repairPlannedDate,
        List<String> photoRefs
) {
}
