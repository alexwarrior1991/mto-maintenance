package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageMetadataResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.dto.order.CancelOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksResponse;
import com.alejandro.mtomaintenance.application.service.MaintenanceMaterialUsageService;
import com.alejandro.mtomaintenance.application.service.MaintenanceOrderService;
import com.alejandro.mtomaintenance.application.service.MaintenanceShiftService;
import com.alejandro.mtomaintenance.application.service.MaintenanceTaskService;
import com.alejandro.mtomaintenance.application.service.StatusHistoryService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetRequest;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetResponse;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectRequest;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectResponse;
import com.alejandro.mtomaintenance.application.dto.defect.ResolveDefectRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.CreateCorrectiveOrderRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.CreateDefectFromInspectionRequest;
import com.alejandro.mtomaintenance.application.dto.report.MonthlyReportResponse;
import com.alejandro.mtomaintenance.application.dto.shift.CloseShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.StartShiftRequest;
import com.alejandro.mtomaintenance.application.dto.tasktype.MaintenanceTaskTypeResponse;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamRequest;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamResponse;
import com.alejandro.mtomaintenance.application.dto.export.ExportedReport;
import com.alejandro.mtomaintenance.application.dto.export.ReportFormat;
import com.alejandro.mtomaintenance.application.dto.export.ReportMediaTypes;
import com.alejandro.mtomaintenance.application.dto.report.ProgressReportResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportResponse;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.application.service.CatenaryAssetService;
import com.alejandro.mtomaintenance.application.service.CatenaryDefectService;
import com.alejandro.mtomaintenance.application.service.InspectionTemplateService;
import com.alejandro.mtomaintenance.application.service.MaintenanceInspectionService;
import com.alejandro.mtomaintenance.application.service.MaintenanceReportService;
import com.alejandro.mtomaintenance.application.service.MaintenanceTaskTypeService;
import com.alejandro.mtomaintenance.application.service.MaintenanceTeamService;
import com.alejandro.mtomaintenance.application.service.ReportExportService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import java.time.YearMonth;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestControllerLayerTest {

    @Test
    void controllersExposeVersionedResourceBasePathsAndOpenApiTags() {
        assertController(CatenaryAssetController.class, "/api/v1/maintenance/assets", "Assets");
        assertController(MaintenanceOrderController.class, "/api/v1/maintenance/orders", "Orders");
        assertController(MaintenanceShiftController.class, "/api/v1/maintenance/shifts", "Shifts");
        assertController(MaintenanceTeamController.class, "/api/v1/maintenance/teams", "Teams");
        assertController(MaintenanceTaskTypeController.class, "/api/v1/maintenance/task-types", "Task Types");
        assertController(MaintenanceInspectionController.class, "/api/v1/maintenance/inspections", "Inspections");
        assertController(InspectionTemplateController.class, "/api/v1/maintenance/inspection-templates", "Inspection Templates");
        assertController(CatenaryDefectController.class, "/api/v1/maintenance/defects", "Defects");
        assertController(MaintenanceReportController.class, "/api/v1/maintenance/reports", "Reports");
    }

    @Test
    void orderControllerDelegatesCreationSearchAndTransitions() {
        MaintenanceOrderService orderService = mock(MaintenanceOrderService.class);
        MaintenanceTaskService taskService = mock(MaintenanceTaskService.class);
        MaintenanceOrderController controller = new MaintenanceOrderController(orderService, taskService,
                mock(MaintenanceMaterialUsageService.class), mock(StatusHistoryService.class));
        UUID orderId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        MaintenanceOrderRequest request = new MaintenanceOrderRequest("Preventive T2", null, MaintenanceOrderType.PREVENTIVE, null, assetId, null, null, null, null);
        MaintenanceOrderResponse order = orderResponse(orderId);
        PageResponse<MaintenanceOrderResponse> page = new PageResponse<>(List.of(order), new PageMetadataResponse(0, 20, 1, 1, true, true));
        GeneratePreventiveTasksResponse generated = new GeneratePreventiveTasksResponse(23, 0, 23, new BigDecimal("1266.00"), 5);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        when(orderService.create(request)).thenReturn(order);
        when(orderService.search(MaintenanceOrderStatus.PLANNED, null, MaintenancePriority.HIGH, null, null, 2L, null, null, from, to, null, null, null, null, null, pageable)).thenReturn(page);
        when(orderService.cancel(orderId, new CancelOrderRequest("rain"))).thenReturn(order);
        when(taskService.generatePreventiveTasks(orderId, new GeneratePreventiveTasksRequest(null, null))).thenReturn(generated);

        var createResponse = controller.create(request);
        var searchResponse = controller.search(MaintenanceOrderStatus.PLANNED, null, MaintenancePriority.HIGH, null, null, 2L, null, null, from, to, null, null, null, null, null, pageable);
        var cancelResponse = controller.cancel(orderId, new CancelOrderRequest("rain"));
        var generateResponse = controller.generateTasks(orderId, null);

        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        assertEquals("/api/v1/maintenance/orders/" + orderId, createResponse.getHeaders().getLocation().toString());
        assertSame(page, searchResponse.getBody());
        assertSame(order, cancelResponse.getBody());
        assertSame(generated, generateResponse.getBody());
        verify(orderService).search(MaintenanceOrderStatus.PLANNED, null, MaintenancePriority.HIGH, null, null, 2L, null, null, from, to, null, null, null, null, null, pageable);
    }

    @Test
    void shiftControllerExposesTheProfilesReviewedAndFiltersItsTasksByStatus() {
        MaintenanceShiftService shiftService = mock(MaintenanceShiftService.class);
        MaintenanceTaskService taskService = mock(MaintenanceTaskService.class);
        MaintenanceShiftController controller = new MaintenanceShiftController(shiftService, taskService, mock(ReportExportService.class));
        UUID shiftId = UUID.randomUUID();
        List<CatenaryAssetSummaryResponse> profiles = List.of(new CatenaryAssetSummaryResponse(UUID.randomUUID(), "PRF-1", "12-2.27",
                CatenaryAssetType.PROFILE, 2L, new BigDecimal("12847.990"), new BigDecimal("12847.990"), "A/S", true));
        when(shiftService.profiles(shiftId, null)).thenReturn(profiles);
        when(taskService.findByShift(shiftId, MaintenanceTaskStatus.COMPLETED)).thenReturn(List.of());

        assertSame(profiles, controller.profiles(shiftId, null).getBody());
        assertEquals(List.of(), controller.tasks(shiftId, MaintenanceTaskStatus.COMPLETED).getBody());
        verify(taskService).findByShift(shiftId, MaintenanceTaskStatus.COMPLETED);
    }

    private static void assertController(Class<?> controller, String basePath, String tag) {
        RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
        assertEquals(basePath, mapping.value()[0], controller.getSimpleName());
        assertEquals(tag, controller.getAnnotation(Tag.class).name(), controller.getSimpleName());
    }

    private static MaintenanceOrderResponse orderResponse(UUID id) {
        return new MaintenanceOrderResponse(id, "MO-000001", "Preventive T2", null, MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT,
                MaintenancePriority.MEDIUM, null, 6L, 2L, null, null, null, null, null, null, null, null, null, null, null, null, null,
                0, 0, BigDecimal.ZERO, 0, null);
    }

    @Test
    void defectControllerAnswersCreatedWithALocationAndChecksTheDefectExistsBeforeReadingItsHistory() {
        CatenaryDefectService defectService = mock(CatenaryDefectService.class);
        StatusHistoryService historyService = mock(StatusHistoryService.class);
        CatenaryDefectController controller = new CatenaryDefectController(defectService, historyService);
        UUID defectId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        CatenaryDefectResponse defect = mock(CatenaryDefectResponse.class);
        when(defect.id()).thenReturn(defectId);
        CatenaryDefectRequest request = new CatenaryDefectRequest(UUID.randomUUID(), DefectSeverity.HIGH, "kink", null, null, null, null, null, null, null, null, null, null);
        ResolveDefectRequest resolve = new ResolveDefectRequest("fixed", null, null, null);
        when(defectService.create(request)).thenReturn(defect);
        when(defectService.resolve(defectId, resolve)).thenReturn(defect);
        when(defectService.linkOrder(defectId, orderId)).thenReturn(defect);
        when(defectService.findById(defectId)).thenThrow(new NotFoundException("Catenary defect", defectId));

        var created = controller.create(request);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals("/api/v1/maintenance/defects/" + defectId, created.getHeaders().getLocation().toString());
        assertSame(defect, controller.resolve(defectId, resolve).getBody());
        assertSame(defect, controller.linkOrder(defectId, orderId).getBody());
        assertThrows(NotFoundException.class, () -> controller.history(defectId));
        verify(historyService, never()).findByDefect(any());
    }

    @Test
    void shiftAndInspectionControllersTurnAMissingBodyIntoAnEmptyRequest() {
        MaintenanceShiftService shiftService = mock(MaintenanceShiftService.class);
        MaintenanceShiftController shifts = new MaintenanceShiftController(shiftService, mock(MaintenanceTaskService.class),
                mock(ReportExportService.class));
        MaintenanceInspectionService inspectionService = mock(MaintenanceInspectionService.class);
        MaintenanceInspectionController inspections = new MaintenanceInspectionController(inspectionService);
        MaintenanceOrderService orderService = mock(MaintenanceOrderService.class);
        MaintenanceOrderController orders = new MaintenanceOrderController(orderService, mock(MaintenanceTaskService.class),
                mock(MaintenanceMaterialUsageService.class), mock(StatusHistoryService.class));
        UUID id = UUID.randomUUID();

        shifts.start(id, null);
        shifts.close(id, null);
        inspections.createDefect(id, null);
        inspections.createCorrectiveOrder(id, null);
        orders.start(id, null);

        verify(shiftService).start(id, new StartShiftRequest(null, null));
        verify(shiftService).close(id, new CloseShiftRequest(null, null, null, null));
        verify(inspectionService).createDefect(id, new CreateDefectFromInspectionRequest(null, null, null, null));
        verify(inspectionService).createCorrectiveOrder(id, new CreateCorrectiveOrderRequest(null, null, null, null, null));
        verify(orderService).start(id, null);
    }

    @Test
    void assetTeamReportTaskTypeAndTemplateControllersDelegateToTheirServices() {
        CatenaryAssetService assetService = mock(CatenaryAssetService.class);
        MaintenanceOrderService orderService = mock(MaintenanceOrderService.class);
        CatenaryAssetController assets = new CatenaryAssetController(assetService, orderService);
        MaintenanceTeamService teamService = mock(MaintenanceTeamService.class);
        MaintenanceTeamController teams = new MaintenanceTeamController(teamService);
        MaintenanceReportService reportService = mock(MaintenanceReportService.class);
        MaintenanceReportController reports = new MaintenanceReportController(reportService, mock(ReportExportService.class));
        MaintenanceTaskTypeService taskTypeService = mock(MaintenanceTaskTypeService.class);
        MaintenanceTaskTypeController taskTypes = new MaintenanceTaskTypeController(taskTypeService);
        InspectionTemplateService templateService = mock(InspectionTemplateService.class);
        InspectionTemplateController templates = new InspectionTemplateController(templateService);
        UUID assetId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        CatenaryAssetRequest assetRequest = new CatenaryAssetRequest("SEC-1", "T2", null, 6L, 2L, null, new BigDecimal("12847.990"),
                new BigDecimal("14078.090"), TrackKind.MAIN, null);
        CatenaryAssetResponse asset = mock(CatenaryAssetResponse.class);
        when(asset.id()).thenReturn(assetId);
        when(assetService.createTrackSection(assetRequest)).thenReturn(asset);
        MaintenanceTeamRequest teamRequest = new MaintenanceTeamRequest("A", "Team A", null, null, null, null);
        MaintenanceTeamResponse team = mock(MaintenanceTeamResponse.class);
        when(team.id()).thenReturn(teamId);
        when(teamService.create(teamRequest)).thenReturn(team);
        MonthlyReportResponse monthly = mock(MonthlyReportResponse.class);
        when(reportService.monthly(YearMonth.of(2026, 1), 6L)).thenReturn(monthly);
        MaintenanceTaskTypeResponse taskType = mock(MaintenanceTaskTypeResponse.class);
        when(taskTypeService.findByCode("RG-01")).thenReturn(taskType);

        var createdAsset = assets.create(assetRequest);
        var disabled = assets.disable(assetId);
        var createdTeam = teams.create(teamRequest);

        assertEquals(HttpStatus.CREATED, createdAsset.getStatusCode());
        assertEquals("/api/v1/maintenance/assets/" + assetId, createdAsset.getHeaders().getLocation().toString());
        assertEquals(HttpStatus.NO_CONTENT, disabled.getStatusCode());
        verify(assetService).disable(assetId);
        assets.orders(assetId, pageable);
        verify(orderService).findByAsset(assetId, pageable);
        assets.search(CatenaryAssetType.PROFILE, 2L, null, null, true, null, "12-2.27", null, null, null, pageable);
        verify(assetService).search(CatenaryAssetType.PROFILE, 2L, null, null, true, null, "12-2.27", null, null, null, pageable);
        assertEquals("/api/v1/maintenance/teams/" + teamId, createdTeam.getHeaders().getLocation().toString());
        assertSame(monthly, reports.monthly(YearMonth.of(2026, 1), 6L, null).getBody());
        reports.progress(6L, 2L, CatenaryAssetType.PROFILE, null, null, null);
        verify(reportService).progress(6L, 2L, CatenaryAssetType.PROFILE, null, null);
        assertSame(taskType, taskTypes.findByCode("RG-01").getBody());
        taskTypes.findAll(null, true, null);
        verify(taskTypeService).findAll(null, true, null);
        templates.findAll();
        verify(templateService).findAll();
    }

    @Test
    void theReportEndpointsAnswerTheirDtoOrAFileDependingOnTheFormatAsked() {
        MaintenanceReportService reportService = mock(MaintenanceReportService.class);
        ReportExportService exports = mock(ReportExportService.class);
        MaintenanceReportController reports = new MaintenanceReportController(reportService, exports);
        MaintenanceShiftService shiftService = mock(MaintenanceShiftService.class);
        MaintenanceShiftController shifts = new MaintenanceShiftController(shiftService, mock(MaintenanceTaskService.class), exports);
        UUID shiftId = UUID.randomUUID();
        ProgressReportResponse progress = mock(ProgressReportResponse.class);
        ShiftReportResponse shiftReport = mock(ShiftReportResponse.class);
        when(reportService.progress(null, null, null, null, null)).thenReturn(progress);
        when(shiftService.report(shiftId)).thenReturn(shiftReport);
        when(exports.exportProgressReport(progress, ReportFormat.XLSX))
                .thenReturn(new ExportedReport("progress-report-2026-01-31.xlsx", ReportMediaTypes.XLSX, new byte[] {1, 2, 3}));
        when(exports.exportShiftReport(shiftReport, ReportFormat.PDF))
                .thenReturn(new ExportedReport("shift-report-2026-01-27-SH-000001.pdf", ReportMediaTypes.PDF, new byte[] {4}));

        assertSame(progress, reports.progress(null, null, null, null, null, null).getBody(), "no format is the JSON of always");
        assertSame(shiftReport, shifts.report(shiftId, "json").getBody());

        var workbook = reports.progress(null, null, null, null, null, "xlsx");
        assertEquals(ReportMediaTypes.XLSX, workbook.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"progress-report-2026-01-31.xlsx\"",
                workbook.getHeaders().getFirst("Content-Disposition"));
        assertEquals(3, workbook.getHeaders().getContentLength());

        var printable = shifts.report(shiftId, "pdf");
        assertEquals(ReportMediaTypes.PDF, printable.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"shift-report-2026-01-27-SH-000001.pdf\"",
                printable.getHeaders().getFirst("Content-Disposition"));

        // El informe se calcula igual y lo que falla es la peticion, no el exportador.
        assertThrows(ValidationException.class, () -> reports.progress(null, null, null, null, null, "csv"));
        verify(exports, never()).exportProgressReport(progress, null);
    }
}
