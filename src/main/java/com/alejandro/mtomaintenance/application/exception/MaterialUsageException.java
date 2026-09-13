package com.alejandro.mtomaintenance.application.exception;

/** Regla de materiales incumplida: cantidad consumida por encima de la prevista sin permiso, linea duplicada o sincronizacion con stock pendiente. Responde 409. */
public class MaterialUsageException extends BusinessException {

    public MaterialUsageException(String message) {
        super(message);
    }
}
