package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetRequest;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetResponse;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.application.service.CatenaryAssetService;
import com.alejandro.mtomaintenance.application.service.MaintenanceOrderService;
import com.alejandro.mtomaintenance.configuration.security.SecurityRoles;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    @Operation(summary = "Update asset", description = "Assets synchronized from master data only accept description, enabled and preventiveIntervalDays. "
            + "enabled=false disables the asset here, like DELETE, and needs the delete role too; it survives every master data event. "
            + "enabled=true lifts that, and answers 409 AST-001 when mto-configuration has the asset disabled: it comes back when the source enables it.")
    @PreAuthorize("!#request.disabling or hasRole('" + SecurityRoles.MAINTENANCE_DELETE + "')")
    @PutMapping("/{id}")
    public ResponseEntity<CatenaryAssetResponse> update(@PathVariable UUID id, @Valid @RequestBody CatenaryAssetUpdateRequest request) {
        return ResponseEntity.ok(assetService.update(id, request));
    }

    @Operation(summary = "Patch asset", description = "application/merge-patch+json: a key left out is not touched, null empties it (only the optional fields: emptying a required one is 400), a value changes it as in PUT. A synchronized asset only empties description and preventiveIntervalDays; enabled=false needs the delete role too. With version, 409 CON-001 if it changed since it was read.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MergePatch.MEDIA_TYPE,
                    schema = @Schema(implementation = CatenaryAssetUpdateRequest.class))))
    @PreAuthorize("!#patch.values().disabling or hasRole('" + SecurityRoles.MAINTENANCE_DELETE + "')")
    @PatchMapping(value = "/{id}", consumes = MergePatch.MEDIA_TYPE)
    public ResponseEntity<CatenaryAssetResponse> patch(@PathVariable UUID id,
            @Parameter(hidden = true) @Valid MergePatch<CatenaryAssetUpdateRequest> patch) {
        return ResponseEntity.ok(assetService.patch(id, patch));
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
            @Parameter(description = "Natural name, as the field crew knows it: the profileId of a profile (12-2.27), "
                    + "the name of a disconnector (HSA-NS5). Partial and case insensitive; it is unique only together "
                    + "with trackId, because mto-configuration keeps the profileId unique per track, not globally.")
            @RequestParam(required = false) String name,
            @RequestParam(required = false) BigDecimal kpFrom,
            @RequestParam(required = false) BigDecimal kpTo,
            @Parameter(description = "ISO-8601 instant") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant preventiveDueBefore,
            @Parameter(description = "Section insulators connecting with this track, matched on their secondary track.")
            @RequestParam(required = false) Long connectedTrackId,
            @Parameter(description = "Section insulators sitting on a turnout with this code (W31). Partial and case insensitive.")
            @RequestParam(required = false) String switchCode,
            @PageableDefault(size = 20, sort = {"trackId", "startKp"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(assetService.search(type, trackId, stationId, executionPackageId, enabled, code, name, kpFrom, kpTo,
                preventiveDueBefore, connectedTrackId, switchCode, pageable));
    }

    @Operation(summary = "Disable asset", description = "Assets are never deleted: orders, inspections and defects reference them. DELETE disables the asset here; "
            + "a master data event does not undo it, and PUT with enabled=true does.")
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
