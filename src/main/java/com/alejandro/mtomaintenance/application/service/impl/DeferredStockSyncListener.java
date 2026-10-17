package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.service.StockSyncRetryService;
import com.alejandro.mtomaintenance.configuration.async.AsyncConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * El reintento inmediato de las lineas que una transicion dejo sin respuesta de stock
 * ({@link StockLinesDeferred}): en cuanto la transaccion confirma, en un hilo virtual, en vez de esperar
 * a la vuelta de {@code app.stock.sync-retry} (cinco minutos).
 *
 * <p>Antes espera a que termine la llamada que cada linea tuviera en el aire, con un tope: el reintento
 * repite lo que siga en duda con la misma clave, pero una liberacion o un consumo que llegaran a la vez
 * que su repeticion los veria stock dos veces. Si alguna sigue sin terminar, el reintento sale igual y
 * lo que no se resuelva queda para la vuelta periodica.</p>
 */
@Component
class DeferredStockSyncListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeferredStockSyncListener.class);

    /** Mas que el {@code read-timeout} de stock (10 s): pasado esto, la llamada en el aire ya ha fallado. */
    static final Duration IN_FLIGHT_WAIT = Duration.ofSeconds(30);

    private final StockSyncRetryService retryService;
    private final Executor executor;

    DeferredStockSyncListener(StockSyncRetryService retryService, @Qualifier(AsyncConfiguration.TASK_EXECUTOR) Executor executor) {
        this.retryService = retryService;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void retryAfterCommit(StockLinesDeferred deferred) {
        executor.execute(() -> retry(deferred));
    }

    void retry(StockLinesDeferred deferred) {
        try {
            CompletableFuture.allOf(deferred.inFlight().toArray(CompletableFuture[]::new))
                    .get(IN_FLIGHT_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException stillThere) {
            LOGGER.info("A stock call of order {} is still in the air after {}; retrying its lines anyway", deferred.orderId(), IN_FLIGHT_WAIT);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        int settled = retryService.retryLines(deferred.orderId(), deferred.lineIds());
        LOGGER.info("Immediate stock retry of order {}: {} of {} deferred material line(s) settled", deferred.orderId(), settled,
                deferred.lineIds().size());
    }
}
