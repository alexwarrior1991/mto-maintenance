package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.operation.OperationError;
import com.alejandro.mtomaintenance.application.dto.operation.OperationResponse;
import com.alejandro.mtomaintenance.application.dto.operation.OperationStatus;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.exception.TooManyOperationsException;
import com.alejandro.mtomaintenance.application.service.BudgetedOperations;
import com.alejandro.mtomaintenance.application.service.OperationErrorTranslator;
import com.alejandro.mtomaintenance.configuration.AuditActorResolver;
import com.alejandro.mtomaintenance.configuration.async.AsyncConfiguration;
import com.alejandro.mtomaintenance.configuration.async.OperationsProperties;
import com.alejandro.mtomaintenance.configuration.security.CurrentUserService;
import com.alejandro.mtomaintenance.configuration.security.SecurityRoles;
import com.alejandro.mtomaintenance.infrastructure.persistence.audit.AuditRevisionListener;
import com.alejandro.mtomaintenance.infrastructure.persistence.audit.BackgroundOperationContext;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.BackgroundOperation;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.BackgroundOperationRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Las operaciones de fondo de {@link BudgetedOperations}.
 *
 * <p>El trabajo corre en el executor de hilos virtuales de {@link AsyncConfiguration}, que le pasa el
 * {@code SecurityContext} y el MDC de la peticion; lo que la peticion ya no puede dar (su cabecera de
 * correlacion, su URI) viaja en {@link BackgroundOperationContext}. La peticion espera al
 * {@link CompletableFuture} hasta su presupuesto, y un hilo virtual que espera no cuesta nada.</p>
 *
 * <p>Ninguna espera retiene una conexion: apuntar, arrancar, latir y terminar son sentencias cortas, cada
 * una en su transaccion. Las de un mismo ambito hacen fila en esta instancia en {@link OperationScopeLocks},
 * sin conexion y sin mirar la base de datos; con el turno local piden el de la base de datos, que entre
 * replicas es el que manda. Solo si otra replica tiene el ambito sueltan el cerrojo y vuelven a mirar con
 * espera creciente.</p>
 */
@Service
class BudgetedOperationsImpl implements BudgetedOperations {

    private static final Logger LOGGER = LoggerFactory.getLogger(BudgetedOperationsImpl.class);

    private static final long CAPACITY_LOCK = AdvisoryLockKeys.of("mto-maintenance:background-operation:capacity");
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_URI_LENGTH = 500;
    /** Cuantas veces se intenta apuntar el resultado de un trabajo hecho si la base de datos no contesta. */
    private static final int RECORD_ATTEMPTS = 3;

    private final BackgroundOperationRepository repository;
    private final AsyncTaskExecutor executor;
    private final TransactionTemplate transactions;
    private final OperationErrorTranslator errorTranslator;
    private final CurrentUserService currentUserService;
    private final OperationsProperties properties;
    private final JsonMapper jsonMapper;
    private final OperationScopeLocks scopeLocks;

    /** Las operaciones vivas de este proceso, las que late {@link #heartbeat()}. */
    private final Set<UUID> alive = ConcurrentHashMap.newKeySet();

    BudgetedOperationsImpl(BackgroundOperationRepository repository,
                           @Qualifier(AsyncConfiguration.TASK_EXECUTOR) AsyncTaskExecutor executor,
                           PlatformTransactionManager transactionManager,
                           OperationErrorTranslator errorTranslator,
                           CurrentUserService currentUserService,
                           OperationsProperties properties,
                           JsonMapper jsonMapper,
                           OperationScopeLocks scopeLocks) {
        this.repository = repository;
        this.executor = executor;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.errorTranslator = errorTranslator;
        this.currentUserService = currentUserService;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.scopeLocks = scopeLocks;
    }

