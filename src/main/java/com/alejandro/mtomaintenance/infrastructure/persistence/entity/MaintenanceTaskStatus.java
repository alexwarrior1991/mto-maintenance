package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

/** Ciclo de vida de una tarea dentro de su orden. */
public enum MaintenanceTaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
