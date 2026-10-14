package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

/**
 * Cómo se acciona un seccionador, tal y como lo declara {@code mto-configuration} (V16). Decide su
 * tipo de tarea por defecto al generar un preventivo: RP-06 es el de los seccionadores con motor, y
 * el plan no tiene uno para los manuales.
 */
public enum DisconnectorDriveType {

    /** Con motor: se maniobra a distancia. */
    MOTOR,

    /** Sin motor: se maniobra a mano en el propio seccionador. */
    MANUAL
}
