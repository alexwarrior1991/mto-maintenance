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
        MaintenanceShiftController controller = new MaintenanceShiftController(shiftService, taskService);
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
}
