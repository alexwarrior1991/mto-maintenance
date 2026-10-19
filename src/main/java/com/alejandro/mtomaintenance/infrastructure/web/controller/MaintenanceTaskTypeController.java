package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.dto.tasktype.MaintenanceTaskTypeResponse;
import com.alejandro.mtomaintenance.application.dto.tasktype.MaintenanceTaskTypeUpdateRequest;
import com.alejandro.mtomaintenance.application.service.MaintenanceTaskTypeService;
import com.alejandro.mtomaintenance.configuration.security.SecurityRoles;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FunctionalGroup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(MaintenanceApiPaths.BASE + "/task-types")
@Tag(name = "Task Types")
public class MaintenanceTaskTypeController {

    private final MaintenanceTaskTypeService taskTypeService;

    public MaintenanceTaskTypeController(MaintenanceTaskTypeService taskTypeService) {
        this.taskTypeService = taskTypeService;
    }

    @Operation(summary = "List task types", description = "RG (general revision) and RP (particular revision) catalogue with standard times and execution windows.")
    @GetMapping
    public ResponseEntity<List<MaintenanceTaskTypeResponse>> findAll(@RequestParam(required = false) FunctionalGroup functionalGroup,
                                                                     @RequestParam(required = false) Boolean requiresFullPossession,
                                                                     @RequestParam(required = false) Boolean diagnostic) {
        return ResponseEntity.ok(taskTypeService.findAll(functionalGroup, requiresFullPossession, diagnostic));
    }

    @Operation(summary = "Get task type by code")
    @GetMapping("/{code}")
    public ResponseEntity<MaintenanceTaskTypeResponse> findByCode(@PathVariable String code) {
        return ResponseEntity.ok(taskTypeService.findByCode(code));
    }

    @Operation(summary = "Patch the equipment a task type requires",
            description = "application/merge-patch+json with requiredEquipment (INSULATED_PLATFORM, EARTHING) and version: the set "
                    + "replaces the previous one, null empties it. A task of this type is then assigned, started and completed "
                    + "only in a shift that carries it (409 SHF-001). Needs maintenance-supervise besides maintenance-write.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MergePatch.MEDIA_TYPE,
                    schema = @Schema(implementation = MaintenanceTaskTypeUpdateRequest.class))))
    @PatchMapping(value = "/{code}", consumes = MergePatch.MEDIA_TYPE)
    @PreAuthorize("hasRole('" + SecurityRoles.MAINTENANCE_SUPERVISE + "')")
    public ResponseEntity<MaintenanceTaskTypeResponse> patch(@PathVariable String code,
            @Parameter(hidden = true) MergePatch<MaintenanceTaskTypeUpdateRequest> patch) {
        return ResponseEntity.ok(taskTypeService.patch(code, patch));
    }
}
