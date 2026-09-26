package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.stock.StockMaterial;
import com.alejandro.mtomaintenance.application.dto.stock.StockReservation;
import com.alejandro.mtomaintenance.application.exception.StockRejectedException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Frontera con mto-stock. Los materiales y los almacenes viven alli; aqui solo se referencian por
 * uuid y se pide reservar, consumir, liberar o dar salida. Toda operacion puede fallar de dos
 * maneras, y los servicios las tratan distinto sin perder la operacion de negocio:
 * {@link StockUnavailableException} si stock no respondio (la linea queda FAILED y reintentar tiene
 * sentido) y {@link StockRejectedException} si respondio que no (queda REJECTED con su motivo).
 */
public interface StockClient {

    boolean isEnabled();

    Optional<StockMaterial> findMaterialById(UUID materialId);

    Optional<StockMaterial> findMaterialByCode(String code);

    /** Proyecto de stock por codigo; para un paquete de ejecucion es {@code EP-<id>}. */
    Optional<UUID> findProjectIdByCode(String code);

    /** La reserva como la tiene stock ahora; vacio si stock no la conoce (404). */
    Optional<StockReservation> findReservation(UUID reservationId);

    StockReservation reserve(UUID materialId, UUID warehouseId, UUID projectId, BigDecimal quantity);

    /** Consume la reserva completa (mto-stock no admite consumo parcial). */
    void consume(UUID reservationId);

    void release(UUID reservationId);

    /** Salida directa sin reserva, con la orden como referencia externa. */
    void output(UUID materialId, UUID warehouseId, UUID projectId, BigDecimal quantity, String externalReference, String notes);
}
