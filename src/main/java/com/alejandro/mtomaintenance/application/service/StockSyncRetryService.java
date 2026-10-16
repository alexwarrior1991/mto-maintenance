package com.alejandro.mtomaintenance.application.service;

import java.util.Collection;
import java.util.UUID;

/**
 * Reintento automatico de las lineas de material que stock no llego a contestar (FAILED).
 *
 * <p>Una linea FAILED esperaba a que alguien pulsara sincronizar, y si tenia una reserva o una salida
 * en duda, esa peticion podia quedarse asi semanas. mto-stock solo recuerda una clave de idempotencia
 * durante un plazo ({@code app.idempotency.retention} alli, 30 dias): pasado, repetirla ya no la
 * reconoce y la aplicaria otra vez. Reintentando solas en cuanto stock responde, las peticiones en
 * duda se resuelven en minutos y no en semanas.</p>
 *
 * <p>Solo las FAILED: una REJECTED es que stock dijo que no, y repetir lo mismo daria lo mismo hasta
 * que alguien cambie algo.</p>
 */
public interface StockSyncRetryService {

    /**
     * Una vuelta: sincroniza las lineas FAILED, por turnos entre vueltas, y se para en la primera a la
     * que stock sigue sin contestar. Cada linea va en su propia transaccion, como un /sync.
     *
     * @return cuantas han dejado de estar FAILED
     */
    int retryFailedLines();

    /**
     * Las lineas dadas de una orden, ahora: las que su transicion dejo sin respuesta de stock en el plazo
     * ({@code app.stock.sync.deadline}). Como una vuelta, cada una en su transaccion, y se para en la
     * primera a la que stock sigue sin contestar; lo que quede FAILED lo recoge la vuelta periodica.
     *
     * @return cuantas han dejado de estar FAILED
     */
    int retryLines(UUID orderId, Collection<UUID> lineIds);
}
