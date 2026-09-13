package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.tasktype.MaintenanceTaskTypeResponse;
import com.alejandro.mtomaintenance.application.service.MaintenanceTaskTypeService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FunctionalGroup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
