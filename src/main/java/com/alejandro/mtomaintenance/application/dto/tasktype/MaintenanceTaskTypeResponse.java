package com.alejandro.mtomaintenance.application.dto.tasktype;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FunctionalGroup;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TaskUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record MaintenanceTaskTypeResponse(
        UUID id,
        String code,
        String description,
        FunctionalGroup functionalGroup,
        BigDecimal standardMinutesPerUnit,
        TaskUnit unit,
        BigDecimal fixedMinutes,
        Boolean requiresFullPossession,
        Boolean diagnostic,
        Boolean active,
        Integer orderIndex
) {
}
