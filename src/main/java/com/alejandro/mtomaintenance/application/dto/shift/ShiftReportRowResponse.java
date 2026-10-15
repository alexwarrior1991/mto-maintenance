package com.alejandro.mtomaintenance.application.dto.shift;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Una fila del parte diario del turno: una tarea trabajada (un perfil, un aislador...).
 *
 * <p>Ejemplo, un elemento de {@code ShiftReportResponse.rows} (la fila de un aislador trae {@code "switches": ["W33 1:9"]}):</p>
 * <pre>{@code
 * {"number": 3, "orderCode": "MO-000001", "profileCode": "PRF-4321", "profileName": "13-2.01", "kp": 13007.290,
 *  "sectioning": "A/S", "switches": [], "taskTypeCodes": ["RG-01", "RG-11", "RG-12"],
 *  "materials": ["1.000000 UNIT DROPPER-STD"], "status": "COMPLETED", "workComplete": true, …}
 * }</pre>
 */
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
        /** Agujas del aislador de seccion, con su tangente: {@code W31 1:9}. Vacia en el resto. */
        List<String> switches,
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
