package com.alejandro.mtomaintenance.application.dto.order;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Planificacion de una orden (DRAFT a PLANNED) con su fecha prevista.
 *
 * <p>Ejemplo, {@code POST /orders/{id}/plan}:</p>
 * <pre>{@code
 * {"plannedDate": "2026-01-27", "comment": "Semana 5, equipo A"}
 * }</pre>
 */
public record PlanOrderRequest(@NotNull LocalDate plannedDate, String comment) {
}
