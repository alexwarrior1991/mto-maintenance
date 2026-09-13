package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

/** URGENT es el correctivo de emergencia: nace CRITICAL y salta de DRAFT a IN_PROGRESS sin planificar. */
public enum MaintenanceOrderType {
    PREVENTIVE,
    CORRECTIVE,
    INSPECTION,
    URGENT
}
