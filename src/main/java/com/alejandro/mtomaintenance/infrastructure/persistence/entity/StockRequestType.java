package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

/**
 * Las dos peticiones de una linea de material que crean algo en mto-stock, y por eso llevan clave de
 * idempotencia: la reserva y la salida. Es lo que {@code stockRequestInDoubt} dice que se quedo sin
 * respuesta.
 */
public enum StockRequestType {
    RESERVATION,
    OUTPUT
}
