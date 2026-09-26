package com.alejandro.mtomaintenance.application.exception;

/**
 * mto-stock ya no tiene activa la reserva que se queria liberar: la liberaron, la cancelaron o la
 * consumieron desde Almacen (404 si no existe, 422 si existe pero ya no esta activa).
 *
 * <p>Para quien no la distinga sigue siendo un {@link StockUnavailableException}. Quitar una linea
 * de material la trata aparte, porque esa reserva ya no retiene nada y la linea se puede borrar. Un
 * stock caido, o una cuenta de servicio sin permiso, siguen siendo indisponibilidad: borrar la linea
 * dejaria viva su reserva.</p>
 */
public class StockReservationNotActiveException extends StockUnavailableException {

    public StockReservationNotActiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
