package com.alejandro.mtomaintenance.application.exception;

/**
 * Fuera del ambito de quien escribe ({@code MaintenanceScope}): un jefe de equipo, un supervisor o un jefe
 * de supervisores solo escribe en lo de los EP de sus equipos. Responde 403 {@code SCP-403}, distinto del
 * 403 de un permiso que falta: tiene el rol, pero no sobre eso.
 */
public class ScopeException extends BusinessException {

    public ScopeException(String message) {
        super(message);
    }
}
