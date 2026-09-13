package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.dto.history.StatusHistoryResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.CheckItemUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageRequest;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageResponse;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.order.AssignOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.CancelOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.CompleteOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.order.OrderCommentRequest;
import com.alejandro.mtomaintenance.application.dto.order.PlanOrderRequest;
import com.alejandro.mtomaintenance.application.dto.task.CancelTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.CompleteTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksResponse;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskResponse;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.task.StartTaskRequest;
import com.alejandro.mtomaintenance.application.service.MaintenanceMaterialUsageService;
import com.alejandro.mtomaintenance.application.service.MaintenanceOrderService;
import com.alejandro.mtomaintenance.application.service.MaintenanceTaskService;
import com.alejandro.mtomaintenance.application.service.StatusHistoryService;
import com.alejandro.mtomaintenance.configuration.security.SecurityRoles;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping(MaintenanceApiPaths.BASE + "/orders")
@Tag(name = "Orders")
public class MaintenanceOrderController {

    private final MaintenanceOrderService orderService;
    private final MaintenanceTaskService taskService;
    private final MaintenanceMaterialUsageService materialService;
    private final StatusHistoryService historyService;

    public MaintenanceOrderController(MaintenanceOrderService orderService, MaintenanceTaskService taskService,
                                      MaintenanceMaterialUsageService materialService, StatusHistoryService historyService) {
        this.orderService = orderService;
        this.taskService = taskService;
        this.materialService = materialService;
        this.historyService = historyService;
    }

    // ---------------------------------------------------------------- orders

    @Operation(summary = "Create order", description = "Creates an order in DRAFT on an enabled asset. URGENT orders are always CRITICAL.")
    @PostMapping
    public ResponseEntity<MaintenanceOrderResponse> create(@Valid @RequestBody MaintenanceOrderRequest request) {
        MaintenanceOrderResponse response = orderService.create(request);
        return ResponseEntity.created(URI.create(MaintenanceApiPaths.BASE + "/orders/" + response.id())).body(response);
    }

    @Operation(summary = "Update order", description = "Full update in DRAFT/PLANNED; afterwards only description, priority and closingNotes.")
    @PutMapping("/{id}")
    public ResponseEntity<MaintenanceOrderResponse> update(@PathVariable UUID id, @Valid @RequestBody MaintenanceOrderUpdateRequest request) {
        return ResponseEntity.ok(orderService.update(id, request));
    }

