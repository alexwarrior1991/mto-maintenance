package com.alejandro.mtomaintenance.application.dto.stock;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una reserva de mto-stock tal como la tiene stock. {@code status} viaja como texto para que un
 * estado nuevo alli no rompa la lectura aqui: quien la use decide que hacer con lo que no conoce.
 */
public record StockReservation(UUID id, UUID materialId, UUID warehouseId, UUID projectId, BigDecimal quantity, String status) {

    /** Solo una reserva activa retiene existencias y admite consumirse o liberarse. */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isConsumed() {
        return "CONSUMED".equals(status);
    }

    /** Liberada o cancelada: ya no retiene nada y nunca volvera a hacerlo. */
    public boolean holdsNothing() {
        return "RELEASED".equals(status) || "CANCELLED".equals(status);
    }
}