    @Override
    public <T> Outcome<T> run(String kind, String scope, int successStatus, Duration wait, Supplier<T> work) {
        UUID id = UUID.randomUUID();
        BackgroundOperationContext.Context context = captureRequest(id);
        Instant now = Instant.now();

        Integer inserted = transactions.execute(status -> {
            repository.lockCapacity(CAPACITY_LOCK);
            return repository.insertIfRoom(id, kind, scope, AuditActorResolver.currentActor(), context.correlationId(),
                    context.requestMethod(), context.requestUri(), now, now.minus(properties.staleAfter()), properties.maxActive());
        });
        if (inserted == null || inserted == 0) {
            throw new TooManyOperationsException("There are already " + properties.maxActive()
                    + " background operations running; try again in a few seconds", Math.max(1, properties.defaultWait().toSeconds()));
        }
        alive.add(id);

        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> execute(id, scope, successStatus, context, work, future));
        Duration budget = properties.effectiveWait(wait);

        try {
            // Sin espera: lo que no ha terminado ya es 202. Lo terminado se lee con get(), que no espera y
            // lanza el fallo como el plazo (getNow lo lanzaria envuelto en CompletionException, un 500, y
            // mirar isDone despues de getNow podia dar por terminada sin resultado una que acababa entre medias).
            if (budget.isZero() && !future.isDone()) {
                return new Outcome<>(response(load(id)), null, false);
            }
            T result = budget.isZero() ? future.get() : future.get(budget.toMillis(), TimeUnit.MILLISECONDS);
            return new Outcome<>(response(load(id)), result, true);
        } catch (TimeoutException pending) {
            LOGGER.info("Background operation {} {} answered 202 after {}: it keeps running", kind, id, budget);
            return new Outcome<>(response(load(id)), null, false);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Outcome<>(response(load(id)), null, false);
        } catch (ExecutionException failed) {
            throw rethrowable(failed.getCause());
        }
    }

    private <T> void execute(UUID id, String scope, int successStatus, BackgroundOperationContext.Context context,
                             Supplier<T> work, CompletableFuture<T> future) {
        BackgroundOperationContext.set(context);
        try {
            retryUntil(() -> runIfItsTurn(id, scope, successStatus, work, future));
        } catch (Throwable failure) {
            // No llego a trabajar: la base de datos, una interrupcion esperando o ya no estaba en cola. Con la
            // interrupcion puesta el pool no daria la conexion de markFailed: se quita para apuntarlo y se repone.
            boolean interrupted = Thread.interrupted();
            fail(id, failure, future);
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        } finally {
            alive.remove(id);
            BackgroundOperationContext.clear();
        }
    }

    /**
     * Un intento, con el turno local de su ambito (sin ambito no hay fila): arranca si la base de datos dice
     * que le toca, hace el trabajo y lo deja apuntado antes de soltar el cerrojo, para que la siguiente de
     * la instancia arranque a la primera. {@code false} si no le toca, que con el turno local solo pasa si
     * otra replica tiene el ambito: entonces suelta el cerrojo.
     */
    private <T> boolean runIfItsTurn(UUID id, String scope, int successStatus, Supplier<T> work, CompletableFuture<T> future) {
        Supplier<Boolean> attempt = () -> {
            if (!start(id, scope)) {
                requireQueued(id);
                return false;
            }
            run(id, successStatus, work, future);
            return true;
        };
        return scope == null ? attempt.get() : scopeLocks.withLock(scope, attempt);
    }

    /** Una transaccion corta con el cerrojo del ambito: arranca la operacion si es su turno. */
    private boolean start(UUID id, String scope) {
        Instant now = Instant.now();
        Integer started = transactions.execute(status -> {
            if (scope != null) {
                repository.lockScope(scopeKey(scope));
            }
            return repository.startIfTurn(id, now, now.minus(properties.staleAfter()));
        });
        return started != null && started == 1;
    }

    private void requireQueued(UUID id) {
        OperationStatus current = repository.findById(id).map(BackgroundOperation::getStatus).orElse(OperationStatus.FAILED);
        if (current != OperationStatus.QUEUED) {
            throw new IllegalStateException("Background operation " + id + " can no longer start: it is " + current);
        }
    }

    private <T> void run(UUID id, int successStatus, Supplier<T> work, CompletableFuture<T> future) {
        T result;
        String body;
        try {
            result = work.get();
            body = jsonMapper.writeValueAsString(result);
        } catch (Throwable failure) {
            fail(id, failure, future, true);
            return;
        }
        // Desde aqui el trabajo esta hecho y confirmado: nada de lo que pase al apuntarlo lo convierte en un fallo.
        try {
            succeed(id, successStatus, body);
        } finally {
            future.complete(result);
        }
    }

    /**
     * Apunta el resultado mirando el recuento de filas. El trabajo ya esta hecho y confirmado en sus propias
     * transacciones, asi que su resultado es la verdad: si entretanto otra replica (o esta, con el latido
     * retrasado) la dio por abandonada, la operacion pasa a {@code SUCCEEDED} igualmente, y lo que queda del
     * abandono es su {@code abandoned_at}, que la respuesta muestra, y este aviso. Dejarla {@code FAILED} haria
     * que el cliente repitiera un trabajo hecho.
     *
     * <p>Si la base de datos no contesta al apuntarlo se reintenta unas pocas veces; nunca se apunta como
     * fallida por eso. Si no hay manera, la fila se queda {@code RUNNING} sin latido y acabara en
     * {@code OPS-410}: es lo unico que no se puede evitar sin base de datos, y queda dicho en el log.</p>
     */
    private void succeed(UUID id, int successStatus, String result) {
        Duration pause = properties.pollInitial();
        for (int attempt = 1; ; attempt++) {
            try {
                if (repository.markSucceeded(id, successStatus, result, Instant.now()) == 1) {
                    return;
                }
                if (repository.markSucceededAfterAbandon(id, successStatus, result, Instant.now()) == 1) {
                    LOGGER.warn("Background operation {} had been given up for abandoned (its heartbeat was late) but its "
                            + "work finished: it is recorded as succeeded. Another one of its scope may have run meanwhile", id);
                    return;
                }
                LOGGER.error("Background operation {} finished its work but its row could not be marked succeeded: "
                        + "it is no longer running nor abandoned (purged?)", id);
                return;
            } catch (RuntimeException unrecorded) {
                if (attempt == RECORD_ATTEMPTS) {
                    LOGGER.error("Background operation {} finished its work but its result could not be recorded after {} "
                            + "attempts: its row will end as abandoned (OPS-410) although the work is done", id, attempt, unrecorded);
                    return;
                }
                LOGGER.warn("Background operation {} finished its work but recording it failed (attempt {}): {}", id, attempt,
                        unrecorded.getMessage());
                try {
                    Thread.sleep(pause);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    LOGGER.error("Background operation {} finished its work but was interrupted before recording it", id);
                    return;
                }
                pause = longer(pause);
            }
        }
    }

    private void fail(UUID id, Throwable failure, CompletableFuture<?> future) {
        fail(id, failure, future, false);
    }

    /**
     * @param worked si el fallo es del trabajo: entonces su error sustituye al {@code OPS-410} de una que se dio
     *               por abandonada mientras trabajaba. La que no llego a trabajar se queda como este.
     */
    private void fail(UUID id, Throwable failure, CompletableFuture<?> future, boolean worked) {
        try {
            OperationError error = errorTranslator.translate(failure);
            LOGGER.warn("Background operation {} failed with {} {}: {}", id, error.status(), error.errorCode(), error.message());
            String message = truncate(error.message(), MAX_MESSAGE_LENGTH);
            if (repository.markFailed(id, error.status(), error.errorCode(), message, Instant.now()) == 0 && worked
                    && repository.markFailedAfterAbandon(id, error.status(), error.errorCode(), message, Instant.now()) == 1) {
                LOGGER.warn("Background operation {} had been given up for abandoned but its work went on and failed: "
                        + "its own error replaces OPS-410", id);
            }
        } finally {
            // Tambien si no se pudo apuntar: quien espera recibe el fallo del trabajo, no un 202 de algo que ya acabo.
            future.completeExceptionally(failure);
        }
    }

    /**
     * Repite {@code attempt} hasta que diga que si, sin conexion entre uno y otro: el hilo virtual duerme con
     * espera creciente ({@code poll-initial} doblando hasta {@code poll-max}), que no cuesta nada.
     */
    private void retryUntil(BooleanSupplier attempt) {
        for (Duration pause = properties.pollInitial(); !attempt.getAsBoolean(); pause = longer(pause)) {
            try {
                Thread.sleep(pause);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the turn of a background operation", interrupted);
            }
        }
    }

    private Duration longer(Duration pause) {
        Duration doubled = pause.multipliedBy(2);
        return doubled.compareTo(properties.pollMax()) > 0 ? properties.pollMax() : doubled;
    }

    @Override
    public OperationResponse find(UUID id) {
        BackgroundOperation operation = repository.findById(id)
                .filter(this::visibleToCurrentUser)
                .orElseThrow(() -> new NotFoundException("Background operation", id));
        return response(operation);
    }

    @Override
    public void heartbeat() {
        if (!alive.isEmpty()) {
            repository.heartbeat(Set.copyOf(alive), Instant.now());
        }
    }

    @Override
    public int failAbandoned() {
        // Primero late por las suyas: si el latido se retraso (el planificador ocupado, una pausa larga) y esta
        // revision llega antes que el, este proceso daria por abandonado lo que el mismo esta haciendo.
        heartbeat();
        Instant now = Instant.now();
        int abandoned = repository.failAbandoned(now.minus(properties.staleAfter()), now);
        if (abandoned > 0) {
            LOGGER.warn("{} background operations were abandoned: their instance stopped beating", abandoned);
        }
        return abandoned;
    }

    @Override
    public int purgeFinished() {
        Instant before = Instant.now().minus(properties.retention());
        int total = 0;
        int deleted;
        do {
            deleted = repository.purgeFinished(before, properties.purgeBatchSize());
            total += deleted;
        } while (deleted == properties.purgeBatchSize());
        return total;
    }

    private boolean visibleToCurrentUser(BackgroundOperation operation) {
        String username = currentUserService.getUsername().orElse(null);
        return operation.getRequestedBy().equals(username) || currentUserService.hasRole(SecurityRoles.MAINTENANCE_SUPERVISE)
                || currentUserService.hasRole(SecurityRoles.OPS_WRITE);
    }

    private BackgroundOperation load(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Background operation", id));
    }

    private OperationResponse response(BackgroundOperation operation) {
        OperationError error = operation.getErrorCode() == null ? null
                : new OperationError(operation.getErrorStatus(), operation.getErrorCode(), operation.getErrorMessage());
        Object result = operation.getResult() == null ? null : jsonMapper.readValue(operation.getResult(), Object.class);
        return new OperationResponse(operation.getId(), operation.getKind(), operation.getScope(), operation.getStatus(),
                operation.getRequestedBy(), operation.getCreatedAt(), operation.getStartedAt(), operation.getFinishedAt(),
                operation.getResultStatus(), result, error, operation.getAbandonedAt());
    }

    private static BackgroundOperationContext.Context captureRequest(UUID id) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return new BackgroundOperationContext.Context(id, null, null, null, null, null);
        }
        HttpServletRequest request = attributes.getRequest();
        return new BackgroundOperationContext.Context(id,
                request.getHeader("X-Correlation-Id"),
                request.getMethod(),
                truncate(request.getRequestURI(), MAX_URI_LENGTH),
                AuditRevisionListener.clientAddress(request),
                request.getHeader("User-Agent"));
    }

    private static long scopeKey(String scope) {
        return AdvisoryLockKeys.of("mto-maintenance:background-operation:scope:" + scope);
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

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
