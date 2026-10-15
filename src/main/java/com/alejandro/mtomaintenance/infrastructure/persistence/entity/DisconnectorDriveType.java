package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

/**
 * Cómo se acciona un seccionador, tal y como lo declara {@code mto-configuration} (V16). Decide uno de
 * sus tipos de tarea por defecto al generar un preventivo: RP-06, la prueba del accionamiento a
 * distancia, solo con motor; RP-07, el seccionador mismo, lo llevan todos.
 */
public enum DisconnectorDriveType {

    /** Con motor: se maniobra a distancia. */
    MOTOR,

    /** Sin motor: se maniobra a mano en el propio seccionador. */
    MANUAL
}
