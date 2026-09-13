package com.alejandro.mtomaintenance.application.dto.shift;

import java.util.List;

/** Informe diario del turno: cabecera + una fila por tarea (perfil) trabajada. */
public record ShiftReportResponse(
        MaintenanceShiftResponse shift,
        int tasksCompleted,
        int tasksPending,
        int profilesReviewed,
        int defectsFound,
        int defectsResolved,
        List<ShiftReportRowResponse> rows
) {
}
