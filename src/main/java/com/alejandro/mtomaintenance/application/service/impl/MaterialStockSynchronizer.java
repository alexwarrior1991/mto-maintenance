package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.stock.StockReservation;
import com.alejandro.mtomaintenance.application.exception.MaterialUsageException;
import com.alejandro.mtomaintenance.application.exception.StockRejectedException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.service.StockClient;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Conversacion con mto-stock por linea de material. Un fallo de stock nunca tumba la operacion de
 * negocio: la linea queda FAILED (stock no respondio) o REJECTED (respondio que no) con el motivo,
 * y se reintenta con /sync. Solo {@link #syncNow} y {@link #releaseNow} dejan pasar la excepcion,
 * porque ahi el cliente ha pedido explicitamente hablar con stock.
 *
 * <p>Reserva completa al planificar; al completar, la reserva se consume si lo consumido iguala lo
 * reservado, se libera y se da salida directa de lo consumido si es menor, y se consume mas una
 * salida del exceso si es mayor (mto-stock no admite consumo parcial de una reserva). Al cancelar,
 * se libera.</p>
 *
 * <p>Antes de consumir o liberar una linea con reserva se pregunta a stock como esta, porque stock no
 * avisa de nada y la reserva puede haber cambiado desde Almacen, o en un intento anterior que fallo a
 * medias: si sigue activa, lo de arriba; si ya se consumio, solo queda la salida del exceso; si ya no
 * retiene nada (liberada, cancelada o desconocida para stock), lo usado sale como salida directa.
 * Asi repetir un paso no consume ni libera dos veces lo mismo.</p>
 *
 * <p>Una reserva o una salida que llegaron a stock pero cuya respuesta se perdio no se pueden
 * preguntar asi: la linea no llego a saber que se creo. Esas dos peticiones llevan una clave de
 * idempotencia ({@link #idempotencyKey}), la misma para el mismo paso con el mismo cuerpo, y stock
 * devuelve lo que ya hizo en vez de hacerlo otra vez.</p>
 */
@Component
@RequiredArgsConstructor
class MaterialStockSynchronizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaterialStockSynchronizer.class);

    static final String PROJECT_CODE_PREFIX = "EP-";

    private final StockClient stockClient;

    boolean isEnabled() {
        return stockClient.isEnabled();
    }

    /** Planificar o arrancar la orden: reserva cada linea que aun no tiene reserva. */
    void reserveAll(MaintenanceOrder order) {
        if (stockClient.isEnabled()) {
            reserve(order, order.getMaterials().stream().filter(MaterialStockSynchronizer::awaitsReservation).toList());
        }
    }

    /** Una linea registrada con la orden ya planificada, o lo gastado al completar una tarea. */
    void reserve(MaintenanceMaterialUsage usage) {
        if (stockClient.isEnabled() && awaitsReservation(usage)) {
            reserve(usage.getOrder(), List.of(usage));
        }
    }

    /** Cierre de la orden: registra en stock lo consumido de la linea. */
    void consume(MaintenanceMaterialUsage usage) {
        if (stockClient.isEnabled() && usage.getStockSyncStatus() != StockSyncStatus.CONSUMED) {
            attempt(usage, "consume", () -> settle(usage));
        }
    }

    /** Cancelacion de la orden: lo que la linea retiene vuelve a estar disponible en stock. */
    void release(MaintenanceMaterialUsage usage) {
        if (stockClient.isEnabled() && isPending(usage)) {
            attempt(usage, "release", () -> free(usage));
        }
    }

    /**
     * Liberacion antes de quitar una linea. Como {@link #syncNow}, deja pasar la excepcion: si stock
     * no responde, la linea no se quita, porque su reserva seguiria reteniendo material alli. Una
     * reserva que ya no retiene nada (liberada o cancelada desde Almacen) no impide quitarla; una ya
     * consumida si, porque el material salio del almacen con esta linea.
     */
    void releaseNow(MaintenanceMaterialUsage usage) {
        if (usage.getStockReservationId() == null || !isPending(usage)) {
            return;
        }
        if (!stockClient.isEnabled()) {
            throw new StockUnavailableException("The stock client is disabled (app.stock.enabled=false); the reservation of "
                    + usage.getMaterialCode() + " cannot be released");
        }
        Optional<StockReservation> reservation = currentReservation(usage);
        if (reservation.filter(StockReservation::isConsumed).isPresent()) {
            throw new MaterialUsageException("Material " + usage.getMaterialCode() + " was already consumed in stock (reservation "
                    + usage.getStockReservationId() + "); the line cannot be removed");
        }
        if (reservation.filter(StockReservation::isActive).isPresent()) {
            stockClient.release(usage.getStockReservationId());
            LOGGER.info("Reservation released before removing the line: order={}, material={}, reservation={}",
                    usage.getOrder().getCode(), usage.getMaterialCode(), usage.getStockReservationId());
        } else {
            LOGGER.info("Reservation {} no longer holds anything in stock; the line is removed anyway: order={}, material={}",
                    usage.getStockReservationId(), usage.getOrder().getCode(), usage.getMaterialCode());
        }
    }

    /**
     * Reintento explicito: rehace el paso que toque segun el estado de la orden. A diferencia del
     * resto, deja pasar la excepcion, despues de dejar en la linea lo que haya pasado: quien pide
     * sincronizar quiere saber si stock sigue caido (503) o por que dice que no (409 o 422).
     *
     * <p>En una orden en curso tambien comprueba una linea RESERVED: si la reserva se libero desde
     * Almacen, pide otra.</p>
     */
    void syncNow(MaintenanceMaterialUsage usage) {
        if (!stockClient.isEnabled()) {
            throw new StockUnavailableException("The stock client is disabled (app.stock.enabled=false)");
        }
        MaintenanceOrder order = usage.getOrder();
        StockSyncStatus status = usage.getStockSyncStatus();
        RuntimeException failure = switch (order.getStatus()) {
            case DRAFT -> null;
            case COMPLETED -> status == StockSyncStatus.CONSUMED ? null : attempt(usage, "consume", () -> settle(usage));
            case CANCELLED -> isPending(usage) ? attempt(usage, "release", () -> free(usage)) : null;
            default -> usage.getPlannedQuantity().signum() == 0 || status == StockSyncStatus.CONSUMED
                    ? null : reserve(order, List.of(usage));
        };
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Reserva las lineas dadas contra el proyecto de stock de la orden, resuelto una vez. Si no se
     * puede resolver, ninguna se reserva y todas lo dicen. Devuelve el primer fallo, o null.
     */
    private RuntimeException reserve(MaintenanceOrder order, List<MaintenanceMaterialUsage> lines) {
        if (lines.isEmpty()) {
            return null;
        }
        Optional<UUID> project;
        try {
            project = projectOf(order);
        } catch (StockUnavailableException | StockRejectedException exception) {
            lines.forEach(line -> record(line, "resolve project", exception));
            return exception;
        }
        RuntimeException failure = null;
        for (MaintenanceMaterialUsage line : lines) {
            RuntimeException lineFailure = attempt(line, "reserve", () -> hold(line, project));
            if (failure == null) {
                failure = lineFailure;
            }
        }
        return failure;
    }

    /**
     * Deja la linea con su reserva. Si ya tuvo una (un intento anterior a medias, o una comprobacion
     * pedida con /sync), antes pregunta a stock: otra reserva la duplicaria. Una reserva consumida
     * sigue siendo la de la linea, y se liquida al completar la orden.
     */
    private void hold(MaintenanceMaterialUsage usage, Optional<UUID> project) {
        Optional<StockReservation> current = currentReservation(usage);
        if (current.isPresent() && !current.get().holdsNothing()) {
            usage.markReserved(current.get().id());
            return;
        }
        if (project.isEmpty()) {
            // Sin proyecto de stock no hay contra que reservar: al completar, lo usado sale como salida directa.
            usage.markNotRequested();
            return;
        }
        // La reserva anterior va en la clave: si la linea tuvo una y ya no retiene nada, esta es otra
        // reserva y no un reintento de aquella.
        String key = idempotencyKey(usage, "reserve", usage.getStockReservationId(), usage.getMaterialId(),
                usage.getWarehouseId(), project.get(), usage.getPlannedQuantity());
        StockReservation reservation = stockClient.reserve(usage.getMaterialId(), usage.getWarehouseId(), project.get(),
                usage.getPlannedQuantity(), key);
        usage.markReserved(reservation.id());
        LOGGER.info("Material reserved in stock: order={}, material={}, reservation={}",
                usage.getOrder().getCode(), usage.getMaterialCode(), reservation.id());
    }

    private void settle(MaintenanceMaterialUsage usage) {
        Optional<StockReservation> reservation = currentReservation(usage);
        if (reservation.filter(StockReservation::isActive).isPresent()) {
            settleActive(usage, reservation.get());
        } else if (reservation.filter(StockReservation::isConsumed).isPresent()) {
            settleConsumed(usage, reservation.get());
        } else if (usage.getConsumedQuantity().signum() > 0) {
            // Sin reserva que retenga nada: lo usado sale como salida directa.
            output(usage, usage.getConsumedQuantity(), directOutputNotes(usage));
            usage.markConsumed();
        } else if (usage.getStockReservationId() != null) {
            usage.markReleased();
        } else {
            usage.markNotRequested();
        }
    }

    private void settleActive(MaintenanceMaterialUsage usage, StockReservation reservation) {
        BigDecimal used = usage.getConsumedQuantity();
        BigDecimal reserved = reserved(usage, reservation);
        if (used.signum() == 0) {
            stockClient.release(reservation.id());
            usage.markReleased();
            return;
        }
        int comparison = used.compareTo(reserved);
        if (comparison < 0) {
            // mto-stock no consume parte de una reserva: se libera entera y sale lo usado.
            stockClient.release(reservation.id());
            output(usage, used, directOutputNotes(usage));
        } else {
            stockClient.consume(reservation.id());
            if (comparison > 0) {
                output(usage, used.subtract(reserved), "Over-consumption beyond reservation " + reservation.id());
            }
        }
        usage.markConsumed();
    }

    /** La reserva ya se consumio (en un intento anterior o desde Almacen): queda la salida de lo usado por encima. */
    private void settleConsumed(MaintenanceMaterialUsage usage, StockReservation reservation) {
        BigDecimal excess = usage.getConsumedQuantity().subtract(reserved(usage, reservation));
        if (excess.signum() > 0) {
            output(usage, excess, "Over-consumption beyond reservation " + reservation.id());
        } else if (excess.signum() < 0) {
            LOGGER.warn("Reservation {} was consumed in stock for more than the {} {} used: order={}, material={}",
                    reservation.id(), usage.getConsumedQuantity(), usage.getUnit(), usage.getOrder().getCode(), usage.getMaterialCode());
        }
        usage.markConsumed();
    }

    private void free(MaintenanceMaterialUsage usage) {
        Optional<StockReservation> reservation = currentReservation(usage);
        if (reservation.filter(StockReservation::isActive).isPresent()) {
            stockClient.release(reservation.get().id());
            usage.markReleased();
        } else if (reservation.filter(StockReservation::isConsumed).isPresent()) {
            // Se consumio antes de cancelar: el material ya salio del almacen, y la linea lo dice.
            usage.markConsumed();
        } else if (usage.getStockReservationId() != null) {
            usage.markReleased();
        } else {
            usage.markNotRequested();
        }
    }

    /**
     * La reserva de la linea tal como la tiene stock, si la linea tiene una que pueda seguir viva;
     * vacio si no, o si stock no la conoce. Un estado que este servicio no conoce no se interpreta:
     * la linea queda FAILED con el.
     */
    private Optional<StockReservation> currentReservation(MaintenanceMaterialUsage usage) {
        StockSyncStatus status = usage.getStockSyncStatus();
        if (usage.getStockReservationId() == null || status == StockSyncStatus.RELEASED || status == StockSyncStatus.CONSUMED) {
            return Optional.empty();
        }
        Optional<StockReservation> reservation = stockClient.findReservation(usage.getStockReservationId());
        if (reservation.isPresent() && !reservation.get().isActive() && !reservation.get().isConsumed() && !reservation.get().holdsNothing()) {
            throw new StockUnavailableException("mto-stock reports reservation " + usage.getStockReservationId() + " as "
                    + reservation.get().status() + ", which this service does not know how to settle");
        }
        return reservation;
    }

    /** Proyecto de stock de la orden: el suyo, o el de su paquete de ejecucion ({@code EP-<id>}) si stock lo tiene. */
    private Optional<UUID> projectOf(MaintenanceOrder order) {
        if (order.getStockProjectId() != null) {
            return Optional.of(order.getStockProjectId());
        }
        if (order.getExecutionPackageId() == null) {
            return Optional.empty();
        }
        Optional<UUID> project = stockClient.findProjectIdByCode(PROJECT_CODE_PREFIX + order.getExecutionPackageId());
        project.ifPresent(order::setStockProjectId);
        return project;
    }

    private void output(MaintenanceMaterialUsage usage, BigDecimal quantity, String notes) {
        MaintenanceOrder order = usage.getOrder();
        String key = idempotencyKey(usage, "output", usage.getMaterialId(), usage.getWarehouseId(), order.getStockProjectId(),
                quantity, order.getCode(), notes);
        stockClient.output(usage.getMaterialId(), usage.getWarehouseId(), order.getStockProjectId(), quantity, order.getCode(), notes, key);
    }

    /**
     * Las notas de la salida de lo usado cuando ninguna reserva lo cubre. Son las mismas por los dos
     * caminos que llegan a ella -la reserva se libera en este paso porque se uso menos, o ya no
     * retenia nada- porque la clave de idempotencia sale del cuerpo: si la salida se queda sin
     * respuesta despues de liberar, el reintento llega por el segundo camino y tiene que mandar lo mismo.
     */
    private static String directOutputNotes(MaintenanceMaterialUsage usage) {
        String order = "Maintenance order " + usage.getOrder().getCode();
        return usage.getStockReservationId() == null ? order : order + ", reservation " + usage.getStockReservationId() + " not consumed";
    }

    /**
     * La clave de idempotencia de una peticion de la linea a mto-stock: {@code mto-maintenance:}, la
     * linea, el paso y un resumen de lo que viaja. Es la misma para el mismo paso con el mismo cuerpo,
     * asi que un reintento tras perder la respuesta la repite y stock devuelve lo que ya hizo. Cambia
     * en cuanto cambia lo que viaja (lo previsto de una linea cuya reserva fallo, el proyecto): stock
     * rechaza con 409 una clave repetida con otro cuerpo, y la linea quedaria atascada. El precio es
     * que, si lo primero habia llegado a stock, ahi se queda, como sin clave.
     *
     * <p>La linea tiene que estar guardada: sin id, dos lineas con el mismo cuerpo compartirian clave y
     * la segunda recibiria la reserva de la primera.</p>
     */
    static String idempotencyKey(MaintenanceMaterialUsage usage, String step, Object... request) {
        if (usage.getId() == null) {
            throw new IllegalStateException("Material line " + usage.getMaterialCode() + " has no id yet: save it before asking stock for anything");
        }
        MessageDigest digest = sha256();
        for (Object part : request) {
            byte[] bytes = (part instanceof BigDecimal decimal ? decimal.stripTrailingZeros().toPlainString() : String.valueOf(part))
                    .getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }
        return "mto-maintenance:" + usage.getId() + ":" + step + ":" + HexFormat.of().formatHex(digest.digest(), 0, 16);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /** Lo que stock tiene reservado, que desde Almacen se puede haber cambiado; lo previsto si no lo dice. */
    private static BigDecimal reserved(MaintenanceMaterialUsage usage, StockReservation reservation) {
        return reservation.quantity() == null ? usage.getPlannedQuantity() : reservation.quantity();
    }

    /** Lo que falta por reservar: cantidad prevista y ninguna reserva viva ni consumida. */
    private static boolean awaitsReservation(MaintenanceMaterialUsage usage) {
        StockSyncStatus status = usage.getStockSyncStatus();
        return usage.getPlannedQuantity().signum() > 0 && status != StockSyncStatus.RESERVED && status != StockSyncStatus.CONSUMED;
    }

    /** Algo a medias en stock: una reserva, o un intento que fallo o se rechazo. */
    private static boolean isPending(MaintenanceMaterialUsage usage) {
        return usage.getStockSyncStatus() == StockSyncStatus.RESERVED || usage.isSyncFailed();
    }

    /** Ejecuta un paso contra stock; si falla, la linea lo dice y se devuelve el fallo. */
    private static RuntimeException attempt(MaintenanceMaterialUsage usage, String step, Runnable action) {
        try {
            action.run();
            return null;
        } catch (StockUnavailableException | StockRejectedException exception) {
            record(usage, step, exception);
            return exception;
        }
    }

    private static void record(MaintenanceMaterialUsage usage, String step, RuntimeException exception) {
        if (exception instanceof StockRejectedException) {
            usage.markRejected(step + ": " + exception.getMessage());
            LOGGER.warn("Stock rejected the {} of a material line, left as REJECTED: order={}, material={}, cause={}",
                    step, usage.getOrder().getCode(), usage.getMaterialCode(), exception.getMessage());
        } else {
            usage.markFailed(step + ": " + exception.getMessage());
            LOGGER.warn("Stock {} failed, material line left as FAILED: order={}, material={}, cause={}",
                    step, usage.getOrder().getCode(), usage.getMaterialCode(), exception.getMessage());
        }
    }
}
