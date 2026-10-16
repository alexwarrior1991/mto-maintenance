package com.alejandro.mtomaintenance.application.exception;

/**
 * El equipo es de mto-resources (sincronizado): su nombre, su jefe, sus linemen, sus EP y su vehiculo
 * se cambian alli y llegan por evento. Aqui no se edita. Responde 409 {@code TEA-001}.
 */
public class SynchronizedTeamException extends BusinessException {

    public SynchronizedTeamException(String message) {
        super(message);
    }
}
