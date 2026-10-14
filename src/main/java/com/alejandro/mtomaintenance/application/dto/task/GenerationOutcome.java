package com.alejandro.mtomaintenance.application.dto.task;

/** Lo que pasa con cada activo que encaja en lo que se pide generar. */
public enum GenerationOutcome {

    /** Se le crea su tarea. */
    CREATE,

    /** Ya tiene una tarea en esta orden, tambien si se cancelo. */
    ALREADY_IN_ORDER,

    /** Es de otro EP que la orden: va en otra orden. */
    OTHER_PACKAGE,

    /** Tiene una tarea abierta en otra orden preventiva que no ha terminado. */
    IN_OTHER_ORDER,

    /** El plan no le da tipo de tarea (un seccionador sin motor) y no se eligio ninguno. */
    NO_TASK_TYPES
}
