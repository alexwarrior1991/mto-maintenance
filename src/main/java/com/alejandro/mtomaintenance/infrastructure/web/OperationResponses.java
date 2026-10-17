package com.alejandro.mtomaintenance.infrastructure.web;

import com.alejandro.mtomaintenance.application.service.BudgetedOperations;
import com.alejandro.mtomaintenance.infrastructure.web.controller.MaintenanceApiPaths;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

/**
 * La respuesta de algo que pudo terminar o quedarse en segundo plano: el resultado con su estado de
 * siempre, o {@code 202 Accepted} con la operacion, su {@code Location} y {@code Preference-Applied}.
 */
public final class OperationResponses {

    private OperationResponses() {
    }

    public static ResponseEntity<Object> of(BudgetedOperations.Outcome<?> outcome, HttpStatus successStatus) {
        if (outcome.completed()) {
            return ResponseEntity.status(successStatus).body(outcome.result());
        }
        return accepted(outcome);
    }

    public static ResponseEntity<Object> accepted(BudgetedOperations.Outcome<?> outcome) {
        return ResponseEntity.accepted()
                .location(URI.create(MaintenanceApiPaths.OPERATIONS + "/" + outcome.operation().id()))
                .header(PreferHeader.PREFERENCE_APPLIED, PreferHeader.RESPOND_ASYNC)
                .body(outcome.operation());
    }
}
