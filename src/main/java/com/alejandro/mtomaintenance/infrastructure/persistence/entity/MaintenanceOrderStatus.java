package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

/** Ciclo de vida de una orden; las transiciones las decide OrderStateMachine. */
public enum MaintenanceOrderStatus {
    DRAFT,
    PLANNED,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
