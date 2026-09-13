package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.stock.StockMaterial;
import com.alejandro.mtomaintenance.application.dto.stock.StockReservation;
import com.alejandro.mtomaintenance.application.service.StockClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Cliente de stock apagado ({@code app.stock.enabled=false}): registra lo que habria hecho y no
 * reserva nada, de modo que las lineas de material quedan NOT_REQUESTED. Es lo que usan los tests
 * y un entorno sin mto-stock.
 */
@Component
@ConditionalOnProperty(prefix = "app.stock", name = "enabled", havingValue = "false", matchIfMissing = false)
class NoOpStockClient implements StockClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoOpStockClient.class);

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public Optional<StockMaterial> findMaterialById(UUID materialId) {
        LOGGER.debug("Stock client disabled: material lookup by id skipped ({})", materialId);
        return Optional.empty();
    }

    @Override
    public Optional<StockMaterial> findMaterialByCode(String code) {
        LOGGER.debug("Stock client disabled: material lookup by code skipped ({})", code);
        return Optional.empty();
    }

    @Override
    public Optional<UUID> findProjectIdByCode(String code) {
        LOGGER.debug("Stock client disabled: project lookup skipped ({})", code);
        return Optional.empty();
    }

    @Override
    public StockReservation reserve(UUID materialId, UUID warehouseId, UUID projectId, BigDecimal quantity) {
        throw new UnsupportedOperationException("stock client is disabled");
    }

    @Override
    public void consume(UUID reservationId) {
        throw new UnsupportedOperationException("stock client is disabled");
    }

    @Override
    public void release(UUID reservationId) {
        throw new UnsupportedOperationException("stock client is disabled");
    }

    @Override
    public void output(UUID materialId, UUID warehouseId, UUID projectId, BigDecimal quantity, String externalReference, String notes) {
        throw new UnsupportedOperationException("stock client is disabled");
    }
}
