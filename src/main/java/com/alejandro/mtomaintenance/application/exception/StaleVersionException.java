package com.alejandro.mtomaintenance.application.exception;

/**
 * La peticion trae la version que leyo y la fila ya va por otra: alguien escribio entre medias.
 * Responde 409 {@code CON-001}, como el bloqueo optimista de mto-configuration, sin escribir nada;
 * quien llama relee y decide.
 */
public class StaleVersionException extends BusinessException {

    public StaleVersionException(String message) {
        super(message);
    }

    /** Si la peticion trae version y no es la de la fila; sin version en la peticion no se comprueba nada. */
    public static void check(String what, Long current, Long requested) {
        if (requested != null && !requested.equals(current)) {
            throw new StaleVersionException(what + " was changed by someone else: it is at version " + current
                    + " and the request was made on version " + requested + "; read it again");
        }
    }
}
