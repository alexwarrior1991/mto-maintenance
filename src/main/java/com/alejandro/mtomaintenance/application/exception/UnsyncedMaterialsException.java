package com.alejandro.mtomaintenance.application.exception;

/**
 * Completar la orden se rechaza porque alguna linea de material sigue sin sincronizar con stock
 * (FAILED o REJECTED). Es un {@link MaterialUsageException} (409 {@code MAT-001}) con una
 * diferencia: completar la lanza con {@code noRollbackFor}, porque para entonces stock ya ha
 * consumido o liberado las otras lineas y lo que cada una guarda de eso no se puede perder.
 */
public class UnsyncedMaterialsException extends MaterialUsageException {

    public UnsyncedMaterialsException(String message) {
        super(message);
    }
}
