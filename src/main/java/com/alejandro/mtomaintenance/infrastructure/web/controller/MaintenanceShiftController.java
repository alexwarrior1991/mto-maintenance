package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.dto.shift.CancelShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.CloseShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftResponse;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportResponse;
import com.alejandro.mtomaintenance.application.dto.shift.StartShiftRequest;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskResponse;
import com.alejandro.mtomaintenance.application.service.MaintenanceShiftService;
import com.alejandro.mtomaintenance.application.service.MaintenanceTaskService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping(MaintenanceApiPaths.BASE + "/shifts")
@Tag(name = "Shifts")
public class MaintenanceShiftController {

    private final MaintenanceShiftService shiftService;
    private final MaintenanceTaskService taskService;

    public MaintenanceShiftController(MaintenanceShiftService shiftService, MaintenanceTaskService taskService) {
        this.shiftService = shiftService;
        this.taskService = taskService;
    }

    @Operation(summary = "Create shift", description = "A night shift on one track: team, possession type, disconnectors opened, earthing points, kp range.")
    @PostMapping
    public ResponseEntity<MaintenanceShiftResponse> create(@Valid @RequestBody MaintenanceShiftRequest request) {
        MaintenanceShiftResponse response = shiftService.create(request);
        return ResponseEntity.created(URI.create(MaintenanceApiPaths.BASE + "/shifts/" + response.id())).body(response);
    }

    @Operation(summary = "Update shift")
    @PutMapping("/{id}")
    public ResponseEntity<MaintenanceShiftResponse> update(@PathVariable UUID id, @Valid @RequestBody MaintenanceShiftUpdateRequest request) {
        return ResponseEntity.ok(shiftService.update(id, request));
    }

    @Operation(summary = "Get shift")
    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceShiftResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftService.findById(id));
    }

    @Operation(summary = "Search shifts")
    @GetMapping
    public ResponseEntity<PageResponse<MaintenanceShiftResponse>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) Long trackId,
            @RequestParam(required = false) Long executionPackageId,
            @RequestParam(required = false) ShiftStatus status,
            @RequestParam(required = false) PossessionType possessionType,
            @PageableDefault(size = 20, sort = "shiftDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(shiftService.search(date, dateFrom, dateTo, teamId, trackId, executionPackageId, status, possessionType, pageable));
    }

    @Operation(summary = "Start shift", description = "PLANNED -> IN_PROGRESS; records the real start and the voltage cut-off.")
    @PostMapping("/{id}/start")
    public ResponseEntity<MaintenanceShiftResponse> start(@PathVariable UUID id, @RequestBody(required = false) StartShiftRequest request) {
        return ResponseEntity.ok(shiftService.start(id, request == null ? new StartShiftRequest(null, null) : request));
    }

    @Operation(summary = "Close shift", description = "IN_PROGRESS -> CLOSED; computes the net work minutes and returns unfinished tasks to the order queue.")
    @PostMapping("/{id}/close")
    public ResponseEntity<MaintenanceShiftResponse> close(@PathVariable UUID id, @RequestBody(required = false) @Valid CloseShiftRequest request) {
        return ResponseEntity.ok(shiftService.close(id, request == null ? new CloseShiftRequest(null, null, null, null) : request));
    }

    @Operation(summary = "Cancel shift")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<MaintenanceShiftResponse> cancel(@PathVariable UUID id, @Valid @RequestBody CancelShiftRequest request) {
        return ResponseEntity.ok(shiftService.cancel(id, request));
    }

    @Operation(summary = "Tasks worked in the shift", tags = "Tasks")
    @GetMapping("/{id}/tasks")
    public ResponseEntity<List<MaintenanceTaskResponse>> tasks(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.findByShift(id));
    }

    @Operation(summary = "Assign a pending task to the shift", tags = "Tasks", description = "Checks that the task belongs to the shift's track and that its work is allowed under the shift's possession.")
    @PostMapping("/{id}/tasks/{taskId}")
    public ResponseEntity<MaintenanceTaskResponse> assignTask(@PathVariable UUID id, @PathVariable UUID taskId) {
        return ResponseEntity.ok(taskService.assignToShift(id, taskId));
    }

    @Operation(summary = "Daily report of the shift", tags = "Reports", description = "Header of the shift plus one row per task (profile) worked: works performed, defects found, materials, times, completion and repair date.")
    @GetMapping("/{id}/report")
    public ResponseEntity<ShiftReportResponse> report(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftService.report(id));
    }

    @Operation(summary = "Shift change history (Envers revisions)")
    @GetMapping("/{id}/revisions")
    public ResponseEntity<PageResponse<EntityRevisionResponse<MaintenanceShiftResponse>>> revisions(@PathVariable UUID id,
                                                                                                     @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(shiftService.findRevisions(id, pageable));
    }
}
