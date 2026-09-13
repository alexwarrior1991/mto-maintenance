package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectRequest;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectResponse;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.defect.DefectCommentRequest;
import com.alejandro.mtomaintenance.application.dto.defect.ResolveDefectRequest;
import com.alejandro.mtomaintenance.application.dto.history.StatusHistoryResponse;
import com.alejandro.mtomaintenance.application.service.CatenaryDefectService;
import com.alejandro.mtomaintenance.application.service.StatusHistoryService;
import com.alejandro.mtomaintenance.configuration.security.SecurityRoles;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
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
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping(MaintenanceApiPaths.BASE + "/defects")
@Tag(name = "Defects")
public class CatenaryDefectController {

    private final CatenaryDefectService defectService;
    private final StatusHistoryService historyService;

    public CatenaryDefectController(CatenaryDefectService defectService, StatusHistoryService historyService) {
        this.defectService = defectService;
        this.historyService = historyService;
    }

    @Operation(summary = "Create defect")
    @PostMapping
    public ResponseEntity<CatenaryDefectResponse> create(@Valid @RequestBody CatenaryDefectRequest request) {
        CatenaryDefectResponse response = defectService.create(request);
        return ResponseEntity.created(URI.create(MaintenanceApiPaths.BASE + "/defects/" + response.id())).body(response);
    }

    @Operation(summary = "Update defect")
    @PutMapping("/{id}")
    public ResponseEntity<CatenaryDefectResponse> update(@PathVariable UUID id, @Valid @RequestBody CatenaryDefectUpdateRequest request) {
        return ResponseEntity.ok(defectService.update(id, request));
    }

    @Operation(summary = "Get defect")
    @GetMapping("/{id}")
    public ResponseEntity<CatenaryDefectResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(defectService.findById(id));
    }

    @Operation(summary = "Search defects")
    @GetMapping
    public ResponseEntity<PageResponse<CatenaryDefectResponse>> search(
            @RequestParam(required = false) DefectSeverity severity,
            @RequestParam(required = false) DefectStatus status,
            @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) Long trackId,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) Long executionPackageId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant detectedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant detectedTo,
            @PageableDefault(size = 20, sort = "detectedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(defectService.search(severity, status, assetId, orderId, trackId, stationId, executionPackageId, detectedFrom, detectedTo, pageable));
    }

    @Operation(summary = "Resolve defect", description = "OPEN/IN_PROGRESS -> RESOLVED. With a linked order, the order must be COMPLETED unless the shift where it was corrected is declared. Requires the supervise role.")
    @PreAuthorize("hasRole('" + SecurityRoles.MAINTENANCE_SUPERVISE + "')")
    @PostMapping("/{id}/resolve")
    public ResponseEntity<CatenaryDefectResponse> resolve(@PathVariable UUID id, @Valid @RequestBody ResolveDefectRequest request) {
        return ResponseEntity.ok(defectService.resolve(id, request));
    }

    @Operation(summary = "Close defect", description = "RESOLVED -> CLOSED. Requires the supervise role.")
    @PreAuthorize("hasRole('" + SecurityRoles.MAINTENANCE_SUPERVISE + "')")
    @PostMapping("/{id}/close")
    public ResponseEntity<CatenaryDefectResponse> close(@PathVariable UUID id, @Valid @RequestBody DefectCommentRequest request) {
        return ResponseEntity.ok(defectService.close(id, request));
    }

    @Operation(summary = "Discard defect", description = "OPEN -> DISCARDED with a reason. Requires the supervise role.")
    @PreAuthorize("hasRole('" + SecurityRoles.MAINTENANCE_SUPERVISE + "')")
    @PostMapping("/{id}/discard")
    public ResponseEntity<CatenaryDefectResponse> discard(@PathVariable UUID id, @Valid @RequestBody DefectCommentRequest request) {
        return ResponseEntity.ok(defectService.discard(id, request));
    }

    @Operation(summary = "Link defect to an order", description = "OPEN -> IN_PROGRESS.")
    @PostMapping("/{id}/link-order/{orderId}")
    public ResponseEntity<CatenaryDefectResponse> linkOrder(@PathVariable UUID id, @PathVariable UUID orderId) {
        return ResponseEntity.ok(defectService.linkOrder(id, orderId));
    }

    @Operation(summary = "Status history of the defect")
    @GetMapping("/{id}/history")
    public ResponseEntity<List<StatusHistoryResponse>> history(@PathVariable UUID id) {
        defectService.findById(id);
        return ResponseEntity.ok(historyService.findByDefect(id));
    }

    @Operation(summary = "Defect change history (Envers revisions)")
    @GetMapping("/{id}/revisions")
    public ResponseEntity<PageResponse<EntityRevisionResponse<CatenaryDefectResponse>>> revisions(@PathVariable UUID id,
                                                                                                   @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(defectService.findRevisions(id, pageable));
    }
}
