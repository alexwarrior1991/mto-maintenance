package com.alejandro.mtomaintenance.application.exception;

/**
 * Hay tantas operaciones de fondo en marcha como admite el despliegue. Responde 429 {@code OPS-429}
 * con {@code Retry-After}: lo que se agota no es el hilo, que es virtual, sino la conexion que cada
 * operacion retiene mientras trabaja.
 */
public class TooManyOperationsException extends BusinessException {

    private final long retryAfterSeconds;

    public TooManyOperationsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
