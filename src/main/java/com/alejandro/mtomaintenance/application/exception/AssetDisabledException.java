package com.alejandro.mtomaintenance.application.exception;

/** El activo esta desactivado o no admite la operacion (por ejemplo, editar la identidad de un activo que viene de datos maestros). Responde 409. */
public class AssetDisabledException extends BusinessException {

    public AssetDisabledException(String message) {
        super(message);
    }
}
