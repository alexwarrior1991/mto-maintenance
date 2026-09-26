package com.alejandro.mtomaintenance.application.exception;

/**
 * mto-stock no ha respondido: red, tiempo agotado, un 5xx, el circuito abierto, o la cuenta de
 * servicio sin token o sin permiso (401/403). Reintentar tiene sentido en cuanto vuelva. Responde
 * 503 solo cuando el cliente ha pedido explicitamente hablar con stock (sincronizar una linea o
 * quitar una reservada); en el resto de flujos la linea de material queda FAILED y la operacion de
 * negocio sigue adelante. Que stock responda que no es {@link StockRejectedException}.
 */
public class StockUnavailableException extends BusinessException {

    public StockUnavailableException(String message) {
        super(message);
    }

    public StockUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
