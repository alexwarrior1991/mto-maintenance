package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageResponse;
import com.alejandro.mtomaintenance.application.exception.StockRejectedException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.service.MaintenanceMaterialUsageService;
import com.alejandro.mtomaintenance.application.service.StockSyncRetryService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceMaterialUsageRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceMaterialUsageRepository.LineRef;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.SessionAdvisoryLock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sin transaccion a proposito: cada linea se sincroniza con {@link MaintenanceMaterialUsageService#sync},
 * en la suya, y lo que pase con una (stock dice que no, alguien la quito o la cambio mientras tanto) no
 * deshace lo de las demas.
 *
 * <p>La vuelta periodica es una sola en todo el despliegue: corre bajo un cerrojo de aviso de sesion
 * ({@link SessionAdvisoryLock}), y la instancia que no lo consigue no hace nada esa vez. Sin el, todas
 * las replicas reintentaban las mismas lineas a la vez: la clave de idempotencia y la version de la
 * linea impedian el doble efecto, pero stock recibia la misma llamada una vez por replica y las que
 * perdian dejaban un aviso de version vieja. El reintento diferido de una orden ({@link #retryLines})
 * no lo toma: son las lineas que acaba de dejar en el aire esta misma instancia.</p>
 */
@Service
@RequiredArgsConstructor
class StockSyncRetryServiceImpl implements StockSyncRetryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockSyncRetryServiceImpl.class);

    /** Clave del cerrojo de aviso, propia de esta vuelta; cualquier otro trabajo usa otra. */
    static final long LOCK_KEY = AdvisoryLockKeys.of("mto-maintenance:stock-sync-retry");

    /**
     * Lo que una linea FAILED tiene que llevar sin cambiar para que la vuelta la coja. Una transicion
     * que corta una linea por su plazo la deja FAILED con su llamada a stock quiza aun en el aire (como
     * mucho el read-timeout de stock, 10 s), y su reintento diferido la espera hasta 30 s antes de
     * repetirla. Si la vuelta la cogiera entre medias, repetiria un consumo o una liberacion que sigue
     * en curso, stock rechazaria la segunda y la linea quedaria REJECTED sin serlo. Un minuto los cubre;
     * lo que se salta lo coge la vuelta siguiente.
     */
    static final Duration QUIET_PERIOD = Duration.ofMinutes(1);

    private final MaintenanceMaterialUsageRepository repository;
    private final MaintenanceMaterialUsageService materialUsageService;
    private final SessionAdvisoryLock lock;

    /**
     * La ultima linea intentada: la vuelta siguiente empieza despues de ella. Si una linea falla siempre
     * (un 5xx de stock con ese material), la vuelta se para en ella, y sin turnos las de detras no se
     * intentarian nunca. Es de cada instancia: la que gana el cerrojo sigue desde la suya, y como la
     * vuelta las recorre todas en circulo, ninguna se queda atras por cambiar de instancia.
     */
    private final AtomicReference<UUID> lastAttempted = new AtomicReference<>();

    @Override
    public int retryFailedLines() {
        int[] synchronizedLines = {0};
        if (!lock.runIfFree(LOCK_KEY, () -> synchronizedLines[0] = retryFailedLinesHoldingTheLock())) {
            LOGGER.debug("Stock retry skipped: another instance is running it");
        }
        return synchronizedLines[0];
    }

    private int retryFailedLinesHoldingTheLock() {
        List<LineRef> failed = new ArrayList<>(repository.findRefsByStockSyncStatusUnchangedSince(StockSyncStatus.FAILED,
                Instant.now().minus(QUIET_PERIOD)));
        if (failed.isEmpty()) {
            return 0;
        }
        failed.sort(Comparator.comparing(LineRef::getId));
        int start = firstAfter(failed, lastAttempted.get());
        int synchronizedLines = 0;
        for (int offset = 0; offset < failed.size(); offset++) {
            LineRef line = failed.get((start + offset) % failed.size());
            lastAttempted.set(line.getId());
            try {
                MaterialUsageResponse response = materialUsageService.sync(line.getOrderId(), line.getId());
                if (response.stockSyncStatus() != StockSyncStatus.FAILED) {
                    synchronizedLines++;
                }
            } catch (StockUnavailableException unavailable) {
                // Stock sigue sin contestar: el resto fallaria igual. La vuelta siguiente sigue desde aqui.
                LOGGER.info("mto-stock still does not answer; {} material line(s) left FAILED until the next retry: {}",
                        failed.size() - offset, unavailable.getMessage());
                break;
            } catch (StockRejectedException rejected) {
                // La linea queda REJECTED con el motivo: ya no es cosa de reintentar.
                LOGGER.info("Stock rejected material line {} of order {} on retry: {}", line.getId(), line.getOrderId(), rejected.getMessage());
            } catch (RuntimeException other) {
                // Quitada o cambiada mientras tanto (404, version vieja...): se sigue con las demas.
                LOGGER.warn("Material line {} of order {} could not be retried: {}", line.getId(), line.getOrderId(), other.toString());
            }
        }
        if (synchronizedLines > 0) {
            LOGGER.info("Stock retry synchronized {} of {} FAILED material line(s)", synchronizedLines, failed.size());
        }
        return synchronizedLines;
    }

    @Override
    public int retryLines(UUID orderId, Collection<UUID> lineIds) {
        int synchronizedLines = 0;
        int attempted = 0;
        for (UUID lineId : lineIds) {
            attempted++;
            try {
                if (materialUsageService.sync(orderId, lineId).stockSyncStatus() != StockSyncStatus.FAILED) {
                    synchronizedLines++;
                }
            } catch (StockUnavailableException unavailable) {
                LOGGER.info("mto-stock still does not answer for order {}; {} deferred material line(s) left to the periodic retry: {}",
                        orderId, lineIds.size() - attempted + 1, unavailable.getMessage());
                break;
            } catch (StockRejectedException rejected) {
                LOGGER.info("Stock rejected deferred material line {} of order {}: {}", lineId, orderId, rejected.getMessage());
            } catch (RuntimeException other) {
                LOGGER.warn("Deferred material line {} of order {} could not be retried: {}", lineId, orderId, other.toString());
            }
        }
        return synchronizedLines;
    }

    /** La posicion de la primera linea despues de la ultima intentada, o 0 para empezar por el principio. */
    private static int firstAfter(List<LineRef> lines, UUID last) {
        if (last != null) {
            for (int index = 0; index < lines.size(); index++) {
                if (lines.get(index).getId().compareTo(last) > 0) {
                    return index;
                }
            }
        }
        return 0;
    }
}
