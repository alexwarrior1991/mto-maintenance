package com.alejandro.mtomaintenance.application.dto.shift;

import java.util.List;

/**
 * Informe diario del turno: cabecera + una fila por tarea trabajada (un perfil, un aislador...) y
 * las inspecciones registradas en el.
 *
 * @param inspectionsRecorded cuantas inspecciones se registraron en el turno; lo cuenta el servicio
 *                            para que ningun cliente tenga que sumar
 * @param inspections         las inspecciones del turno, por fecha y codigo
 */
public record ShiftReportResponse(
        MaintenanceShiftResponse shift,
        int tasksCompleted,
        int tasksPending,
        int profilesReviewed,
        int defectsFound,
        int defectsResolved,
        List<ShiftReportRowResponse> rows,
        int inspectionsRecorded,
        List<ShiftReportInspectionResponse> inspections
) {

    public ShiftReportResponse {
        rows = rows == null ? List.of() : List.copyOf(rows);
        inspections = inspections == null ? List.of() : List.copyOf(inspections);
    }
}
