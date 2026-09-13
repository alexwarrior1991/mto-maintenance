package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

/** Ciclo de vida de un defecto; las transiciones las decide DefectStateMachine. */
public enum DefectStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    DISCARDED
}
