package com.alejandro.mtomaintenance.configuration.security;

/**
 * Roles de cliente de Keycloak que comprueba esta API, ya normalizados a autoridad de Spring.
 *
 * <p>Son los nombres que declara {@code keycloak/mto-maintenance-partial-import.json} en
 * mayusculas y con guion bajo: {@code maintenance-read} llega como {@code ROLE_MAINTENANCE_READ}.
 * Un rol que se anade aqui sin anadirlo alli no lo tiene nadie y todo responde 403.</p>
 */
public final class SecurityRoles {

    private SecurityRoles() {
    }

    /** Consulta de activos, ordenes, turnos, inspecciones, defectos e informes. */
    public static final String MAINTENANCE_READ = "MAINTENANCE_READ";

    /**
     * Trabajo diario: alta y modificacion de ordenes, tareas, turnos, inspecciones, defectos y
     * materiales, y las transiciones ordinarias (planificar, asignar, iniciar, completar).
     */
    public static final String MAINTENANCE_WRITE = "MAINTENANCE_WRITE";

    /** Desactivacion de activos y equipos. */
    public static final String MAINTENANCE_DELETE = "MAINTENANCE_DELETE";

    /**
     * Supervision: cancelar ordenes, cerrar con materiales sin sincronizar, resolver, cerrar y
     * descartar defectos. Se pide ADEMAS de write: son las escrituras que anulan o cierran sin
     * documento con el que contrastar.
     */
    public static final String MAINTENANCE_SUPERVISE = "MAINTENANCE_SUPERVISE";

    /** Lectura de los endpoints de Actuator. */
    public static final String OPS_METRICS = "OPS_METRICS";

    /** Operaciones de Actuator que modifican estado. */
    public static final String OPS_WRITE = "OPS_WRITE";
}
