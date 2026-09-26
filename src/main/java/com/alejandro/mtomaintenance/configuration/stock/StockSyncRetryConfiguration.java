package com.alejandro.mtomaintenance.configuration.stock;

import com.alejandro.mtomaintenance.application.service.StockSyncRetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Reintenta las lineas de material FAILED cada {@code app.stock.sync-retry.interval} (5 minutos).
 *
 * <p>Es lo que hace que una reserva o una salida en duda se resuelva mientras mto-stock todavia
 * recuerda su clave de idempotencia ({@code app.idempotency.retention} alli, 30 dias). Con
 * {@code app.stock.enabled=false} no hay a quien reintentar y no existe; los tests la apagan asi, y
 * prueban el reintento llamandolo. {@code app.stock.sync-retry.enabled=false} la apaga con stock
 * encendido, y entonces las lineas FAILED vuelven a esperar a que alguien sincronice.</p>
 *
 * <p>Con varias instancias, todas reintentan sin coordinarse: dos que sincronizan la misma linea
 * mandan la misma clave, stock aplica la peticion una vez, y la version de la linea deja guardar solo
 * a una.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.stock", name = {"enabled", "sync-retry.enabled"}, havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class StockSyncRetryConfiguration {

    private final StockSyncRetryService stockSyncRetryService;

    @Scheduled(fixedDelayString = "${app.stock.sync-retry.interval:PT5M}", initialDelayString = "${app.stock.sync-retry.initial-delay:PT1M}")
    public void retryFailedLines() {
        stockSyncRetryService.retryFailedLines();
    }
}
