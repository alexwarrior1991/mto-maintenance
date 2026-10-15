package com.alejandro.mtomaintenance.application.dto.material;

import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockRequestType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una linea de material de una orden y en que punto esta su conversacion con mto-stock.
 *
 * <p>Ejemplo, un elemento de {@code GET /orders/{id}/materials}:</p>
 * <pre>{@code
 * {"materialId": "…(DROPPER-STD)", "materialCode": "DROPPER-STD", "warehouseId": "…(Rishpon)", "plannedQuantity": 6.000000,
 *  "consumedQuantity": 0.000000, "unit": "UNIT", "stockReservationId": "…(reserva en mto-stock)", "stockSyncStatus": "RESERVED",
 *  "stockSyncError": null, "stockRequestInDoubt": null, "version": 1, …}
 * }</pre>
 */
public record MaterialUsageResponse(
        UUID id,
        UUID orderId,
        UUID taskId,
        UUID materialId,
        String materialCode,
        String materialDescriptionSnapshot,
        UUID warehouseId,
        BigDecimal plannedQuantity,
        BigDecimal consumedQuantity,
        String unit,
        Boolean allowOverConsumption,
        UUID stockReservationId,
        StockSyncStatus stockSyncStatus,
        String stockSyncError,
        StockRequestType stockRequestInDoubt,
        Long version,
        AuditMetadataResponse audit
) {
}
