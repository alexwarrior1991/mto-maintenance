package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

/** Grupos funcionales del plan OCS: las tareas de un grupo se ejecutan en paralelo por subequipos. */
public enum FunctionalGroup {
    STRUCTURAL_SUPPORTS,
    OVERHEAD_CONDUCTORS,
    DEVICES_AND_SWITCHES,
    ANCHORAGE_COMPONENTS,
    TURNOUTS_AND_SWITCHES,
    DIAGNOSTICS,
    NONE
}
