package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceTaskRepository extends JpaRepository<MaintenanceTask, UUID> {

    /** Cuantos activos se preguntan por sentencia en {@link #findOpenPreventiveTasksElsewhere}. */
    int ASSETS_PER_QUERY = 1000;

    Optional<MaintenanceTask> findByIdAndOrderId(UUID id, UUID orderId);

    Optional<MaintenanceTask> findByIdAndShiftId(UUID id, UUID shiftId);

    List<MaintenanceTask> findByOrderIdOrderBySequenceAsc(UUID orderId);

    List<MaintenanceTask> findByShiftIdOrderBySequenceAsc(UUID shiftId);

    List<MaintenanceTask> findByShiftIdAndStatusIn(UUID shiftId, Collection<MaintenanceTaskStatus> statuses);

    boolean existsByOrderIdAndStatusIn(UUID orderId, Collection<MaintenanceTaskStatus> statuses);

    long countByOrderIdAndStatus(UUID orderId, MaintenanceTaskStatus status);

    @Query("select coalesce(max(task.sequence), 0) from MaintenanceTask task where task.order.id = :orderId")
    int findMaxSequence(@Param("orderId") UUID orderId);

    @Query("select task.asset.id from MaintenanceTask task where task.order.id = :orderId and task.asset is not null")
    List<UUID> findAssetIdsByOrderId(@Param("orderId") UUID orderId);

    /** Un activo que ya tiene trabajo preventivo pendiente en otra orden, y en cual. */
    interface AssetInOtherOrder {
        UUID getAssetId();

        String getOrderCode();
    }

    /**
     * De estos activos, los que tienen una tarea abierta (pendiente o en curso) en otra orden
     * preventiva que no ha terminado: generar los saltaria para no hacer dos veces el mismo trabajo.
     * Una tarea cancelada o una orden terminada no cuentan. Los estados van como parametro y no como
     * literal JPQL, por lo mismo que en {@code MaintenanceReportRepository}.
     */
    @Query("""
            select task.asset.id as assetId, task.order.code as orderCode
            from MaintenanceTask task
            where task.asset.id in :assetIds
              and task.order.id <> :orderId
              and task.status in :openTaskStatuses
              and task.order.type = :orderType
              and task.order.status not in :finishedOrderStatuses
            order by task.order.code asc
            """)
    List<AssetInOtherOrder> findAssetsInOtherOrders(
            @Param("assetIds") Collection<UUID> assetIds,
            @Param("orderId") UUID orderId,
            @Param("openTaskStatuses") Collection<MaintenanceTaskStatus> openTaskStatuses,
            @Param("orderType") MaintenanceOrderType orderType,
            @Param("finishedOrderStatuses") Collection<MaintenanceOrderStatus> finishedOrderStatuses
    );

    /**
     * En tandas de {@value #ASSETS_PER_QUERY}: todo un EP son miles de perfiles, y PostgreSQL no admite
     * mas de 32767 parametros en una sentencia.
     */
    default List<AssetInOtherOrder> findOpenPreventiveTasksElsewhere(Collection<UUID> assetIds, UUID orderId) {
        List<UUID> ids = List.copyOf(assetIds);
        List<AssetInOtherOrder> found = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += ASSETS_PER_QUERY) {
            found.addAll(findAssetsInOtherOrders(ids.subList(from, Math.min(ids.size(), from + ASSETS_PER_QUERY)), orderId,
                    List.of(MaintenanceTaskStatus.PENDING, MaintenanceTaskStatus.IN_PROGRESS),
                    MaintenanceOrderType.PREVENTIVE,
                    List.of(MaintenanceOrderStatus.COMPLETED, MaintenanceOrderStatus.CANCELLED)));
        }
        return found;
    }

    /**
     * Un cerrojo de aviso de PostgreSQL ligado a la transaccion, esperando a que lo suelte quien lo
     * tenga: lo que pone en fila las generaciones de tareas de un mismo EP entre instancias (en una,
     * ya hicieron fila en {@code GenerationLocks}, sin conexion), para que dos a la vez no den el mismo
     * activo por libre. {@code pg_advisory_xact_lock} devuelve {@code void}, que no se puede leer; de ahi
     * el {@code select 1} que lo envuelve. Se suelta al terminar la transaccion.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:key)) as locked", nativeQuery = true)
    int advisoryTransactionLock(@Param("key") long key);
}
