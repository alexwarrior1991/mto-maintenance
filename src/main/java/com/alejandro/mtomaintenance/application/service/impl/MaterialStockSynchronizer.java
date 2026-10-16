package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.stock.StockReservation;
import com.alejandro.mtomaintenance.application.exception.MaterialUsageException;
import com.alejandro.mtomaintenance.application.exception.StockRejectedException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.service.DomainEventPublisher;
import com.alejandro.mtomaintenance.application.service.StockClient;
import com.alejandro.mtomaintenance.configuration.async.AsyncConfiguration;
import com.alejandro.mtomaintenance.configuration.stock.StockSyncProperties;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockRequestType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

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
 *
 * <p>Cada linea que se queda a medias lo cuenta ademas fuera: {@code maintenance.material.rejected}
 * (stock dijo que no, con su codigo), {@code maintenance.material.in-doubt} (una reserva o una
 * salida enviadas sin respuesta) o {@code maintenance.material.failed} (stock no respondio y no hay
 * nada en duda), en la misma transaccion en la que la linea queda marcada.</p>
 *
 * <p>Una transicion de orden con varias lineas (planificar, arrancar, completar, cancelar) habla con
 * stock por todas a la vez, cada una en un hilo virtual y como mucho {@code app.stock.sync.parallelism}
 * a la vez, con un plazo para todas ({@code app.stock.sync.deadline}). La conversacion de cada linea
 * corre sobre una copia ({@link StockLine}), sin tocar la entidad ni la base, y el hilo de la transaccion
 * la devuelve a la linea y publica lo que toque. La que no termina en el plazo queda FAILED con lo que
 * tuviera en duda y no manda nada mas a stock; al confirmar, se reintenta en ese momento
 * ({@link StockLinesDeferred}) en vez de esperar a la vuelta de cinco minutos. Es seguro por lo mismo
 * que lo es reintentar: la misma clave, y antes de nada se repite lo que siga en duda.</p>
 */
