package com.alejandro.mtomaintenance.application.dto.report;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

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
