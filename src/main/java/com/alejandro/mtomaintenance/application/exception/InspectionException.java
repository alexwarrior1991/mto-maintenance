package com.alejandro.mtomaintenance.application.exception;

/** Regla de inspeccion incumplida: resultado incoherente con los items, defecto no generable con ese resultado. Responde 422. */
public class InspectionException extends BusinessException {

    public InspectionException(String message) {
        super(message);
    }
}
