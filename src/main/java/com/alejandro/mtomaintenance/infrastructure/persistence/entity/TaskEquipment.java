package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

/**
 * El equipo que un tipo de tarea puede exigir al turno en el que se hace ({@code V24}).
 *
 * <ul>
 *   <li>{@link #INSULATED_PLATFORM}: un vehiculo del turno cuyo modelo tiene la capacidad
 *       {@code INSULATED_PLATFORM} de mto-resources.</li>
 *   <li>{@link #EARTHING}: un vehiculo con la capacidad {@code EARTHING}, o una pertiga de puesta a tierra
 *       ({@code EARTHING_POLE}) entre los instrumentos del turno.</li>
 * </ul>
 */
public enum TaskEquipment {
    INSULATED_PLATFORM,
    EARTHING
}
