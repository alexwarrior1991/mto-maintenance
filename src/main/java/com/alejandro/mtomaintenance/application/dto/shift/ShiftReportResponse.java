package com.alejandro.mtomaintenance.application.dto.shift;

import java.util.List;

/**
 * Informe diario del turno: cabecera + una fila por tarea trabajada (un perfil, un aislador...) y
 * las inspecciones registradas en el.
 *
 * <p>Ejemplo, {@code GET /shifts/{id}/report} (JSON, el formato por defecto):</p>
 * <pre>{@code
 * {"shift": {"code": "SH-000001", "status": "CLOSED", …}, "tasksCompleted": 21, "tasksPending": 0, "profilesReviewed": 21,
 *  "defectsFound": 3, "defectsResolved": 2, "rows": [{"number": 1, "orderCode": "MO-000001", …}, …],
 *  "inspectionsRecorded": 1, "inspections": [{"code": "INS-000004", "result": "UNSAFE", …}]}
 * }</pre>
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
