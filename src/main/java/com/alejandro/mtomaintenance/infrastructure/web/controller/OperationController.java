package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.operation.OperationResponse;
import com.alejandro.mtomaintenance.application.service.BudgetedOperations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Las operaciones de fondo: lo que se contesto con 202 y como va. */
@RestController
@RequestMapping(MaintenanceApiPaths.OPERATIONS)
@RequiredArgsConstructor
@Tag(name = "Operations")
public class OperationController {

    private final BudgetedOperations operations;

    @GetMapping("/{id}")
    @Operation(summary = "A background operation: its status and, when finished, the body of the synchronous call or its error. "
            + "Only to whom asked for it, to maintenance-supervise or to ops-write.")
    public OperationResponse find(@PathVariable UUID id) {
        return operations.find(id);
    }
}
