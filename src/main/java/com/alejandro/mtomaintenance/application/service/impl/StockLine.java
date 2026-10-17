package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockRequestType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Una linea de material tal como la conversacion con mto-stock la necesita, separada de la entidad.
 *
 * <p>La conversacion corre en un hilo virtual, fuera de la transaccion: no puede tocar la entidad JPA
 * (la sesion de Hibernate no es de ese hilo, y un proxy perezoso iria a la base desde el). Trabaja sobre
 * esta copia, hecha en el hilo de la transaccion, y es ese hilo quien la devuelve a la entidad
 * ({@link #applyTo}) y publica lo que haya que publicar.</p>
 *
 * <p>Si la conversacion no termina en su plazo, la transaccion sigue sin ella: {@link #cancel()} la
 * corta y devuelve lo que la linea sabe en ese momento, incluida la peticion que este en duda. Desde
 * entonces el hilo no manda nada mas a stock ({@link #beginRequest}, {@link #checkOpen}): lo unico que
 * puede seguir en el aire es la llamada que ya estaba haciendo, y esa queda contada en la entidad, o en
 * duda (una reserva o una salida, que llevan clave de idempotencia) o como lo que ya era (una
 * liberacion o un consumo, que el siguiente intento comprueba preguntando a stock por la reserva).</p>
 *
 * <p>El estado va bajo un cerrojo, y nunca con una llamada a stock dentro: lo que la transaccion lee al
 * cortarla es lo que el hilo habia escrito, ni a medias ni despues.</p>
 */
final class StockLine {

    /** La conversacion se corto por plazo: el hilo deja de hablar con stock. */
    static final class Cancelled extends RuntimeException {
        private Cancelled() {
            super("The stock conversation of this line was cut by its deadline", null, false, false);
        }
    }

    /** Lo que la linea cuenta de su conversacion con stock, en un instante. */
    record State(UUID reservationId, StockSyncStatus status, String error, StockRequestType inDoubt) {
    }

    private final ReentrantLock lock = new ReentrantLock();

    private final UUID id;
    private final UUID materialId;
    private final UUID warehouseId;
    private final String materialCode;
    private final String unit;
    private final BigDecimal plannedQuantity;
    private final BigDecimal consumedQuantity;
    private final String orderCode;
    private final UUID stockProjectId;

    private UUID reservationId;
    private StockSyncStatus status;
    private String error;
    private StockRequestType inDoubt;
    private boolean cancelled;

    private StockLine(MaintenanceMaterialUsage usage) {
        this.id = usage.getId();
        this.materialId = usage.getMaterialId();
        this.warehouseId = usage.getWarehouseId();
        this.materialCode = usage.getMaterialCode();
        this.unit = usage.getUnit();
        this.plannedQuantity = usage.getPlannedQuantity();
        this.consumedQuantity = usage.getConsumedQuantity();
        // La orden se lee aqui, en el hilo de la transaccion: si fuera un proxy, se inicializa ahora.
        this.orderCode = usage.getOrder().getCode();
        this.stockProjectId = usage.getOrder().getStockProjectId();
        this.reservationId = usage.getStockReservationId();
        this.status = usage.getStockSyncStatus();
        this.error = usage.getStockSyncError();
        this.inDoubt = usage.getStockRequestInDoubt();
    }

    /** La copia de la linea, en el hilo de la transaccion y despues de resolver el proyecto de la orden. */
    static StockLine of(MaintenanceMaterialUsage usage) {
        return new StockLine(usage);
    }

    /** Devuelve a la entidad lo que la conversacion dejo en la copia. Solo en el hilo de la transaccion. */
    void applyTo(MaintenanceMaterialUsage usage) {
        State state = state();
        usage.applyStockState(state.reservationId(), state.status(), state.error(), state.inDoubt());
    }

    /**
     * Corta la conversacion: el hilo no mandara nada mas a stock. Devuelve lo que la linea sabe ahora,
     * con la peticion en duda si la que estaba en el aire era una reserva o una salida.
     */
    State cancel() {
        lock.lock();
        try {
            cancelled = true;
            return new State(reservationId, status, error, inDoubt);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Justo antes de mandar una reserva o una salida: la apunta en duda, salvo que la conversacion ya
     * este cortada, y entonces no se manda. Las dos cosas a la vez, para que la transaccion que la corta
     * vea la peticion en duda o sepa que no saldra.
     */
    void beginRequest(StockRequestType request) {
        lock.lock();
        try {
            requireOpen();
            this.inDoubt = request;
        } finally {
            lock.unlock();
        }
    }

    /** Antes de cualquier otra llamada a stock (preguntar, liberar, consumir): no despues del plazo. */
    void checkOpen() {
        lock.lock();
        try {
            requireOpen();
        } finally {
            lock.unlock();
        }
    }

    private void requireOpen() {
        if (cancelled) {
            throw new Cancelled();
        }
    }

    State state() {
        lock.lock();
        try {
            return new State(reservationId, status, error, inDoubt);
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------- lo mismo que la entidad, sobre la copia

    void clearInDoubt() {
        set(reservationId(), status(), error(), null);
    }

    void markNotRequested() {
        set(reservationId(), StockSyncStatus.NOT_REQUESTED, null, null);
    }

    void markReserved(UUID reservation) {
        set(reservation, StockSyncStatus.RESERVED, null, null);
    }

    void markConsumed() {
        set(reservationId(), StockSyncStatus.CONSUMED, null, null);
    }

    void markReleased() {
        set(reservationId(), StockSyncStatus.RELEASED, null, null);
    }

    private void set(UUID reservation, StockSyncStatus newStatus, String newError, StockRequestType newInDoubt) {
        lock.lock();
        try {
            this.reservationId = reservation;
            this.status = newStatus;
            this.error = newError;
            this.inDoubt = newInDoubt;
        } finally {
            lock.unlock();
        }
    }

    UUID reservationId() {
        return state().reservationId();
    }

    StockSyncStatus status() {
        return state().status();
    }

    String error() {
        return state().error();
    }

    StockRequestType inDoubt() {
        return state().inDoubt();
    }

    boolean isInDoubt() {
        return inDoubt() != null;
    }

    UUID id() {
        return id;
    }

    UUID materialId() {
        return materialId;
    }

    UUID warehouseId() {
        return warehouseId;
    }

    String materialCode() {
        return materialCode;
    }

    String unit() {
        return unit;
    }

    BigDecimal plannedQuantity() {
        return plannedQuantity;
    }

    BigDecimal consumedQuantity() {
        return consumedQuantity;
    }

    String orderCode() {
        return orderCode;
    }

    UUID stockProjectId() {
        return stockProjectId;
    }
}
