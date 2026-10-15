package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Datos de la orden correctiva que nace de una inspeccion; sin cuerpo salen de la propia inspeccion.
 *
 * <p>Ejemplo, {@code POST /inspections/{id}/create-corrective-order} (INS-000004 es UNSAFE: sale URGENT y CRITICAL):</p>
 * <pre>{@code
 * {"title": "Sustituir seccionador HSA-NS5", "plannedDate": "2026-01-29", "teamId": "…(equipo A)"}
 * }</pre>
 */
public record CreateCorrectiveOrderRequest(
        @Size(max = 255) String title,
        String description,
        MaintenancePriority priority,
        LocalDate plannedDate,
        UUID teamId
) {
}
