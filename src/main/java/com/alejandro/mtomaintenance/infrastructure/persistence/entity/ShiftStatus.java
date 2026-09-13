package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

/** Ciclo de vida de un turno nocturno. */
public enum ShiftStatus {
    PLANNED,
    IN_PROGRESS,
    CLOSED,
    CANCELLED
}
