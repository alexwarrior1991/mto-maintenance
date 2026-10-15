package com.alejandro.mtomaintenance.application.dto.task;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cierre de una tarea dentro de un turno. workComplete=false deja el defecto en linea OPEN con
 * repairPlannedDate; workComplete=true lo crea ya RESOLVED en este turno.
 *
 * <p>Ejemplo, {@code POST /orders/{id}/tasks/{taskId}/complete}:</p>
 * <pre>{@code
 * {"shiftId": "…(SH-000001)", "taskTypeCodes": ["RG-01", "RG-12"], "notes": "Sustituida una pendola", "workComplete": true,
 *  "inlineDefects": [{"severity": "LOW", "description": "Pendola danada en 13-2.01", "correctionType": "Sustitucion"}],
 *  "materials": [{"materialCode": "DROPPER-STD", "warehouseId": "…(Rishpon)", "quantity": 1, "unit": "UNIT"}],
 *  "photoRefs": ["…"]}
 * }</pre>
 */
public record CompleteTaskRequest(
        @NotNull UUID shiftId,
        List<@NotNull String> taskTypeCodes,
        String notes,
        String defectsFound,
        Boolean workComplete,
        LocalDate repairPlannedDate,
        @Valid List<@NotNull InlineDefectRequest> inlineDefects,
        @Valid List<@NotNull TaskMaterialRequest> materials,
        List<@NotNull String> photoRefs
) {
    public boolean isWorkComplete() {
        return workComplete == null || workComplete;
    }
}
