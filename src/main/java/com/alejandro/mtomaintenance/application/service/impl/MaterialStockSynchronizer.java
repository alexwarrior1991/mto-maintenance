package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.stock.StockReservation;
import com.alejandro.mtomaintenance.application.exception.MaterialUsageException;
import com.alejandro.mtomaintenance.application.exception.StockRejectedException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.service.StockClient;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockRequestType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
 * idempotencia ({@link #idempotencyKey}), y la linea las apunta en duda ({@code stockRequestInDoubt})
 * antes de mandarlas. Lo siguiente que se haga con ella contra stock -reintentar, completar, cancelar,
 * quitarla- empieza por repetir la que siga en duda, con la misma clave y el mismo cuerpo: stock
 * devuelve lo que ya hizo, o lo hace ahora, y solo entonces se sigue. Hasta que stock contesta no
 * cambia lo que viaja en ella: ni lo previsto ni lo consumido de la linea, ni el proyecto de stock de
 * la orden (lo rechazan los servicios que los modifican).</p>
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
     *
     * <p>Una reserva en duda se confirma primero, para liberar la que stock tenga. Una salida en duda
     * impide quitarla: el material quiza ya salio, y lo que toca es sincronizar.</p>
     */
    void releaseNow(MaintenanceMaterialUsage usage) {
        if (usage.getStockRequestInDoubt() == StockRequestType.OUTPUT) {
            throw new MaterialUsageException("Material " + usage.getMaterialCode() + " has an output to stock without an answer yet, "
                    + "so it may have left the warehouse already; sync the line before removing it");
        }
        if (!usage.isInDoubt() && (usage.getStockReservationId() == null || !isPending(usage))) {
            return;
        }
        if (!stockClient.isEnabled()) {
            throw new StockUnavailableException("The stock client is disabled (app.stock.enabled=false); the reservation of "
                    + usage.getMaterialCode() + " cannot be released");
        }
        if (usage.isInDoubt()) {
            // La reserva que se quedo sin respuesta puede estar reteniendo material: se confirma para liberarla.
            confirmReservation(usage);
            if (usage.getStockReservationId() == null) {
                return;
            }
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
     * Almacen, pide otra. Y termina una salida que se quedo sin respuesta al intentar completarla, en
     * vez de reservar de nuevo lo que quiza ya salio del almacen.</p>
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
            default -> usage.getStockRequestInDoubt() == StockRequestType.OUTPUT
                    ? attempt(usage, "consume", () -> settle(usage))
                    : usage.getPlannedQuantity().signum() == 0 || status == StockSyncStatus.CONSUMED ? null : reserve(order, List.of(usage));
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
     * sigue siendo la de la linea, y se liquida al completar la orden. Una reserva en duda se repite
     * aqui mismo: es la misma peticion, con la misma clave.
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
        requestReservation(usage, project.get());
    }

    /** Pide la reserva de la linea contra {@code project}, apuntada en duda hasta que stock conteste. */
    private void requestReservation(MaintenanceMaterialUsage usage, UUID project) {
        String key = idempotencyKey(usage, StockRequestType.RESERVATION, usage.getStockReservationId());
        usage.markInDoubt(StockRequestType.RESERVATION);
        StockReservation reservation = stockClient.reserve(usage.getMaterialId(), usage.getWarehouseId(), project,
                usage.getPlannedQuantity(), key);
        usage.markReserved(reservation.id());
        LOGGER.info("Material reserved in stock: order={}, material={}, reservation={}",
                usage.getOrder().getCode(), usage.getMaterialCode(), reservation.id());
    }

    /**
     * Repite la reserva que la linea tiene en duda, con su clave y su cuerpo: si llego a stock, stock
     * devuelve la que creo; si no, la crea ahora. Si stock dice que no, es que no llego -la habria
     * devuelto sin validar nada-, y la linea sigue como si no se hubiera pedido. Un 409 IDEM-001 dice
     * lo contrario, que la clave ya creo algo con otro cuerpo: se deja pasar, y la linea lo cuenta.
     */
    private void confirmReservation(MaintenanceMaterialUsage usage) {
        UUID project = usage.getOrder().getStockProjectId();
        if (project == null) {
            throw new IllegalStateException("Material line " + usage.getMaterialCode() + " has a reservation in doubt but order "
                    + usage.getOrder().getCode() + " has no stock project to repeat it against");
        }
        try {
            requestReservation(usage, project);
        } catch (StockRejectedException rejected) {
            if (rejected.isIdempotencyConflict()) {
                LOGGER.error("The idempotency key of the reservation in doubt was used with another body: order={}, material={}, cause={}",
                        usage.getOrder().getCode(), usage.getMaterialCode(), rejected.getMessage());
                throw rejected;
            }
            usage.clearInDoubt();
            LOGGER.info("The reservation in doubt never reached stock, which now rejects it: order={}, material={}, cause={}",
                    usage.getOrder().getCode(), usage.getMaterialCode(), rejected.getMessage());
        }
    }

    private void settle(MaintenanceMaterialUsage usage) {
        if (usage.getStockRequestInDoubt() == StockRequestType.RESERVATION) {
            // La reserva sin respuesta puede estar reteniendo material: primero se confirma, y luego se
            // consume o se libera como cualquier otra. Sin esto, lo usado salia como salida directa y
            // la reserva se quedaba en stock.
            confirmReservation(usage);
        }
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
        if (usage.getStockRequestInDoubt() == StockRequestType.OUTPUT) {
            // Lo usado salio, o sale ahora: la salida que se quedo sin respuesta se termina, y la linea
            // queda consumida como en stock. Liberar no la deshace.
            settle(usage);
            return;
        }
        if (usage.getStockRequestInDoubt() == StockRequestType.RESERVATION) {
            confirmReservation(usage);
        }
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

    /** La salida de la linea, apuntada en duda hasta que stock conteste; quien la llama deja la linea consumida. */
    private void output(MaintenanceMaterialUsage usage, BigDecimal quantity, String notes) {
        MaintenanceOrder order = usage.getOrder();
        String key = idempotencyKey(usage, StockRequestType.OUTPUT, null);
        usage.markInDoubt(StockRequestType.OUTPUT);
        stockClient.output(usage.getMaterialId(), usage.getWarehouseId(), order.getStockProjectId(), quantity, order.getCode(), notes, key);
    }

    /**
     * Las notas de la salida de lo usado cuando ninguna reserva lo cubre. Son las mismas por los dos
     * caminos que llegan a ella -la reserva se libera en este paso porque se uso menos, o ya no
     * retenia nada- porque stock compara el cuerpo del reintento con el del primero: si la salida se
     * queda sin respuesta despues de liberar, el reintento llega por el segundo camino y tiene que
     * mandar lo mismo, o stock lo rechazaria con 409 IDEM-001.
     */
    private static String directOutputNotes(MaintenanceMaterialUsage usage) {
        String order = "Maintenance order " + usage.getOrder().getCode();
        return usage.getStockReservationId() == null ? order : order + ", reservation " + usage.getStockReservationId() + " not consumed";
    }

    /**
     * La clave de idempotencia de una peticion de la linea a mto-stock: {@code mto-maintenance:}, la
     * linea, la peticion y, en una reserva, la reserva anterior de la linea ({@code first} si no tuvo).
     * No sale del cuerpo: un reintento lleva siempre la misma, y si el cuerpo cambiara stock lo
     * rechazaria con 409 IDEM-001 en vez de aplicarlo otra vez. Por eso lo que viaja no cambia
     * mientras la peticion esta en duda.
     *
     * <p>Una reserva lleva la anterior porque si la que tenia se libero desde Almacen, la siguiente es
     * otra reserva y no un reintento de aquella. Una salida no lleva nada mas: una linea da como mucho
     * una, la de lo usado o la del exceso sobre su reserva.</p>
     *
     * <p>La linea tiene que estar guardada: sin id, dos lineas compartirian clave y la segunda
     * recibiria lo que creo la primera.</p>
     */
    static String idempotencyKey(MaintenanceMaterialUsage usage, StockRequestType request, UUID previousReservation) {
        if (usage.getId() == null) {
            throw new IllegalStateException("Material line " + usage.getMaterialCode() + " has no id yet: save it before asking stock for anything");
        }
        String key = "mto-maintenance:" + usage.getId();
        return request == StockRequestType.RESERVATION
                ? key + ":reserve:" + (previousReservation == null ? "first" : previousReservation)
                : key + ":output";
    }

    /** Lo que stock tiene reservado, que desde Almacen se puede haber cambiado; lo previsto si no lo dice. */
    private static BigDecimal reserved(MaintenanceMaterialUsage usage, StockReservation reservation) {
        return reservation.quantity() == null ? usage.getPlannedQuantity() : reservation.quantity();
    }

    /**
     * Lo que falta por reservar: cantidad prevista y ninguna reserva viva ni consumida. Una linea con
     * una salida en duda no: su material quiza ya salio del almacen.
     */
    private static boolean awaitsReservation(MaintenanceMaterialUsage usage) {
        StockSyncStatus status = usage.getStockSyncStatus();
        return usage.getPlannedQuantity().signum() > 0 && status != StockSyncStatus.RESERVED && status != StockSyncStatus.CONSUMED
                && usage.getStockRequestInDoubt() != StockRequestType.OUTPUT;
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
