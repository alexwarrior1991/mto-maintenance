package com.alejandro.mtomaintenance.infrastructure.web.controller;

/** Prefijo interno de la API; el gateway la publica como /api/maintenance. */
public final class MaintenanceApiPaths {

    public static final String BASE = "/api/v1/maintenance";

    /** Las operaciones de fondo: lo que se contesto con 202 ({@code Prefer: respond-async}). */
    public static final String OPERATIONS = BASE + "/operations";

    private MaintenanceApiPaths() {
    }
}
