package com.alejandro.mtomaintenance.application.dto.stock;

import java.math.BigDecimal;
import java.util.UUID;

public record StockReservation(UUID id, UUID materialId, UUID warehouseId, UUID projectId, BigDecimal quantity, String status) {
}
