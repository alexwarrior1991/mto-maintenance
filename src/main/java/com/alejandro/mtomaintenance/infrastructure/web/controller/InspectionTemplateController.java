package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateResponse;
import com.alejandro.mtomaintenance.application.service.InspectionTemplateService;
import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateUpdateRequest;
import com.alejandro.mtomaintenance.configuration.security.SecurityRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(MaintenanceApiPaths.BASE + "/inspection-templates")
@Tag(name = "Inspection Templates")
public class InspectionTemplateController {

    private final InspectionTemplateService templateService;

    public InspectionTemplateController(InspectionTemplateService templateService) {
        this.templateService = templateService;
    }

    @Operation(summary = "List inspection templates", description = "One checklist per asset type: profile, disconnector and section insulator each have their own.")
    @GetMapping
    public ResponseEntity<List<InspectionTemplateResponse>> findAll() {
        return ResponseEntity.ok(templateService.findAll());
    }

    @Operation(summary = "Get inspection template")
    @GetMapping("/{id}")
    public ResponseEntity<InspectionTemplateResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(templateService.findById(id));
    }

    @Operation(summary = "Create the next version of an asset type's template",
            description = "The whole item list, in order. The new version is born active and replaces the active one of its asset "
                    + "type, which stays as a previous version; inspections already created keep their own items. With "
                    + "replacesVersion, 409 CON-001 if the active version is no longer that one. Needs maintenance-supervise "
                    + "besides maintenance-write.")
    @PostMapping
    @PreAuthorize("hasRole('" + SecurityRoles.MAINTENANCE_SUPERVISE + "')")
    public ResponseEntity<InspectionTemplateResponse> create(@Valid @RequestBody InspectionTemplateRequest request) {
        InspectionTemplateResponse response = templateService.create(request);
        return ResponseEntity.created(URI.create(MaintenanceApiPaths.BASE + "/inspection-templates/" + response.id())).body(response);
    }

    @Operation(summary = "Rename, activate or deactivate a template version",
            description = "application/merge-patch+json with name, active and lockVersion. active=true makes it the active one of "
                    + "its asset type (the one that was stops being it); active=false leaves the type without a template when it "
                    + "was the active one, and inspections of that type are then created without a checklist. The items of a "
                    + "version never change: POST a new version. Needs maintenance-supervise besides maintenance-write.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MergePatch.MEDIA_TYPE,
                    schema = @Schema(implementation = InspectionTemplateUpdateRequest.class))))
    @PatchMapping(value = "/{id}", consumes = MergePatch.MEDIA_TYPE)
    @PreAuthorize("hasRole('" + SecurityRoles.MAINTENANCE_SUPERVISE + "')")
    public ResponseEntity<InspectionTemplateResponse> patch(@PathVariable UUID id,
            @Parameter(hidden = true) @Valid MergePatch<InspectionTemplateUpdateRequest> patch) {
        return ResponseEntity.ok(templateService.patch(id, patch));
    }
}
