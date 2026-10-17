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
 * una en su transaccion, y una operacion en cola que no tiene turno suelta la suya y duerme con espera
 * creciente antes de volver a mirar.</p>
 */
@Service
class BudgetedOperationsImpl implements BudgetedOperations {

    private static final Logger LOGGER = LoggerFactory.getLogger(BudgetedOperationsImpl.class);

    private static final long CAPACITY_LOCK = "mto-maintenance:background-operation:capacity".hashCode();
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_URI_LENGTH = 500;

    private final BackgroundOperationRepository repository;
    private final AsyncTaskExecutor executor;
    private final TransactionTemplate transactions;
    private final OperationErrorTranslator errorTranslator;
    private final CurrentUserService currentUserService;
    private final OperationsProperties properties;
    private final JsonMapper jsonMapper;

    /** Las operaciones vivas de este proceso, las que late {@link #heartbeat()}. */
    private final Set<UUID> alive = ConcurrentHashMap.newKeySet();

    BudgetedOperationsImpl(BackgroundOperationRepository repository,
                           @Qualifier(AsyncConfiguration.TASK_EXECUTOR) AsyncTaskExecutor executor,
                           PlatformTransactionManager transactionManager,
                           OperationErrorTranslator errorTranslator,
                           CurrentUserService currentUserService,
                           OperationsProperties properties,
                           JsonMapper jsonMapper) {
        this.repository = repository;
        this.executor = executor;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.errorTranslator = errorTranslator;
        this.currentUserService = currentUserService;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
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
            T result = budget.isZero()
                    ? future.getNow(null)
                    : future.get(budget.toMillis(), TimeUnit.MILLISECONDS);
            if (future.isDone() && !future.isCompletedExceptionally()) {
                return new Outcome<>(response(load(id)), result, true);
            }
            return new Outcome<>(response(load(id)), null, false);
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
            awaitTurn(id, scope);
            T result = work.get();
            repository.markSucceeded(id, successStatus, jsonMapper.writeValueAsString(result), Instant.now());
            future.complete(result);
        } catch (Throwable failure) {
            OperationError error = errorTranslator.translate(failure);
            LOGGER.warn("Background operation {} failed with {} {}: {}", id, error.status(), error.errorCode(), error.message());
            repository.markFailed(id, error.status(), error.errorCode(), truncate(error.message(), MAX_MESSAGE_LENGTH), Instant.now());
            future.completeExceptionally(failure);
        } finally {
            alive.remove(id);
            BackgroundOperationContext.clear();
        }
    }

    /**
     * Espera su turno sin retener conexion: cada mirada es una transaccion corta con el cerrojo del ambito,
     * y entre una y otra el hilo virtual duerme, que no cuesta nada.
     */
    private void awaitTurn(UUID id, String scope) throws InterruptedException {
        Duration pause = properties.pollInitial();
        while (true) {
            Instant now = Instant.now();
            Integer started = transactions.execute(status -> {
                if (scope != null) {
                    repository.lockScope(scopeKey(scope));
                }
                return repository.startIfTurn(id, now, now.minus(properties.staleAfter()));
            });
            if (started != null && started == 1) {
                return;
            }
            OperationStatus current = repository.findById(id).map(BackgroundOperation::getStatus).orElse(OperationStatus.FAILED);
            if (current != OperationStatus.QUEUED) {
                throw new IllegalStateException("Background operation " + id + " can no longer start: it is " + current);
            }
            Thread.sleep(pause);
            Duration doubled = pause.multipliedBy(2);
            pause = doubled.compareTo(properties.pollMax()) > 0 ? properties.pollMax() : doubled;
        }
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
                operation.getResultStatus(), result, error);
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
        return ("mto-maintenance:background-operation:scope:" + scope).hashCode();
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
