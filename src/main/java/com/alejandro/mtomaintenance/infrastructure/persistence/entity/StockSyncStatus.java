package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

/** Punto en que esta la conversacion con mto-stock para una linea de material. */
public enum StockSyncStatus {
    NOT_REQUESTED,
    RESERVED,
    CONSUMED,
    RELEASED,
    FAILED
}
