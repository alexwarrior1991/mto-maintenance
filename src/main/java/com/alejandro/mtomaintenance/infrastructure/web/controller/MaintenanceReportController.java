package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.export.ReportFormat;
import com.alejandro.mtomaintenance.application.dto.export.ReportMediaTypes;
import com.alejandro.mtomaintenance.application.dto.report.MonthlyReportResponse;
import com.alejandro.mtomaintenance.application.dto.report.ProgressReportResponse;
import com.alejandro.mtomaintenance.application.service.MaintenanceReportService;
import com.alejandro.mtomaintenance.application.service.ReportExportService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
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
    private final ReportExportService reportExports;

    public MaintenanceReportController(MaintenanceReportService reportService, ReportExportService reportExports) {
        this.reportService = reportService;
        this.reportExports = reportExports;
    }

    @Operation(summary = "Progress", description = "Assets checked over total per execution package, track and asset type, with kilometres covered (the advance workbook).")
    @ApiResponse(responseCode = "200", description = "The report as JSON, as a workbook or as a printable PDF", content = {
            @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProgressReportResponse.class)),
            @Content(mediaType = ReportMediaTypes.XLSX, schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = ReportMediaTypes.PDF, schema = @Schema(type = "string", format = "binary"))})
    @GetMapping("/progress")
    public ResponseEntity<?> progress(
            @RequestParam(required = false) Long executionPackageId,
            @RequestParam(required = false) Long trackId,
            @RequestParam(required = false) CatenaryAssetType assetType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = ReportDownloads.FORMAT) @RequestParam(required = false) String format) {
        ProgressReportResponse report = reportService.progress(executionPackageId, trackId, assetType, from, to);
        ReportFormat requested = ReportFormat.of(format);
        if (requested.isJson()) {
            return ResponseEntity.ok(report);
        }
        return ReportDownloads.of(reportExports.exportProgressReport(report, requested));
    }

    @Operation(summary = "Monthly report", description = "Shifts, net time, orders and tasks completed, profiles checked, kilometres, defects and materials of a month.")
    @ApiResponse(responseCode = "200", description = "The report as JSON, as a workbook or as a printable PDF", content = {
            @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = MonthlyReportResponse.class)),
            @Content(mediaType = ReportMediaTypes.XLSX, schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = ReportMediaTypes.PDF, schema = @Schema(type = "string", format = "binary"))})
    @GetMapping("/monthly")
    public ResponseEntity<?> monthly(
            @Parameter(description = "Month as YYYY-MM", example = "2026-01") @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Long executionPackageId,
            @Parameter(description = ReportDownloads.FORMAT) @RequestParam(required = false) String format) {
        MonthlyReportResponse report = reportService.monthly(month, executionPackageId);
        ReportFormat requested = ReportFormat.of(format);
        if (requested.isJson()) {
            return ResponseEntity.ok(report);
        }
        return ReportDownloads.of(reportExports.exportMonthlyReport(report, requested));
    }
}
