package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageResponse;
import com.alejandro.mtomaintenance.application.exception.StockRejectedException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.service.MaintenanceMaterialUsageService;
import com.alejandro.mtomaintenance.application.service.StockSyncRetryService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceMaterialUsageRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceMaterialUsageRepository.LineRef;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sin transaccion a proposito: cada linea se sincroniza con {@link MaintenanceMaterialUsageService#sync},
 * en la suya, y lo que pase con una (stock dice que no, alguien la quito o la cambio mientras tanto) no
 * deshace lo de las demas.
 */
@Service
@RequiredArgsConstructor
class StockSyncRetryServiceImpl implements StockSyncRetryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockSyncRetryServiceImpl.class);

    private final MaintenanceMaterialUsageRepository repository;
    private final MaintenanceMaterialUsageService materialUsageService;

    /**
     * La ultima linea intentada: la vuelta siguiente empieza despues de ella. Si una linea falla siempre
     * (un 5xx de stock con ese material), la vuelta se para en ella, y sin turnos las de detras no se
     * intentarian nunca.
     */
    private final AtomicReference<UUID> lastAttempted = new AtomicReference<>();

    @Override
    public int retryFailedLines() {
        List<LineRef> failed = new ArrayList<>(repository.findRefsByStockSyncStatus(StockSyncStatus.FAILED));
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
