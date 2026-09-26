package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

/**
 * Punto en que esta la conversacion con mto-stock para una linea de material.
 *
 * <p>{@code FAILED} y {@code REJECTED} son las dos maneras de quedarse a medias: stock no respondio
 * (reintentar tiene sentido en cuanto vuelva) o respondio que no (sin existencias, un material
 * retirado...: hay que cambiar algo antes). El motivo queda en {@code stockSyncError}.</p>
 */
public enum StockSyncStatus {
    NOT_REQUESTED,
    RESERVED,
    CONSUMED,
    RELEASED,
    FAILED,
    REJECTED
}
