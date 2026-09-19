package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

/**
 * Cómo está puesto un aislador de sección sobre la vía, tal y como lo declara
 * {@code mto-configuration}.
 */
public enum SectionInsulatorInstallation {

    /** Separa las catenarias de dos vías que conectan por una aguja. Es el caso normal. */
    TRACK_CONNECTION,

    /** Está en medio de una sola vía, partiendo su catenaria en dos secciones de alimentación. */
    IN_TRACK
}
