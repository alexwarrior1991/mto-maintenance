package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.report.MonthlyReportResponse;
import com.alejandro.mtomaintenance.application.dto.report.ProgressReportResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;

import java.time.Instant;
import java.time.YearMonth;

/** Informes derivados por consulta, sin tablas propias: avance y resumen mensual. */
public interface MaintenanceReportService {

    ProgressReportResponse progress(Long executionPackageId, Long trackId, CatenaryAssetType assetType, Instant from, Instant to);

    MonthlyReportResponse monthly(YearMonth month, Long executionPackageId);
}
