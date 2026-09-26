package com.alejandro.mtomaintenance.application.exception;

/**
 * mto-stock ha respondido y ha dicho que no: sin existencias (409 {@code STK-001}), un material,
 * almacen o proyecto que no existe o esta retirado, una reserva que ya no esta activa... Repetir la
 * misma llamada daria lo mismo, asi que no es una caida: no cuenta para el circuito 'stock', la
 * linea de material queda {@code REJECTED} con el motivo y, si alguien pidio sincronizar, la
 * respuesta es 409 (sin existencias) o 422, no 503.
 *
 * <p>Lleva el estado HTTP y el codigo de error de stock tal como llegaron. El codigo de stock no se
 * devuelve como codigo de este servicio porque los prefijos chocan ({@code MAT-404} es alli un
 * material y aqui una linea de material): va en el mensaje.</p>
 */
public class StockRejectedException extends BusinessException {

    /** El codigo con el que mto-stock dice que no hay existencias disponibles. */
    public static final String INSUFFICIENT_STOCK = "STK-001";

    /** El codigo con el que mto-stock dice que la clave de idempotencia ya se uso con otro cuerpo. */
    public static final String IDEMPOTENCY_CONFLICT = "IDEM-001";

    private final int status;
    private final String stockErrorCode;

    public StockRejectedException(String message, int status, String stockErrorCode, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.stockErrorCode = stockErrorCode;
    }

    /** Estado HTTP con el que respondio mto-stock. */
    public int getStatus() {
        return status;
    }

    /** Codigo de error de mto-stock ({@code STK-001}, {@code RES-001}, {@code WH-001}...), o null si la respuesta no lo traia. */
    public String getStockErrorCode() {
        return stockErrorCode;
    }

    public boolean isInsufficientStock() {
        return INSUFFICIENT_STOCK.equals(stockErrorCode);
    }

    /**
     * La clave ya se habia usado con otro cuerpo: lo que se pidio con ella si llego a stock, aunque no
     * fuera esto. No deberia pasar, porque lo que viaja no cambia mientras la peticion esta en duda.
     */
    public boolean isIdempotencyConflict() {
        return IDEMPOTENCY_CONFLICT.equals(stockErrorCode);
    }
}
