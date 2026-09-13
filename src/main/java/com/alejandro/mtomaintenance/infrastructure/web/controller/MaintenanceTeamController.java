package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamRequest;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamResponse;
import com.alejandro.mtomaintenance.application.service.MaintenanceTeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping(MaintenanceApiPaths.BASE + "/teams")
@Tag(name = "Teams")
public class MaintenanceTeamController {

    private final MaintenanceTeamService teamService;

    public MaintenanceTeamController(MaintenanceTeamService teamService) {
        this.teamService = teamService;
    }

    @Operation(summary = "List teams")
    @GetMapping
    public ResponseEntity<List<MaintenanceTeamResponse>> findAll() {
        return ResponseEntity.ok(teamService.findAll());
    }

    @Operation(summary = "Get team")
    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceTeamResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(teamService.findById(id));
    }

    @Operation(summary = "Create team")
    @PostMapping
    public ResponseEntity<MaintenanceTeamResponse> create(@Valid @RequestBody MaintenanceTeamRequest request) {
        MaintenanceTeamResponse response = teamService.create(request);
        return ResponseEntity.created(URI.create(MaintenanceApiPaths.BASE + "/teams/" + response.id())).body(response);
    }

    @Operation(summary = "Update team")
    @PutMapping("/{id}")
    public ResponseEntity<MaintenanceTeamResponse> update(@PathVariable UUID id, @Valid @RequestBody MaintenanceTeamRequest request) {
        return ResponseEntity.ok(teamService.update(id, request));
    }
}
