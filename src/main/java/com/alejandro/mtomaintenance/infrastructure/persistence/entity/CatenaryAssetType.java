package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

/** Tipo de activo mantenible. PROFILE, DISCONNECTOR y SECTION_INSULATOR vienen de mto-configuration; TRACK_SECTION lo define el mantenimiento. */
public enum CatenaryAssetType {
    TRACK_SECTION,
    PROFILE,
    DISCONNECTOR,
    SECTION_INSULATOR
}