@Component
class MaterialStockSynchronizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaterialStockSynchronizer.class);

    static final String PROJECT_CODE_PREFIX = "EP-";

    private final StockClient stockClient;
    private final DomainEventPublisher events;
    private final Executor executor;
    private final int parallelism;
    /** El plazo de una transicion con varias lineas; {@code null}, sin plazo (los tests de la conversacion). */
    private final Duration deadline;
    private final boolean immediateRetry;
    private final ApplicationEventPublisher applicationEvents;

    @Autowired
    MaterialStockSynchronizer(StockClient stockClient, DomainEventPublisher events, StockSyncProperties properties,
                              @Qualifier(AsyncConfiguration.TASK_EXECUTOR) Executor executor, ApplicationEventPublisher applicationEvents) {
        this(stockClient, events, executor, properties.parallelism(), properties.deadline(), properties.immediateRetry(), applicationEvents);
    }

    /** En el hilo que llama, una linea detras de otra y sin plazo: lo que prueban los casos de la conversacion. */
    MaterialStockSynchronizer(StockClient stockClient, DomainEventPublisher events) {
        this(stockClient, events, Runnable::run, 1, null, false, event -> {
        });
    }

    MaterialStockSynchronizer(StockClient stockClient, DomainEventPublisher events, Executor executor, int parallelism, Duration deadline,
                              boolean immediateRetry, ApplicationEventPublisher applicationEvents) {
        this.stockClient = stockClient;
        this.events = events;
        this.executor = executor;
        this.parallelism = Math.max(1, parallelism);
        this.deadline = deadline;
        this.immediateRetry = immediateRetry;
        this.applicationEvents = applicationEvents;
    }

    boolean isEnabled() {
        return stockClient.isEnabled();
    }

    /** Planificar o arrancar la orden: reserva cada linea que aun no tiene reserva, todas a la vez y con plazo. */
    void reserveAll(MaintenanceOrder order) {
        if (stockClient.isEnabled()) {
            reserve(order, order.getMaterials().stream().filter(MaterialStockSynchronizer::awaitsReservation).toList(), true);
        }
    }

    /** Una linea registrada con la orden ya planificada, o lo gastado al completar una tarea. */
    void reserve(MaintenanceMaterialUsage usage) {
        if (stockClient.isEnabled() && awaitsReservation(usage)) {
            reserve(usage.getOrder(), List.of(usage), false);
        }
    }

    /** Cierre de la orden: registra en stock lo consumido de cada linea, todas a la vez y con plazo. */
    void consumeAll(MaintenanceOrder order) {
        if (stockClient.isEnabled()) {
            converse(order, order.getMaterials().stream().filter(usage -> usage.getStockSyncStatus() != StockSyncStatus.CONSUMED).toList(),
                    "consume", this::settle, true);
        }
    }

    /** Cierre de una linea: registra en stock lo consumido. */
    void consume(MaintenanceMaterialUsage usage) {
        if (stockClient.isEnabled() && usage.getStockSyncStatus() != StockSyncStatus.CONSUMED) {
            converse(usage.getOrder(), List.of(usage), "consume", this::settle, false);
        }
    }

    /** Cancelacion de la orden: lo que cada linea retiene vuelve a estar disponible en stock, todas a la vez y con plazo. */
    void releaseAll(MaintenanceOrder order) {
        if (stockClient.isEnabled()) {
            converse(order, order.getMaterials().stream().filter(MaterialStockSynchronizer::isPending).toList(), "release", this::free, true);
        }
    }

    /** Cancelacion de una linea: lo que retiene vuelve a estar disponible en stock. */
    void release(MaintenanceMaterialUsage usage) {
        if (stockClient.isEnabled() && isPending(usage)) {
            converse(usage.getOrder(), List.of(usage), "release", this::free, false);
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
        StockLine line = StockLine.of(usage);
        try {
            releaseNow(line);
        } finally {
            line.applyTo(usage);
        }
    }

    private void releaseNow(StockLine line) {
        if (line.isInDoubt()) {
            // La reserva que se quedo sin respuesta puede estar reteniendo material: se confirma para liberarla.
            confirmReservation(line);
            if (line.reservationId() == null) {
                return;
            }
        }
        Optional<StockReservation> reservation = currentReservation(line);
        if (reservation.filter(StockReservation::isConsumed).isPresent()) {
            throw new MaterialUsageException("Material " + line.materialCode() + " was already consumed in stock (reservation "
                    + line.reservationId() + "); the line cannot be removed");
        }
        if (reservation.filter(StockReservation::isActive).isPresent()) {
            line.checkOpen();
            stockClient.release(line.reservationId());
            LOGGER.info("Reservation released before removing the line: order={}, material={}, reservation={}",
                    line.orderCode(), line.materialCode(), line.reservationId());
        } else {
            LOGGER.info("Reservation {} no longer holds anything in stock; the line is removed anyway: order={}, material={}",
                    line.reservationId(), line.orderCode(), line.materialCode());
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
            case COMPLETED -> status == StockSyncStatus.CONSUMED ? null : converse(order, List.of(usage), "consume", this::settle, false);
            case CANCELLED -> isPending(usage) ? converse(order, List.of(usage), "release", this::free, false) : null;
            default -> usage.getStockRequestInDoubt() == StockRequestType.OUTPUT
                    ? converse(order, List.of(usage), "consume", this::settle, false)
                    : usage.getPlannedQuantity().signum() == 0 || status == StockSyncStatus.CONSUMED ? null
                    : reserve(order, List.of(usage), false);
        };
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Reserva las lineas dadas contra el proyecto de stock de la orden, resuelto una vez y antes de
     * copiarlas: la copia lleva el proyecto. Si no se puede resolver, ninguna se reserva y todas lo
     * dicen. Devuelve el primer fallo, o null.
     */
    private RuntimeException reserve(MaintenanceOrder order, List<MaintenanceMaterialUsage> lines, boolean together) {
        if (lines.isEmpty()) {
            return null;
        }
        Optional<UUID> project;
        try {
            project = projectOf(order);
        } catch (StockUnavailableException | StockRejectedException exception) {
            lines.forEach(line -> record(line, "resolve project", exception, LineState.of(line)));
            return exception;
        }
        return converse(order, lines, "reserve", line -> hold(line, project), together);
    }

    /**
     * Un paso con stock para cada linea. Con {@code together}, todas a la vez y con plazo
     * ({@link #converseTogether}); si no, en este hilo, una detras de otra. Devuelve el primer fallo,
     * en el orden de las lineas, o null.
     */
    private RuntimeException converse(MaintenanceOrder order, List<MaintenanceMaterialUsage> lines, String step, Consumer<StockLine> action,
                                      boolean together) {
        if (lines.isEmpty()) {
            return null;
        }
        if (together && deadline != null) {
            return converseTogether(order, lines, step, action);
        }
        RuntimeException failure = null;
        for (MaintenanceMaterialUsage usage : lines) {
            RuntimeException lineFailure = attempt(usage, step, action);
            if (failure == null) {
                failure = lineFailure;
            }
        }
        return failure;
    }

    /** Ejecuta un paso contra stock en este hilo, sobre la copia de la linea; si falla, la linea lo dice y se devuelve el fallo. */
    private RuntimeException attempt(MaintenanceMaterialUsage usage, String step, Consumer<StockLine> action) {
        LineState before = LineState.of(usage);
        StockLine line = StockLine.of(usage);
        RuntimeException failure;
        try {
            failure = talk(line, action);
        } finally {
            line.applyTo(usage);
        }
        if (failure != null) {
            record(usage, step, failure, before);
        }
        return failure;
    }

    /**
     * Todas las lineas a la vez, cada una en un hilo virtual, como mucho {@link #parallelism} a la vez y
     * con un plazo para todas. Los hilos solo hablan con stock, sobre su copia; este hilo, el de la
     * transaccion, espera, devuelve cada copia a su linea y publica, en el orden de las lineas.
     *
     * <p>La que no termina en el plazo se corta: se queda con lo que sabia (y lo que tuviera en duda),
     * FAILED, y al confirmar se reintenta en ese momento. Un fallo que no es de stock (un error de
     * programacion) se lanza despues de dejar las demas como contesto stock, como lo haria el bucle.</p>
     */
    private RuntimeException converseTogether(MaintenanceOrder order, List<MaintenanceMaterialUsage> lines, String step,
                                              Consumer<StockLine> action) {
        Semaphore permits = new Semaphore(parallelism);
        List<Conversation> conversations = new ArrayList<>(lines.size());
        for (MaintenanceMaterialUsage usage : lines) {
            Conversation conversation = new Conversation(usage, StockLine.of(usage), LineState.of(usage), new CompletableFuture<>());
            conversations.add(conversation);
            executor.execute(() -> conversation.run(permits, action));
        }

        long end = System.nanoTime() + deadline.toNanos();
        boolean interrupted = false;
        RuntimeException failure = null;
        RuntimeException unexpected = null;
        List<UUID> deferred = new ArrayList<>();
        List<CompletableFuture<?>> inFlight = new ArrayList<>();
        for (Conversation conversation : conversations) {
            MaintenanceMaterialUsage usage = conversation.usage();
            RuntimeException lineFailure;
            try {
                lineFailure = interrupted ? conversation.result().getNow(null) : conversation.result()
                        .get(Math.max(0, end - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (interrupted && !conversation.result().isDone()) {
                    throw new TimeoutException();
                }
                conversation.line().applyTo(usage);
            } catch (TimeoutException late) {
                StockLine.State known = conversation.line().cancel();
                usage.applyStockState(known.reservationId(), known.status(), known.error(), known.inDoubt());
                lineFailure = new StockUnavailableException("deferred: mto-stock did not answer within " + deadline
                        + "; retried again after the transaction");
                deferred.add(usage.getId());
                inFlight.add(conversation.result());
            } catch (ExecutionException broken) {
                conversation.line().applyTo(usage);
                if (unexpected == null) {
                    unexpected = rethrowable(broken.getCause());
                }
                continue;
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                interrupted = true;
                StockLine.State known = conversation.line().cancel();
                usage.applyStockState(known.reservationId(), known.status(), known.error(), known.inDoubt());
                lineFailure = new StockUnavailableException("deferred: interrupted while waiting for mto-stock");
                deferred.add(usage.getId());
                inFlight.add(conversation.result());
            }
            if (lineFailure != null) {
                record(usage, step, lineFailure, conversation.before());
                if (failure == null) {
                    failure = lineFailure;
                }
            }
        }
        if (unexpected != null) {
            conversations.forEach(conversation -> conversation.line().cancel());
            throw unexpected;
        }
        if (!deferred.isEmpty()) {
            LOGGER.warn("Stock {} of order {} left {} of {} material line(s) without an answer within {}: FAILED, {}",
                    step, order.getCode(), deferred.size(), lines.size(), deadline,
                    immediateRetry ? "retried right after the transaction" : "left to the periodic retry");
            if (immediateRetry) {
                applicationEvents.publishEvent(new StockLinesDeferred(order.getId(), List.copyOf(deferred), List.copyOf(inFlight)));
            }
        }
        return failure;
    }

    /** La conversacion de una linea en su hilo: su copia, lo que contaba antes y su resultado (el fallo de stock, o null). */
    private record Conversation(MaintenanceMaterialUsage usage, StockLine line, LineState before,
                                CompletableFuture<RuntimeException> result) {

        void run(Semaphore permits, Consumer<StockLine> action) {
            try {
                permits.acquire();
                try {
                    result.complete(talk(line, action));
                } finally {
                    permits.release();
                }
            } catch (StockLine.Cancelled cancelled) {
                // Cortada por el plazo: la transaccion ya conto lo que sabia; nadie espera este resultado.
                result.complete(null);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                result.completeExceptionally(interrupted);
            } catch (Throwable unexpected) {
                result.completeExceptionally(unexpected);
            }
        }
    }

    /** El paso sobre la copia: un fallo de stock se devuelve para que la linea lo cuente; lo demas se lanza. */
    private static RuntimeException talk(StockLine line, Consumer<StockLine> action) {
        try {
            action.accept(line);
            return null;
        } catch (StockUnavailableException | StockRejectedException exception) {
            return exception;
        }
    }

    /**
     * Deja la linea con su reserva. Si ya tuvo una (un intento anterior a medias, o una comprobacion
     * pedida con /sync), antes pregunta a stock: otra reserva la duplicaria. Una reserva consumida
     * sigue siendo la de la linea, y se liquida al completar la orden. Una reserva en duda se repite
     * aqui mismo: es la misma peticion, con la misma clave.
     */
    private void hold(StockLine line, Optional<UUID> project) {
        Optional<StockReservation> current = currentReservation(line);
        if (current.isPresent() && !current.get().holdsNothing()) {
            line.markReserved(current.get().id());
            return;
        }
        if (project.isEmpty()) {
            // Sin proyecto de stock no hay contra que reservar: al completar, lo usado sale como salida directa.
            line.markNotRequested();
            return;
        }
        requestReservation(line, project.get());
    }

    /** Pide la reserva de la linea contra {@code project}, apuntada en duda hasta que stock conteste. */
    private void requestReservation(StockLine line, UUID project) {
        String key = idempotencyKey(line.id(), line.materialCode(), StockRequestType.RESERVATION, line.reservationId());
        line.beginRequest(StockRequestType.RESERVATION);
        StockReservation reservation = stockClient.reserve(line.materialId(), line.warehouseId(), project, line.plannedQuantity(), key);
        line.markReserved(reservation.id());
        LOGGER.info("Material reserved in stock: order={}, material={}, reservation={}", line.orderCode(), line.materialCode(), reservation.id());
    }

    /**
     * Repite la reserva que la linea tiene en duda, con su clave y su cuerpo: si llego a stock, stock
     * devuelve la que creo; si no, la crea ahora. Si stock dice que no, es que no llego -la habria
     * devuelto sin validar nada-, y la linea sigue como si no se hubiera pedido. Un 409 IDEM-001 dice
     * lo contrario, que la clave ya creo algo con otro cuerpo: se deja pasar, y la linea lo cuenta.
     */
    private void confirmReservation(StockLine line) {
        UUID project = line.stockProjectId();
        if (project == null) {
            throw new IllegalStateException("Material line " + line.materialCode() + " has a reservation in doubt but order "
                    + line.orderCode() + " has no stock project to repeat it against");
        }
        try {
            requestReservation(line, project);
        } catch (StockRejectedException rejected) {
            if (rejected.isIdempotencyConflict()) {
                LOGGER.error("The idempotency key of the reservation in doubt was used with another body: order={}, material={}, cause={}",
                        line.orderCode(), line.materialCode(), rejected.getMessage());
                throw rejected;
            }
            line.clearInDoubt();
            LOGGER.info("The reservation in doubt never reached stock, which now rejects it: order={}, material={}, cause={}",
                    line.orderCode(), line.materialCode(), rejected.getMessage());
        }
    }

    private void settle(StockLine line) {
        if (line.inDoubt() == StockRequestType.RESERVATION) {
            // La reserva sin respuesta puede estar reteniendo material: primero se confirma, y luego se
            // consume o se libera como cualquier otra. Sin esto, lo usado salia como salida directa y
            // la reserva se quedaba en stock.
            confirmReservation(line);
        }
        Optional<StockReservation> reservation = currentReservation(line);
        if (reservation.filter(StockReservation::isActive).isPresent()) {
            settleActive(line, reservation.get());
        } else if (reservation.filter(StockReservation::isConsumed).isPresent()) {
            settleConsumed(line, reservation.get());
        } else if (line.consumedQuantity().signum() > 0) {
            // Sin reserva que retenga nada: lo usado sale como salida directa.
            output(line, line.consumedQuantity(), directOutputNotes(line));
            line.markConsumed();
        } else if (line.reservationId() != null) {
            line.markReleased();
        } else {
            line.markNotRequested();
        }
    }

    private void settleActive(StockLine line, StockReservation reservation) {
        BigDecimal used = line.consumedQuantity();
        BigDecimal reserved = reserved(line, reservation);
        if (used.signum() == 0) {
            line.checkOpen();
            stockClient.release(reservation.id());
            line.markReleased();
            return;
        }
        int comparison = used.compareTo(reserved);
        if (comparison < 0) {
            // mto-stock no consume parte de una reserva: se libera entera y sale lo usado.
            line.checkOpen();
            stockClient.release(reservation.id());
            output(line, used, directOutputNotes(line));
        } else {
            line.checkOpen();
            stockClient.consume(reservation.id());
            if (comparison > 0) {
                output(line, used.subtract(reserved), "Over-consumption beyond reservation " + reservation.id());
            }
        }
        line.markConsumed();
    }

    /** La reserva ya se consumio (en un intento anterior o desde Almacen): queda la salida de lo usado por encima. */
    private void settleConsumed(StockLine line, StockReservation reservation) {
        BigDecimal excess = line.consumedQuantity().subtract(reserved(line, reservation));
        if (excess.signum() > 0) {
            output(line, excess, "Over-consumption beyond reservation " + reservation.id());
        } else if (excess.signum() < 0) {
            LOGGER.warn("Reservation {} was consumed in stock for more than the {} {} used: order={}, material={}",
                    reservation.id(), line.consumedQuantity(), line.unit(), line.orderCode(), line.materialCode());
        }
        line.markConsumed();
    }

    private void free(StockLine line) {
        if (line.inDoubt() == StockRequestType.OUTPUT) {
            // Lo usado salio, o sale ahora: la salida que se quedo sin respuesta se termina, y la linea
            // queda consumida como en stock. Liberar no la deshace.
            settle(line);
            return;
        }
        if (line.inDoubt() == StockRequestType.RESERVATION) {
            confirmReservation(line);
        }
        Optional<StockReservation> reservation = currentReservation(line);
        if (reservation.filter(StockReservation::isActive).isPresent()) {
            line.checkOpen();
            stockClient.release(reservation.get().id());
            line.markReleased();
        } else if (reservation.filter(StockReservation::isConsumed).isPresent()) {
            // Se consumio antes de cancelar: el material ya salio del almacen, y la linea lo dice.
            line.markConsumed();
        } else if (line.reservationId() != null) {
            line.markReleased();
        } else {
            line.markNotRequested();
        }
    }

    /**
     * La reserva de la linea tal como la tiene stock, si la linea tiene una que pueda seguir viva;
     * vacio si no, o si stock no la conoce. Un estado que este servicio no conoce no se interpreta:
     * la linea queda FAILED con el.
     */
    private Optional<StockReservation> currentReservation(StockLine line) {
        StockSyncStatus status = line.status();
        UUID reservationId = line.reservationId();
        if (reservationId == null || status == StockSyncStatus.RELEASED || status == StockSyncStatus.CONSUMED) {
            return Optional.empty();
        }
        line.checkOpen();
        Optional<StockReservation> reservation = stockClient.findReservation(reservationId);
        if (reservation.isPresent() && !reservation.get().isActive() && !reservation.get().isConsumed() && !reservation.get().holdsNothing()) {
            throw new StockUnavailableException("mto-stock reports reservation " + reservationId + " as "
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
    private void output(StockLine line, BigDecimal quantity, String notes) {
        String key = idempotencyKey(line.id(), line.materialCode(), StockRequestType.OUTPUT, null);
        line.beginRequest(StockRequestType.OUTPUT);
        stockClient.output(line.materialId(), line.warehouseId(), line.stockProjectId(), quantity, line.orderCode(), notes, key);
    }

    /**
     * Las notas de la salida de lo usado cuando ninguna reserva lo cubre. Son las mismas por los dos
     * caminos que llegan a ella -la reserva se libera en este paso porque se uso menos, o ya no
     * retenia nada- porque stock compara el cuerpo del reintento con el del primero: si la salida se
     * queda sin respuesta despues de liberar, el reintento llega por el segundo camino y tiene que
     * mandar lo mismo, o stock lo rechazaria con 409 IDEM-001.
     */
    private static String directOutputNotes(StockLine line) {
        String order = "Maintenance order " + line.orderCode();
        return line.reservationId() == null ? order : order + ", reservation " + line.reservationId() + " not consumed";
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
        return idempotencyKey(usage.getId(), usage.getMaterialCode(), request, previousReservation);
    }

    private static String idempotencyKey(UUID lineId, String materialCode, StockRequestType request, UUID previousReservation) {
        if (lineId == null) {
            throw new IllegalStateException("Material line " + materialCode + " has no id yet: save it before asking stock for anything");
        }
        String key = "mto-maintenance:" + lineId;
        return request == StockRequestType.RESERVATION
                ? key + ":reserve:" + (previousReservation == null ? "first" : previousReservation)
                : key + ":output";
    }

    /** Lo que stock tiene reservado, que desde Almacen se puede haber cambiado; lo previsto si no lo dice. */
    private static BigDecimal reserved(StockLine line, StockReservation reservation) {
        return reservation.quantity() == null ? line.plannedQuantity() : reservation.quantity();
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

    private static RuntimeException rethrowable(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(failure);
    }

    /**
     * Deja en la linea lo que contesto stock y lo publica, pero solo si cambia lo que la linea contaba
     * antes del paso ({@code before}). El reintento automatico repite cada 5 minutos el paso de una
     * linea FAILED mientras stock siga caido, y un {@code sync} se puede pedir muchas veces; cada evento
     * lleva un operationId nuevo, asi que sin esto el consumidor recibia un aviso por vuelta.
     */
    private void record(MaintenanceMaterialUsage usage, String step, RuntimeException exception, LineState before) {
        if (exception instanceof StockRejectedException rejection) {
            usage.markRejected(step + ": " + exception.getMessage());
            LOGGER.warn("Stock rejected the {} of a material line, left as REJECTED: order={}, material={}, cause={}",
                    step, usage.getOrder().getCode(), usage.getMaterialCode(), exception.getMessage());
            if (before.changedIn(usage)) {
                events.publish(MaintenanceEvents.materialRejected(usage, step, rejection));
            }
            return;
        }
        usage.markFailed(step + ": " + exception.getMessage());
        LOGGER.warn("Stock {} failed, material line left as FAILED: order={}, material={}, cause={}",
                step, usage.getOrder().getCode(), usage.getMaterialCode(), exception.getMessage());
        if (before.changedIn(usage)) {
            // markFailed conserva la peticion en duda: si la hay, lo que no se sabe es si stock la aplico.
            events.publish(usage.isInDoubt()
                    ? MaintenanceEvents.materialInDoubt(usage, step, exception)
                    : MaintenanceEvents.materialFailed(usage, step, exception));
        }
    }

    /** Lo que la linea contaba de su conversacion con stock antes de un paso. */
    private record LineState(StockSyncStatus status, StockRequestType inDoubt, String error) {

        static LineState of(MaintenanceMaterialUsage usage) {
            return new LineState(usage.getStockSyncStatus(), usage.getStockRequestInDoubt(), usage.getStockSyncError());
        }

        /**
         * Si el paso dejo la linea contando otra cosa. Un rechazo cuenta tambien su motivo: otro motivo
         * es otra cosa que arreglar. Una caida no, porque su texto cambia (conexion rechazada, tiempo
         * agotado, circuito abierto) sin decir nada nuevo; si cambia la peticion en duda, si.
         */
        boolean changedIn(MaintenanceMaterialUsage usage) {
            if (status != usage.getStockSyncStatus()) {
                return true;
            }
            return status == StockSyncStatus.REJECTED
                    ? !Objects.equals(error, usage.getStockSyncError())
                    : inDoubt != usage.getStockRequestInDoubt();
        }
    }
}
