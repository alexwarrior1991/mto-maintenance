package com.alejandro.mtomaintenance.application.exception;

/**
 * mto-stock no ha respondido o ha rechazado la operacion. Responde 503 solo cuando el cliente ha
 * pedido explicitamente sincronizar; en el resto de flujos la linea de material queda en FAILED y la
 * operacion de negocio sigue adelante.
 */
public class StockUnavailableException extends BusinessException {

    public StockUnavailableException(String message) {
        super(message);
    }

    public StockUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
