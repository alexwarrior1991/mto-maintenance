package com.alejandro.mtomaintenance.configuration.stock;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Como habla con mto-stock una transicion de orden con varias lineas de material
 * ({@code app.stock.sync.*}). Existe aunque stock este apagado: con el cliente NoOp no se usa.
 *
 * @param parallelism    lineas que conversan con stock a la vez, cada una en un hilo virtual. Cada
 *                       conversacion no retiene conexion (no toca la base), pero si una del pool HTTP
 *                       y una llamada de stock: no se sube sin mirar a mto-stock
 * @param deadline       el plazo de toda la conversacion de una transicion (planificar, completar,
 *                       cancelar), contado desde antes de resolver el proyecto de stock de la orden. La
 *                       linea que no ha terminado queda FAILED, con lo que tuviera en duda, y la orden
 *                       sigue sin esperar a un stock lento. Por debajo del read-timeout del gateway
 *                       (15 s), con margen para la transaccion: si no, el gateway responde 504 mientras
 *                       la transicion confirma
 * @param immediateRetry al confirmar, reintentar en ese momento las lineas que se quedaron sin
 *                       respuesta, en vez de esperar a la vuelta de {@code app.stock.sync-retry}
 */
@ConfigurationProperties(prefix = "app.stock.sync")
public record StockSyncProperties(int parallelism, Duration deadline, Boolean immediateRetry) {

    public StockSyncProperties {
        parallelism = parallelism <= 0 ? 4 : parallelism;
        deadline = deadline == null ? Duration.ofSeconds(10) : deadline;
        immediateRetry = immediateRetry == null || immediateRetry;
    }
}
