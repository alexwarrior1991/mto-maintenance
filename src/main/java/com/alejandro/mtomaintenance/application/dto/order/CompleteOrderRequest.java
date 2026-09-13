package com.alejandro.mtomaintenance.application.dto.order;

/** force (supervise) permite cerrar con lineas de material sin sincronizar con stock. */
public record CompleteOrderRequest(String closingNotes, Boolean force, String comment) {
    public boolean isForced() {
        return Boolean.TRUE.equals(force);
    }
}
