package com.alejandro.mtomaintenance.application.dto.report;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Informe mensual: turnos, tiempo neto, ordenes y tareas completadas, kilometros, defectos y materiales de un mes.
 *
 * <p>Ejemplo, {@code GET /reports/monthly?month=2026-01&executionPackageId=6}:</p>
 * <pre>{@code
 * {"month": "2026-01", "executionPackageId": 6, "shiftsPlanned": 4, "shiftsClosed": 3, "shiftsCancelled": 1,
 *  "netWorkMinutes": 540, "averageNetMinutesPerShift": 180.0, "profilesChecked": 21, "coveredKm": 1.230,
 *  "defectsDetected": 2, "defectsResolved": 1, "correctiveOrdersCreated": 1, …,
 *  "materials": [{"materialCode": "DROPPER-STD", "unit": "UNIT", "consumedQuantity": 1.000000, …}]}
 * }</pre>
 */
public record MonthlyReportResponse(
        YearMonth month,
        Long executionPackageId,
        int shiftsPlanned,
        int shiftsClosed,
        int shiftsCancelled,
        int netWorkMinutes,
        BigDecimal averageNetMinutesPerShift,
        int ordersCompleted,
        int tasksCompleted,
        long profilesChecked,
        BigDecimal coveredKm,
        int defectsDetected,
        int defectsResolved,
        int correctiveOrdersCreated,
        List<MonthlyMaterialLineResponse> materials
) {
}
