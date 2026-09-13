package com.alejandro.mtomaintenance.application.exception;

/** Transicion de estado no permitida desde el estado actual de una orden, tarea, turno o defecto. Responde 409. */
public class InvalidTransitionException extends BusinessException {

    public InvalidTransitionException(String message) {
        super(message);
    }
}
