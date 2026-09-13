package com.alejandro.mtomaintenance.application.dto.material;

import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;

import java.math.BigDecimal;
import java.util.UUID;

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
        AuditMetadataResponse audit
) {
}
