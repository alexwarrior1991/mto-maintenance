package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.operation.OperationError;
import com.alejandro.mtomaintenance.application.dto.operation.OperationResponse;
import com.alejandro.mtomaintenance.application.dto.operation.OperationStatus;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.exception.ShiftException;
import com.alejandro.mtomaintenance.application.exception.TooManyOperationsException;
import com.alejandro.mtomaintenance.application.service.BudgetedOperations;
import com.alejandro.mtomaintenance.configuration.AuditActorResolver;
import com.alejandro.mtomaintenance.configuration.async.AsyncConfiguration;
import com.alejandro.mtomaintenance.configuration.async.OperationsProperties;
import com.alejandro.mtomaintenance.configuration.security.CurrentUserService;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.BackgroundOperationRepository;
import com.alejandro.mtomaintenance.support.PostgreSQLTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las operaciones de fondo de {@link BudgetedOperations} contra PostgreSQL: el tope y el turno de cada
 * ambito se deciden en {@code background_operation}, dentro de la sentencia que escribe, asi que solo
 * una base real dice si funcionan. Copia de los casos de mto-resources, como los tests del outbox.
 *
 * <p>Sin la transaccion del test a proposito: cada paso de una operacion es su propia transaccion
 * corta, y el trabajo corre en un hilo virtual que no veria lo que el test no ha confirmado.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BudgetedOperationsDataJpaTest extends PostgreSQLTestContainer {

    /** El tope de este test: dos de un ambito y dos sin ambito lo llenan, la quinta sobra. */
    private static final int MAX_ACTIVE = 4;

    @DynamicPropertySource
    static void postgreSQLProperties(DynamicPropertyRegistry registry) {
        registerPostgreSQLProperties(registry);
    }

    @Autowired
    private BackgroundOperationRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private BudgetedOperations operations;

    /** Otra replica del despliegue: la que mira los latidos de las demas. La propia late antes de mirar. */
    private BudgetedOperations otherReplica;

    @BeforeEach
    void operations() {
        // Lo que dejo una pasada anterior no cuenta para el tope de esta.
        jdbc.update("delete from background_operation");
        operations = replica(repository);
        otherReplica = replica(repository);
    }

    /** Una replica: sus operaciones vivas son solo las que ella misma lleva. */
    private BudgetedOperations replica(BackgroundOperationRepository operationRepository) {
        OperationsProperties properties = new OperationsProperties(Duration.ofSeconds(5), Duration.ofSeconds(10), MAX_ACTIVE, null,
                Duration.ofMinutes(1), Duration.ofMillis(20), Duration.ofMillis(100), null, 0, false);
        return new BudgetedOperationsImpl(operationRepository, new AsyncConfiguration().applicationTaskExecutor(), transactionManager,
                failure -> failure instanceof ShiftException
                        ? new OperationError(409, "SHF-001", failure.getMessage())
                        : new OperationError(500, "APP-500", failure.getMessage()),
                new CurrentUserService(), properties, JsonMapper.builder().build(), new OperationScopeLocks());
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aSlowOperationAnswersPendingAndFinishesInTheBackgroundWithTheCallersIdentity() {
        authenticateAs("planner");
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> actor = new AtomicReference<>();

        BudgetedOperations.Outcome<Map<String, Object>> outcome = operations.run("GENERATE_TASKS", "EP-" + System.nanoTime(), 200,
                Duration.ofMillis(50), () -> {
                    await(release);
                    actor.set(AuditActorResolver.currentActor());
                    return Map.of("createdTasks", 3);
                });

        assertFalse(outcome.completed());
        assertNotNull(outcome.operation().id());
        release.countDown();
        OperationResponse finished = waitFor(outcome.operation().id());
        assertEquals(OperationStatus.SUCCEEDED, finished.status());
        assertEquals(200, finished.resultStatus());
        assertEquals(Map.of("createdTasks", 3), finished.result(), "The same body the synchronous call would have given");
        assertEquals("planner", actor.get(), "The SecurityContext reaches the virtual thread");
        assertEquals("planner", finished.requestedBy());
        authenticateAs("someone.else");
        assertThrows(NotFoundException.class, () -> operations.find(outcome.operation().id()), "Only to whom asked for it");
        authenticateAs("supervisor", "ROLE_MAINTENANCE_SUPERVISE");
        assertEquals(OperationStatus.SUCCEEDED, operations.find(outcome.operation().id()).status(), "Or to whom supervises");
    }

    @Test
    void aQuickOperationAnswersWithItsResultAndAFailureIsTheSameAsTheSynchronousCall() {
        authenticateAs("planner");
        BudgetedOperations.Outcome<String> quick = operations.run("GENERATE_TASKS", null, 200, null, () -> "ok");
        assertTrue(quick.completed());
        assertEquals("ok", quick.result());
        assertThrows(ShiftException.class, () -> operations.run("GENERATE_TASKS", null, 200, Duration.ofSeconds(5), () -> {
            throw new ShiftException("no");
        }));

        CountDownLatch release = new CountDownLatch(1);
        UUID late = operations.run("GENERATE_TASKS", null, 200, Duration.ZERO, () -> {
            await(release);
            throw new ShiftException("Shift SH-000001 cannot take it");
        }).operation().id();
        release.countDown();
        OperationResponse failed = waitFor(late);
        assertEquals(OperationStatus.FAILED, failed.status());
        assertEquals(new OperationError(409, "SHF-001", "Shift SH-000001 cannot take it"), failed.error());
    }

    /**
     * Sin espera ({@code wait=0}) y con el trabajo ya terminado al mirar (aqui corre en el mismo hilo): su
     * resultado, o su fallo como el de la llamada sincrona; nunca un 500 por el fallo envuelto, ni un 200
     * sin cuerpo.
     */
    @Test
    void withoutWaitAnOperationAlreadyOverAnswersItsResultOrItsFailure() {
        authenticateAs("planner");
        OperationsProperties properties = new OperationsProperties(Duration.ofSeconds(5), Duration.ofSeconds(10), MAX_ACTIVE, null,
                Duration.ofMinutes(1), Duration.ofMillis(20), Duration.ofMillis(100), null, 0, false);
        BudgetedOperations inline = new BudgetedOperationsImpl(repository, new org.springframework.core.task.support.TaskExecutorAdapter(Runnable::run),
                transactionManager, failure -> failure instanceof ShiftException
                        ? new OperationError(409, "SHF-001", failure.getMessage())
                        : new OperationError(500, "APP-500", failure.getMessage()),
                new CurrentUserService(), properties, JsonMapper.builder().build(), new OperationScopeLocks());

        BudgetedOperations.Outcome<String> done = inline.run("GENERATE_TASKS", null, 200, Duration.ZERO, () -> "ok");
        assertTrue(done.completed());
        assertEquals("ok", done.result());
        ShiftException failed = assertThrows(ShiftException.class, () -> inline.run("GENERATE_TASKS", null, 200, Duration.ZERO, () -> {
            throw new ShiftException("Shift SH-000002 cannot take it");
        }));
        assertEquals("Shift SH-000002 cannot take it", failed.getMessage());
    }

    @Test
    void twoOperationsOfTheSamePackageDoNotRunAtOnceAndTheCapSaysTooMany() throws Exception {
        authenticateAs("planner");
        String scope = "EP-" + System.nanoTime();
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            ids.add(operations.run("GENERATE_TASKS", scope, 200, Duration.ZERO, () -> {
                maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                await(release);
                running.decrementAndGet();
                return "ok";
            }).operation().id());
        }
        List<UUID> fillers = new ArrayList<>();
        for (int i = 0; i < MAX_ACTIVE - 2; i++) {
            fillers.add(operations.run("GENERATE_TASKS", null, 200, Duration.ZERO, () -> {
                await(release);
                return "ok";
            }).operation().id());
        }
        TooManyOperationsException tooMany = assertThrows(TooManyOperationsException.class,
                () -> operations.run("GENERATE_TASKS", null, 200, Duration.ZERO, () -> "late"));
        assertTrue(tooMany.getRetryAfterSeconds() >= 1);

        Thread.sleep(300);
        release.countDown();
        for (UUID id : ids) {
            assertEquals(OperationStatus.SUCCEEDED, waitFor(id).status());
        }
        fillers.forEach(this::waitFor);
        assertEquals(1, maxRunning.get(), "One at a time in a package");
    }

    @Test
    void anOperationWhoseHeartbeatStoppedIsFailed() {
        authenticateAs("planner");
        CountDownLatch release = new CountDownLatch(1);
        UUID id = operations.run("GENERATE_TASKS", "EP-" + System.nanoTime(), 200, Duration.ZERO, () -> {
            await(release);
            return "late";
        }).operation().id();
        waitUntil(() -> operations.find(id).status() == OperationStatus.RUNNING);
        jdbc.update("update background_operation set heartbeat_at = now() - interval '1 hour' where id = ?", id);

        assertEquals(0, operations.failAbandoned(), "Its own replica beats for it before looking: it is alive");
        jdbc.update("update background_operation set heartbeat_at = now() - interval '1 hour' where id = ?", id);
        assertTrue(otherReplica.failAbandoned() >= 1);
        OperationResponse failed = operations.find(id);
        assertEquals(OperationStatus.FAILED, failed.status());
        assertEquals("OPS-410", failed.error().errorCode());
        release.countDown();
        // El trabajo liberado termina y su resultado gana al abandono, con su finished_at de ahora: hasta
        // que lo escriba no se envejece la fila, o lo pisaria y la purga no encontraria nada.
        waitUntil(() -> operations.find(id).status() == OperationStatus.SUCCEEDED);
        jdbc.update("update background_operation set finished_at = now() - interval '30 days' where id = ?", id);
        assertTrue(operations.purgeFinished() >= 1, "What finished long ago is purged");
    }

    /**
     * El latido se para (el planificador ocupado, una pausa larga, la base de datos sin contestar un rato)
     * y otra replica da la operacion por abandonada, pero su trabajo sigue y termina, confirmado. Lo que
     * paso es lo que hizo el trabajo: la operacion acaba con su resultado, no con un {@code OPS-410} que
     * haria repetir a quien la pidio algo ya hecho, y dice que se la dio por abandonada entre medias.
     */
    @Test
    void anOperationGivenUpAsAbandonedWhoseWorkStillEndsSaysWhatItsWorkDid() {
        authenticateAs("planner");
        CountDownLatch release = new CountDownLatch(1);
        UUID id = operations.run("GENERATE_TASKS", "EP-" + System.nanoTime(), 201, Duration.ZERO, () -> {
            await(release);
            return Map.of("createdTasks", 7);
        }).operation().id();
        waitUntil(() -> operations.find(id).status() == OperationStatus.RUNNING);
        jdbc.update("update background_operation set heartbeat_at = now() - interval '1 hour' where id = ?", id);
        assertEquals(1, otherReplica.failAbandoned());
        OperationResponse abandoned = operations.find(id);
        assertEquals("OPS-410", abandoned.error().errorCode());
        assertNotNull(abandoned.abandonedAt());

        release.countDown();
        waitUntil(() -> operations.find(id).status() == OperationStatus.SUCCEEDED);
        OperationResponse finished = operations.find(id);
        assertEquals(201, finished.resultStatus());
        assertEquals(Map.of("createdTasks", 7), finished.result());
        assertEquals(null, finished.error(), "The work ended: no trace of the error it never had");
        assertEquals(abandoned.abandonedAt(), finished.abandonedAt(), "But it says it was given up for a while");
        assertFalse(finished.finishedAt().isBefore(abandoned.finishedAt()));

        // Y al reves: terminada antes de que nadie mire su latido, ya no es de las vivas y se queda como esta.
        jdbc.update("update background_operation set heartbeat_at = now() - interval '1 hour' where id = ?", id);
        assertEquals(0, otherReplica.failAbandoned());
        assertEquals(OperationStatus.SUCCEEDED, operations.find(id).status());
    }

    /**
     * Lo mismo con un trabajo que falla: su error (un 409 que no se arregla repitiendo) es mas cierto que
     * el {@code OPS-410}. La que se abandono en cola y nunca llego a trabajar se queda en {@code OPS-410}.
     */
    @Test
    void anOperationGivenUpAsAbandonedWhoseWorkFailsSaysItsErrorAndOneThatNeverWorkedStaysAbandoned() throws Exception {
        authenticateAs("planner");
        String scope = "EP-" + System.nanoTime();
        CountDownLatch release = new CountDownLatch(1);
        UUID working = operations.run("GENERATE_TASKS", scope, 200, Duration.ZERO, () -> {
            await(release);
            throw new ShiftException("Shift SH-000003 cannot take it");
        }).operation().id();
        AtomicBoolean ran = new AtomicBoolean();
        UUID queued = operations.run("GENERATE_TASKS", scope, 200, Duration.ZERO, () -> {
            ran.set(true);
            return "never";
        }).operation().id();
        waitUntil(() -> operations.find(working).status() == OperationStatus.RUNNING);
        jdbc.update("update background_operation set heartbeat_at = now() - interval '1 hour' where id in (?, ?)", working, queued);
        assertEquals(2, otherReplica.failAbandoned());

        release.countDown();
        waitUntil(() -> !"OPS-410".equals(operations.find(working).error().errorCode()));
        OperationResponse failed = operations.find(working);
        assertEquals(OperationStatus.FAILED, failed.status());
        assertEquals(new OperationError(409, "SHF-001", "Shift SH-000003 cannot take it"), failed.error());
        assertNotNull(failed.abandonedAt());

        Thread.sleep(500);
        assertFalse(ran.get(), "Abandoned in the queue: it never starts");
        assertEquals("OPS-410", operations.find(queued).error().errorCode());
    }

    /**
     * El trabajo termino y no se pudo apuntar a la primera (la base de datos no contesto): se reintenta, y
     * la operacion no queda fallida con el error de apuntarla, que no es del trabajo.
     */
    @Test
    void aSuccessThatCouldNotBeRecordedAtFirstIsRecordedAndNeverTurnedIntoAFailure() {
        authenticateAs("planner");
        AtomicInteger attempts = new AtomicInteger();
        BackgroundOperationRepository flaky = (BackgroundOperationRepository) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{BackgroundOperationRepository.class}, (proxy, method, args) -> {
                    if (method.getName().equals("markSucceeded") && attempts.getAndIncrement() == 0) {
                        throw new org.springframework.dao.TransientDataAccessResourceException("connection lost");
                    }
                    try {
                        return method.invoke(repository, args);
                    } catch (java.lang.reflect.InvocationTargetException thrown) {
                        throw thrown.getCause();
                    }
                });
        BudgetedOperations withFlakyDatabase = replica(flaky);

        BudgetedOperations.Outcome<String> outcome = withFlakyDatabase.run("GENERATE_TASKS", null, 200, Duration.ofSeconds(5), () -> "done");

        assertTrue(outcome.completed());
        assertEquals("done", outcome.result());
        assertEquals(OperationStatus.SUCCEEDED, outcome.operation().status());
        assertEquals(2, attempts.get());
    }

    @Test
    void anOperationWaitsForTheOneOfAnotherReplicaInItsPackageAndStartsWhenItEnds() throws Exception {
        authenticateAs("planner");
        String scope = "EP-" + System.nanoTime();
        UUID elsewhere = UUID.randomUUID();
        // La de otra replica: en marcha en el mismo ambito y con el latido al dia. El cerrojo local no la ve.
        jdbc.update("""
                insert into background_operation (id, kind, scope, status, requested_by, created_at, started_at, heartbeat_at)
                values (?, 'GENERATE_TASKS', ?, 'RUNNING', 'other.replica', now(), now(), now())
                """, elsewhere, scope);
        AtomicBoolean ran = new AtomicBoolean();

        UUID id = operations.run("GENERATE_TASKS", scope, 200, Duration.ZERO, () -> {
            ran.set(true);
            return "ok";
        }).operation().id();

        Thread.sleep(300);
        assertEquals(OperationStatus.QUEUED, operations.find(id).status(), "Not while the other replica's one runs");
        assertFalse(ran.get());
        jdbc.update("update background_operation set status = 'SUCCEEDED', finished_at = now() where id = ?", elsewhere);
        assertEquals(OperationStatus.SUCCEEDED, waitFor(id).status(), "It looks again and starts once that one ends");
        assertTrue(ran.get());
    }

    private static void authenticateAs(String username, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(UUID.randomUUID().toString())
                .claim("preferred_username", username).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList(authorities), username));
    }

    private OperationResponse waitFor(UUID id) {
        waitUntil(() -> operations.find(id).status().isFinished());
        return operations.find(id);
    }

    private static void waitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not met in 15 s");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Never released");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
