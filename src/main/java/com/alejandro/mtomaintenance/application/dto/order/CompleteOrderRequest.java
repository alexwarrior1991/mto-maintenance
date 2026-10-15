package com.alejandro.mtomaintenance.application.dto.order;

/**
 * force (supervise) permite cerrar con lineas de material sin sincronizar con stock.
 *
 * <p>Ejemplo, {@code POST /orders/{id}/complete}:</p>
 * <pre>{@code
 * {"closingNotes": "Perfiles 12-2.27 a 14-2.02 revisados", "force": false, "comment": "Turno SH-000001 cerrado"}
 * }</pre>
 */
public record CompleteOrderRequest(String closingNotes, Boolean force, String comment) {
    public boolean isForced() {
        return Boolean.TRUE.equals(force);
    }
}
