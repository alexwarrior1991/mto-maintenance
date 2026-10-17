package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.BackgroundOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Las escrituras de {@code background_operation}, todas nativas y condicionales: el recuento de filas
 * es la respuesta. Cada una es su propia transaccion corta, para que ni el tope ni el turno de un
 * ambito retengan una conexion mientras la operacion espera o trabaja.
 */
public interface BackgroundOperationRepository extends JpaRepository<BackgroundOperation, UUID> {

    /**
     * Toma el cerrojo del tope del despliegue para el resto de la transaccion: dos peticiones que
     * cuentan a la vez veian las dos un hueco y pasaban las dos.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:key)) as locked", nativeQuery = true)
    int lockCapacity(@Param("key") long key);

    /**
     * Apunta la operacion en {@code QUEUED} solo si las vivas (en cola o en marcha, con el latido al
     * dia) no llegan al tope. 0 filas: no hay sitio.
     */
    @Modifying
    @Query(value = """
            insert into background_operation
                (id, kind, scope, status, requested_by, correlation_id, request_method, request_uri, created_at, heartbeat_at)
            select :id, :kind, :scope, 'QUEUED', :requestedBy, :correlationId, :requestMethod, :requestUri, :now, :now
            where (select count(*) from background_operation o
                   where o.status in ('QUEUED', 'RUNNING') and o.heartbeat_at > :aliveSince) < :maxActive
            """, nativeQuery = true)
    int insertIfRoom(@Param("id") UUID id,
                     @Param("kind") String kind,
                     @Param("scope") String scope,
                     @Param("requestedBy") String requestedBy,
                     @Param("correlationId") String correlationId,
                     @Param("requestMethod") String requestMethod,
                     @Param("requestUri") String requestUri,
                     @Param("now") Instant now,
                     @Param("aliveSince") Instant aliveSince,
                     @Param("maxActive") int maxActive);

    /** Toma el cerrojo de un ambito para el resto de la transaccion: dos de la cola no arrancan a la vez. */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:key)) as locked", nativeQuery = true)
    int lockScope(@Param("key") long key);

    /**
     * Arranca una operacion en cola si es su turno: ninguna viva del mismo ambito en marcha y ninguna
     * apuntada antes esperando. Sin ambito arranca siempre. 0 filas: todavia no le toca.
     */
    @Modifying
    @Query(value = """
            update background_operation op
               set status = 'RUNNING', started_at = :now, heartbeat_at = :now
             where op.id = :id
               and op.status = 'QUEUED'
               and (op.scope is null or not exists (
                    select 1 from background_operation other
                     where other.scope = op.scope
                       and other.id <> op.id
                       and other.heartbeat_at > :aliveSince
                       and (other.status = 'RUNNING'
                            or (other.status = 'QUEUED' and (other.created_at, other.id) < (op.created_at, op.id)))))
            """, nativeQuery = true)
    int startIfTurn(@Param("id") UUID id, @Param("now") Instant now, @Param("aliveSince") Instant aliveSince);

    /** El latido de las operaciones que este proceso tiene en cola o en marcha. */
    @Modifying
    @Transactional
    @Query(value = """
            update background_operation set heartbeat_at = :now
             where id in (:ids) and status in ('QUEUED', 'RUNNING')
            """, nativeQuery = true)
    int heartbeat(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query(value = """
            update background_operation
               set status = 'SUCCEEDED', finished_at = :now, heartbeat_at = :now,
                   result_status = :resultStatus, result = cast(:result as json)
             where id = :id and status = 'RUNNING'
            """, nativeQuery = true)
    int markSucceeded(@Param("id") UUID id, @Param("resultStatus") int resultStatus, @Param("result") String result,
                      @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query(value = """
            update background_operation
               set status = 'FAILED', finished_at = :now, heartbeat_at = :now,
                   error_status = :errorStatus, error_code = :errorCode, error_message = :errorMessage
             where id = :id and status in ('QUEUED', 'RUNNING')
            """, nativeQuery = true)
    int markFailed(@Param("id") UUID id, @Param("errorStatus") int errorStatus, @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage, @Param("now") Instant now);

    /** Las vivas cuyo latido se paro: la replica que las llevaba murio. Quedan {@code FAILED}. */
    @Modifying
    @Transactional
    @Query(value = """
            update background_operation
               set status = 'FAILED', finished_at = :now, error_status = 500, error_code = 'OPS-410',
                   error_message = 'The operation was abandoned: the instance that ran it stopped answering'
             where status in ('QUEUED', 'RUNNING') and heartbeat_at <= :aliveSince
            """, nativeQuery = true)
    int failAbandoned(@Param("aliveSince") Instant aliveSince, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query(value = """
            delete from background_operation
             where id in (select id from background_operation
                           where status in ('SUCCEEDED', 'FAILED') and finished_at < :finishedBefore
                           limit :batchSize)
            """, nativeQuery = true)
    int purgeFinished(@Param("finishedBefore") Instant finishedBefore, @Param("batchSize") int batchSize);
}
