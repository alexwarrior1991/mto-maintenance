package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.stock.StockReservation;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.service.StockClient;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Conversacion con mto-stock por linea de material. Un fallo de stock nunca tumba la operacion de
 * negocio: la linea queda en FAILED con el error y se reintenta con /sync. Solo {@link #syncNow}
 * deja pasar la excepcion, porque ahi el cliente ha pedido explicitamente hablar con stock.
 *
 * <p>Reserva completa al planificar; al completar, la reserva se consume si lo consumido iguala lo
 * previsto, se libera y se da salida directa de lo consumido si es menor, y se consume mas una salida
 * del exceso si es mayor (mto-stock no admite consumo parcial de una reserva).</p>
 */
@Component
@RequiredArgsConstructor
class MaterialStockSynchronizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaterialStockSynchronizer.class);

    static final String PROJECT_CODE_PREFIX = "EP-";

    private final StockClient stockClient;

    boolean isEnabled() {
        return stockClient.isEnabled();
    }

    /** Proyecto de stock del paquete de ejecucion de la orden, si existe alli. */
    Optional<UUID> resolveProjectId(MaintenanceOrder order) {
        if (order.getStockProjectId() != null) {
            return Optional.of(order.getStockProjectId());
        }
        if (!stockClient.isEnabled() || order.getExecutionPackageId() == null) {
            return Optional.empty();
        }
        try {
            return stockClient.findProjectIdByCode(PROJECT_CODE_PREFIX + order.getExecutionPackageId());
        } catch (StockUnavailableException exception) {
            LOGGER.warn("Could not resolve the stock project of order {}: {}", order.getCode(), exception.getMessage());
            return Optional.empty();
        }
    }

    /** Reserva si hay stock, proyecto y cantidad; sin proyecto no hay reserva posible y la linea sigue NOT_REQUESTED. */
    void reserve(MaintenanceMaterialUsage usage) {
        MaintenanceOrder order = usage.getOrder();
        if (!stockClient.isEnabled() || order.getStockProjectId() == null || usage.getPlannedQuantity().signum() == 0) {
            return;
        }
        if (usage.getStockSyncStatus() == StockSyncStatus.RESERVED || usage.getStockSyncStatus() == StockSyncStatus.CONSUMED) {
            return;
        }
        try {
            StockReservation reservation = stockClient.reserve(
                    usage.getMaterialId(), usage.getWarehouseId(), order.getStockProjectId(), usage.getPlannedQuantity());
            usage.markReserved(reservation.id());
            LOGGER.info("Material reserved in stock: order={}, material={}, reservation={}", order.getCode(), usage.getMaterialCode(), reservation.id());
        } catch (StockUnavailableException exception) {
            usage.markFailed("reserve: " + exception.getMessage());
            LOGGER.warn("Material reservation failed, line left as FAILED: order={}, material={}, cause={}",
                    order.getCode(), usage.getMaterialCode(), exception.getMessage());
        }
    }

    /** Consumo al completar la orden. */
    void consume(MaintenanceMaterialUsage usage) {
        MaintenanceOrder order = usage.getOrder();
        if (!stockClient.isEnabled() || usage.getStockSyncStatus() == StockSyncStatus.CONSUMED) {
            return;
        }
        BigDecimal consumed = usage.getConsumedQuantity();
        try {
            if (usage.getStockSyncStatus() == StockSyncStatus.RESERVED && usage.getStockReservationId() != null) {
                int comparison = consumed.compareTo(usage.getPlannedQuantity());
                if (consumed.signum() == 0) {
                    stockClient.release(usage.getStockReservationId());
                    usage.markReleased();
                    return;
                }
                if (comparison == 0) {
                    stockClient.consume(usage.getStockReservationId());
                } else if (comparison < 0) {
                    stockClient.release(usage.getStockReservationId());
                    stockClient.output(usage.getMaterialId(), usage.getWarehouseId(), order.getStockProjectId(), consumed,
                            order.getCode(), "Partial consumption of reservation " + usage.getStockReservationId());
                } else {
                    stockClient.consume(usage.getStockReservationId());
                    stockClient.output(usage.getMaterialId(), usage.getWarehouseId(), order.getStockProjectId(),
                            consumed.subtract(usage.getPlannedQuantity()), order.getCode(),
                            "Over-consumption beyond reservation " + usage.getStockReservationId());
                }
            } else if (consumed.signum() > 0) {
                stockClient.output(usage.getMaterialId(), usage.getWarehouseId(), order.getStockProjectId(), consumed,
                        order.getCode(), "Maintenance order " + order.getCode());
            } else {
                return;
            }
            usage.markConsumed();
        } catch (StockUnavailableException exception) {
            usage.markFailed("consume: " + exception.getMessage());
            LOGGER.warn("Material consumption failed, line left as FAILED: order={}, material={}, cause={}",
                    order.getCode(), usage.getMaterialCode(), exception.getMessage());
        }
    }

    /** Liberacion al cancelar la orden. */
    void release(MaintenanceMaterialUsage usage) {
        if (!stockClient.isEnabled() || usage.getStockSyncStatus() != StockSyncStatus.RESERVED || usage.getStockReservationId() == null) {
            return;
        }
        try {
            stockClient.release(usage.getStockReservationId());
            usage.markReleased();
        } catch (StockUnavailableException exception) {
            usage.markFailed("release: " + exception.getMessage());
            LOGGER.warn("Material release failed, line left as FAILED: order={}, material={}, cause={}",
                    usage.getOrder().getCode(), usage.getMaterialCode(), exception.getMessage());
        }
    }

    /**
     * Reintento explicito: rehace el paso que toque segun el estado de la orden. A diferencia del
     * resto, deja pasar la excepcion: quien pide sincronizar quiere saber si stock sigue caido.
     */
    void syncNow(MaintenanceMaterialUsage usage) {
        if (!stockClient.isEnabled()) {
            throw new StockUnavailableException("The stock client is disabled (app.stock.enabled=false)");
        }
        MaintenanceOrder order = usage.getOrder();
        if (order.getStockProjectId() == null) {
            resolveProjectId(order).ifPresent(order::setStockProjectId);
        }
        switch (order.getStatus()) {
            case COMPLETED -> consume(usage);
            case CANCELLED -> release(usage);
            case DRAFT -> {
            }
            default -> reserve(usage);
        }
        if (usage.isSyncFailed()) {
            throw new StockUnavailableException("Stock synchronization still failing for material "
                    + usage.getMaterialCode() + ": " + usage.getStockSyncError());
        }
    }
}
