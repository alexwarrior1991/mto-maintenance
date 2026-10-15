package com.alejandro.mtomaintenance.application.dto.stock;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una reserva de mto-stock tal como la tiene stock. {@code status} viaja como texto para que un
 * estado nuevo alli no rompa la lectura aqui: quien la use decide que hacer con lo que no conoce.
 *
 * <p>Ejemplo, la reserva R-80 de 6 UNIT de DROPPER-STD para el proyecto EP-6, como la arma
 * {@code RestClientStockClient} a partir de la respuesta de {@code reserve} o de {@code findReservation}:</p>
 * <pre>{@code
 * new StockReservation(id, materialId, warehouseId, projectId, new BigDecimal("6"), "ACTIVE")
 * }</pre>
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
