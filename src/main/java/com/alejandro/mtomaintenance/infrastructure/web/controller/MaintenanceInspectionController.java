package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.CheckItemUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.CreateCorrectiveOrderRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.CreateDefectFromInspectionRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.application.service.MaintenanceInspectionService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;
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
import java.util.UUID;

@Validated
@RestController
@RequestMapping(MaintenanceApiPaths.BASE + "/inspections")
@Tag(name = "Inspections")
public class MaintenanceInspectionController {

    private final MaintenanceInspectionService inspectionService;

    public MaintenanceInspectionController(MaintenanceInspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @Operation(summary = "Create inspection", description = "Copies the active checklist of the asset type; disconnectors and section insulators get their own.")
    @PostMapping
    public ResponseEntity<MaintenanceInspectionResponse> create(@Valid @RequestBody MaintenanceInspectionRequest request) {
        MaintenanceInspectionResponse response = inspectionService.create(request);
        return ResponseEntity.created(URI.create(MaintenanceApiPaths.BASE + "/inspections/" + response.id())).body(response);
    }

    @Operation(summary = "Update inspection", description = "An OK result cannot coexist with items in DEFECT.")
    @PutMapping("/{id}")
    public ResponseEntity<MaintenanceInspectionResponse> update(@PathVariable UUID id, @Valid @RequestBody MaintenanceInspectionUpdateRequest request) {
        return ResponseEntity.ok(inspectionService.update(id, request));
    }

    @Operation(summary = "Update an inspection item", description = "A measurement out of range cannot be OK unless adjusted back into range.")
    @PutMapping("/{id}/items/{itemId}")
    public ResponseEntity<MaintenanceInspectionResponse> updateItem(@PathVariable UUID id, @PathVariable UUID itemId,
                                                                    @Valid @RequestBody CheckItemUpdateRequest request) {
        return ResponseEntity.ok(inspectionService.updateItem(id, itemId, request));
    }

    @Operation(summary = "Get inspection")
    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceInspectionResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(inspectionService.findById(id));
    }

    @Operation(summary = "Search inspections")
    @GetMapping
    public ResponseEntity<PageResponse<MaintenanceInspectionResponse>> search(
            @RequestParam(required = false) InspectionResult result,
            @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) CatenaryAssetType assetType,
            @RequestParam(required = false) Long trackId,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) Long executionPackageId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inspectionFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inspectionTo,
            @RequestParam(required = false) String inspector,
            @RequestParam(required = false) UUID originOrderId,
            @PageableDefault(size = 20, sort = "inspectionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(inspectionService.search(result, assetId, assetType, trackId, stationId, executionPackageId,
                inspectionFrom, inspectionTo, inspector, originOrderId, pageable));
    }

    @Operation(summary = "Create defect from the inspection", tags = "Defects", description = "MAJOR_DEFECT/UNSAFE (MINOR_DEFECT with force). Idempotent: repeating it returns the defect already created.")
    @PostMapping("/{id}/create-defect")
    public ResponseEntity<CatenaryDefectResponse> createDefect(@PathVariable UUID id, @RequestBody(required = false) CreateDefectFromInspectionRequest request) {
        return ResponseEntity.ok(inspectionService.createDefect(id, request == null ? new CreateDefectFromInspectionRequest(null, null, null, null) : request));
    }

    @Operation(summary = "Create corrective order from the inspection", tags = "Orders", description = "UNSAFE creates an URGENT order (CRITICAL), the rest a CORRECTIVE one. Idempotent: repeating it returns the order already created.")
    @PostMapping("/{id}/create-corrective-order")
    public ResponseEntity<MaintenanceOrderResponse> createCorrectiveOrder(@PathVariable UUID id, @RequestBody(required = false) @Valid CreateCorrectiveOrderRequest request) {
        return ResponseEntity.ok(inspectionService.createCorrectiveOrder(id, request == null ? new CreateCorrectiveOrderRequest(null, null, null, null, null) : request));
    }

    @Operation(summary = "Inspection change history (Envers revisions)")
    @GetMapping("/{id}/revisions")
    public ResponseEntity<PageResponse<EntityRevisionResponse<MaintenanceInspectionResponse>>> revisions(@PathVariable UUID id,
                                                                                                          @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(inspectionService.findRevisions(id, pageable));
    }
}
