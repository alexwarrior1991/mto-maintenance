package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateResponse;
import com.alejandro.mtomaintenance.application.service.InspectionTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