    @Operation(summary = "Get order")
    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceOrderResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    @Operation(summary = "Search orders")
    @GetMapping
    public ResponseEntity<PageResponse<MaintenanceOrderResponse>> search(
            @RequestParam(required = false) MaintenanceOrderStatus status,
            @RequestParam(required = false) MaintenanceOrderType type,
            @RequestParam(required = false) MaintenancePriority priority,
            @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) CatenaryAssetType assetType,
            @RequestParam(required = false) Long trackId,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) Long executionPackageId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate plannedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate plannedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant actualStartFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant actualStartTo,
            @RequestParam(required = false) String assignedUser,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) String code,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.search(status, type, priority, assetId, assetType, trackId, stationId, executionPackageId,
                plannedFrom, plannedTo, actualStartFrom, actualStartTo, assignedUser, teamId, code, pageable));
    }

    @Operation(summary = "Plan order", description = "DRAFT -> PLANNED. Reserves the materials in mto-stock when the order has a stock project.")
    @PostMapping("/{id}/plan")
    public ResponseEntity<MaintenanceOrderResponse> plan(@PathVariable UUID id, @Valid @RequestBody PlanOrderRequest request) {
        return ResponseEntity.ok(orderService.plan(id, request));
    }

    @Operation(summary = "Assign order", description = "PLANNED/ASSIGNED -> ASSIGNED; an order IN_PROGRESS is reassigned without changing state.")
    @PostMapping("/{id}/assign")
    public ResponseEntity<MaintenanceOrderResponse> assign(@PathVariable UUID id, @Valid @RequestBody AssignOrderRequest request) {
        return ResponseEntity.ok(orderService.assign(id, request));
    }

    @Operation(summary = "Start order", description = "PLANNED/ASSIGNED -> IN_PROGRESS (URGENT also from DRAFT). Sets actualStartDate if missing.")
    @PostMapping("/{id}/start")
    public ResponseEntity<MaintenanceOrderResponse> start(@PathVariable UUID id, @RequestBody(required = false) OrderCommentRequest request) {
        return ResponseEntity.ok(orderService.start(id, request == null ? null : request.comment()));
    }

    @Operation(summary = "Complete order", description = "IN_PROGRESS -> COMPLETED. Needs an actual start, no open tasks, a completed task or closing notes; consumes the materials in mto-stock. force (supervise role) completes with material lines still pending stock synchronization.")
    @PreAuthorize("!#request.forced or hasRole('" + SecurityRoles.MAINTENANCE_SUPERVISE + "')")
    @PostMapping("/{id}/complete")
    public ResponseEntity<MaintenanceOrderResponse> complete(@PathVariable UUID id, @Valid @RequestBody CompleteOrderRequest request) {
        return ResponseEntity.ok(orderService.complete(id, request));
    }

    @Operation(summary = "Cancel order", description = "Any non-terminal state -> CANCELLED. Releases stock reservations and reopens linked defects. Requires the supervise role.")
    @PreAuthorize("hasRole('" + SecurityRoles.MAINTENANCE_SUPERVISE + "')")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<MaintenanceOrderResponse> cancel(@PathVariable UUID id, @Valid @RequestBody CancelOrderRequest request) {
        return ResponseEntity.ok(orderService.cancel(id, request));
    }

    @Operation(summary = "Status history of the order")
    @GetMapping("/{id}/history")
    public ResponseEntity<List<StatusHistoryResponse>> history(@PathVariable UUID id) {
        orderService.findById(id);
        return ResponseEntity.ok(historyService.findByOrder(id));
    }

    @Operation(summary = "Order change history (Envers revisions)")
    @GetMapping("/{id}/revisions")
    public ResponseEntity<PageResponse<EntityRevisionResponse<MaintenanceOrderResponse>>> revisions(@PathVariable UUID id,
                                                                                                     @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.findRevisions(id, pageable));
    }

    // ----------------------------------------------------------------- tasks

    @Operation(summary = "Tasks of the order", tags = "Tasks")
    @GetMapping("/{id}/tasks")
    public ResponseEntity<List<MaintenanceTaskResponse>> tasks(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.findByOrder(id));
    }

    @Operation(summary = "Add task", tags = "Tasks")
    @PostMapping("/{id}/tasks")
    public ResponseEntity<MaintenanceTaskResponse> createTask(@PathVariable UUID id, @Valid @RequestBody MaintenanceTaskRequest request) {
        MaintenanceTaskResponse response = taskService.create(id, request);
        return ResponseEntity.created(URI.create(MaintenanceApiPaths.BASE + "/orders/" + id + "/tasks/" + response.id())).body(response);
    }

    @Operation(summary = "Generate preventive tasks profile by profile", tags = "Tasks",
            description = "One task per enabled PROFILE of the order's track section, ordered by kp, with the given task types (default: groups 1, 2 and 4). Idempotent per profile.")
    @PostMapping("/{id}/tasks/generate")
    public ResponseEntity<GeneratePreventiveTasksResponse> generateTasks(@PathVariable UUID id, @RequestBody(required = false) GeneratePreventiveTasksRequest request) {
        return ResponseEntity.ok(taskService.generatePreventiveTasks(id, request == null ? new GeneratePreventiveTasksRequest(null, null) : request));
    }

    @Operation(summary = "Get task", tags = "Tasks")
    @GetMapping("/{id}/tasks/{taskId}")
    public ResponseEntity<MaintenanceTaskResponse> findTask(@PathVariable UUID id, @PathVariable UUID taskId) {
        return ResponseEntity.ok(taskService.findById(id, taskId));
    }

    @Operation(summary = "Update task", tags = "Tasks")
    @PutMapping("/{id}/tasks/{taskId}")
    public ResponseEntity<MaintenanceTaskResponse> updateTask(@PathVariable UUID id, @PathVariable UUID taskId,
                                                              @Valid @RequestBody MaintenanceTaskUpdateRequest request) {
        return ResponseEntity.ok(taskService.update(id, taskId, request));
    }

    @Operation(summary = "Start task", tags = "Tasks", description = "Needs the order IN_PROGRESS and a shift IN_PROGRESS on the same track with a compatible possession.")
    @PostMapping("/{id}/tasks/{taskId}/start")
    public ResponseEntity<MaintenanceTaskResponse> startTask(@PathVariable UUID id, @PathVariable UUID taskId,
                                                             @Valid @RequestBody StartTaskRequest request) {
        return ResponseEntity.ok(taskService.start(id, taskId, request));
    }

    @Operation(summary = "Complete task", tags = "Tasks", description = "The daily report row: works performed, defects found (inline defects, resolved or pending with a repair date), materials used and photos.")
    @PostMapping("/{id}/tasks/{taskId}/complete")
    public ResponseEntity<MaintenanceTaskResponse> completeTask(@PathVariable UUID id, @PathVariable UUID taskId,
                                                                @Valid @RequestBody CompleteTaskRequest request) {
        return ResponseEntity.ok(taskService.complete(id, taskId, request));
    }

    @Operation(summary = "Cancel task", tags = "Tasks")
    @PostMapping("/{id}/tasks/{taskId}/cancel")
    public ResponseEntity<MaintenanceTaskResponse> cancelTask(@PathVariable UUID id, @PathVariable UUID taskId,
                                                              @Valid @RequestBody CancelTaskRequest request) {
        return ResponseEntity.ok(taskService.cancel(id, taskId, request));
    }

    @Operation(summary = "Update a check item of the task", tags = "Tasks")
    @PutMapping("/{id}/tasks/{taskId}/check-items/{itemId}")
    public ResponseEntity<MaintenanceTaskResponse> updateCheckItem(@PathVariable UUID id, @PathVariable UUID taskId, @PathVariable UUID itemId,
                                                                   @Valid @RequestBody CheckItemUpdateRequest request) {
        return ResponseEntity.ok(taskService.updateCheckItem(id, taskId, itemId, request));
    }

    // ------------------------------------------------------------- materials

    @Operation(summary = "Materials of the order", tags = "Materials")
    @GetMapping("/{id}/materials")
    public ResponseEntity<List<MaterialUsageResponse>> materials(@PathVariable UUID id) {
        return ResponseEntity.ok(materialService.findByOrder(id));
    }

    @Operation(summary = "Register material", tags = "Materials", description = "Adds a planned material line. Reserved in mto-stock immediately when the order is already planned; in DRAFT the reservation waits for /plan.")
    @PostMapping("/{id}/materials")
    public ResponseEntity<MaterialUsageResponse> registerMaterial(@PathVariable UUID id, @Valid @RequestBody MaterialUsageRequest request) {
        MaterialUsageResponse response = materialService.register(id, request);
        return ResponseEntity.created(URI.create(MaintenanceApiPaths.BASE + "/orders/" + id + "/materials/" + response.id())).body(response);
    }

    @Operation(summary = "Update material line", tags = "Materials", description = "Consumed quantity cannot exceed the planned one unless allowOverConsumption is set on the line.")
    @PutMapping("/{id}/materials/{usageId}")
    public ResponseEntity<MaterialUsageResponse> updateMaterial(@PathVariable UUID id, @PathVariable UUID usageId,
                                                                @Valid @RequestBody MaterialUsageUpdateRequest request) {
        return ResponseEntity.ok(materialService.update(id, usageId, request));
    }

    @Operation(summary = "Retry stock synchronization of a material line", tags = "Materials", description = "Answers 503 when mto-stock is still unavailable.")
    @PostMapping("/{id}/materials/{usageId}/sync")
    public ResponseEntity<MaterialUsageResponse> syncMaterial(@PathVariable UUID id, @PathVariable UUID usageId) {
        return ResponseEntity.ok(materialService.sync(id, usageId));
    }
}
