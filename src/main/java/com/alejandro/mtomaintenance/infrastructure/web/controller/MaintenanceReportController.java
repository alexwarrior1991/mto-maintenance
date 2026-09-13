package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.report.MonthlyReportResponse;
import com.alejandro.mtomaintenance.application.dto.report.ProgressReportResponse;
import com.alejandro.mtomaintenance.application.service.MaintenanceReportService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.YearMonth;

@RestController
@RequestMapping(MaintenanceApiPaths.BASE + "/reports")
@Tag(name = "Reports")
public class MaintenanceReportController {

    private final MaintenanceReportService reportService;

    public MaintenanceReportController(MaintenanceReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Progress", description = "Assets checked over total per execution package, track and asset type, with kilometres covered (the advance workbook).")
    @GetMapping("/progress")
    public ResponseEntity<ProgressReportResponse> progress(
            @RequestParam(required = false) Long executionPackageId,
            @RequestParam(required = false) Long trackId,
            @RequestParam(required = false) CatenaryAssetType assetType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(reportService.progress(executionPackageId, trackId, assetType, from, to));
    }

    @Operation(summary = "Monthly report", description = "Shifts, net time, orders and tasks completed, profiles checked, kilometres, defects and materials of a month.")
    @GetMapping("/monthly")
    public ResponseEntity<MonthlyReportResponse> monthly(
            @Parameter(description = "Month as YYYY-MM", example = "2026-01") @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Long executionPackageId) {
        return ResponseEntity.ok(reportService.monthly(month, executionPackageId));
    }
}
