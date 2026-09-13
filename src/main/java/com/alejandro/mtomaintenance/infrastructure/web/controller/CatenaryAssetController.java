package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetRequest;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetResponse;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.application.service.CatenaryAssetService;
import com.alejandro.mtomaintenance.application.service.MaintenanceOrderService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping(MaintenanceApiPaths.BASE + "/assets")
@Tag(name = "Assets")
public class CatenaryAssetController {

    private final CatenaryAssetService assetService;
    private final MaintenanceOrderService orderService;

    public CatenaryAssetController(CatenaryAssetService assetService, MaintenanceOrderService orderService) {
        this.assetService = assetService;
        this.orderService = orderService;
    }

    @Operation(summary = "Create track section", description = "Creates a maintainable track section (a track between two kilometric points). Profiles, disconnectors and section insulators come from mto-configuration events and cannot be created here.")
    @PostMapping
    public ResponseEntity<CatenaryAssetResponse> create(@Valid @RequestBody CatenaryAssetRequest request) {
        CatenaryAssetResponse response = assetService.createTrackSection(request);
        return ResponseEntity.created(URI.create(MaintenanceApiPaths.BASE + "/assets/" + response.id())).body(response);
    }

    @Operation(summary = "Update asset", description = "Assets synchronized from master data only accept description, enabled and preventiveIntervalDays.")
    @PutMapping("/{id}")
    public ResponseEntity<CatenaryAssetResponse> update(@PathVariable UUID id, @Valid @RequestBody CatenaryAssetUpdateRequest request) {
        return ResponseEntity.ok(assetService.update(id, request));
    }

    @Operation(summary = "Get asset")
    @GetMapping("/{id}")
    public ResponseEntity<CatenaryAssetResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(assetService.findById(id));
    }

    @Operation(summary = "Search assets", description = "Composable filters; preventiveDueBefore returns the assets whose preventive maintenance is due before that instant.")
    @GetMapping
    public ResponseEntity<PageResponse<CatenaryAssetResponse>> search(
            @RequestParam(required = false) CatenaryAssetType type,
            @RequestParam(required = false) Long trackId,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) Long executionPackageId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) BigDecimal kpFrom,
            @RequestParam(required = false) BigDecimal kpTo,
            @Parameter(description = "ISO-8601 instant") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant preventiveDueBefore,
            @PageableDefault(size = 20, sort = {"trackId", "startKp"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(assetService.search(type, trackId, stationId, executionPackageId, enabled, code, kpFrom, kpTo, preventiveDueBefore, pageable));
    }

    @Operation(summary = "Disable asset", description = "Assets are never deleted: orders, inspections and defects reference them. DELETE disables the asset.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disable(@PathVariable UUID id) {
        assetService.disable(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Orders of an asset")
    @GetMapping("/{id}/orders")
    public ResponseEntity<PageResponse<MaintenanceOrderResponse>> orders(@PathVariable UUID id,
                                                                         @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.findByAsset(id, pageable));
    }

    @Operation(summary = "Asset change history", description = "Envers revisions. Changes applied from master data events leave no revision (native upsert).")
    @GetMapping("/{id}/revisions")
    public ResponseEntity<PageResponse<EntityRevisionResponse<CatenaryAssetResponse>>> revisions(@PathVariable UUID id,
                                                                                                  @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(assetService.findRevisions(id, pageable));
    }
}
