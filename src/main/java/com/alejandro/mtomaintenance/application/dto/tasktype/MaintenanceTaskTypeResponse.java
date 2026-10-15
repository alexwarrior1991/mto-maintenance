package com.alejandro.mtomaintenance.application.dto.tasktype;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FunctionalGroup;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TaskUnit;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Un tipo de tarea del catalogo del plan OCS, con su tiempo estandar.
 *
 * <p>Ejemplo, {@code GET /task-types/{code}}:</p>
 * <pre>{@code
 * {"id": "…", "code": "RG-01", "description": "Insulators (cantilever and anchorage zones)",
 *  "functionalGroup": "STRUCTURAL_SUPPORTS", "standardMinutesPerUnit": 5.00, "unit": "UNIT", "fixedMinutes": 0.00,
 *  "requiresFullPossession": false, "diagnostic": false, "active": true, "orderIndex": 1}
 * }</pre>
 */
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
