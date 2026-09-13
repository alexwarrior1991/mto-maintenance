package com.alejandro.mtomaintenance.application.exception;

/** Regla de turno incumplida: ventana de posesion incompatible con la tarea o la via, turno de otra via, turno no en curso. Responde 409. */
public class ShiftException extends BusinessException {

    public ShiftException(String message) {
        super(message);
    }
}
