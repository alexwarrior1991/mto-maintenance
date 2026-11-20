package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Consultas de agregacion de los informes. Viven aparte de los repositorios de Spring Data porque
 * cruzan varias entidades y no pertenecen a ningun agregado.
 */
@Repository
public class MaintenanceReportRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Lo que el informe de avance lee de un activo, y nada mas: sin ventana y sin filtros son todos los
     * activos habilitados de la red, y cargarlos como entidades era traer treinta columnas de cada uno
     * para usar seis.
     */
    public record ReportableAsset(UUID id, Long executionPackageId, Long trackId, CatenaryAssetType type, BigDecimal startKp,
                                  Instant lastPreventiveCompletedAt) {

        public static ReportableAsset of(CatenaryAsset asset) {
            return new ReportableAsset(asset.getId(), asset.getExecutionPackageId(), asset.getTrackId(), asset.getType(),
                    asset.getStartKp(), asset.getLastPreventiveCompletedAt());
        }
    }

    /**
     * Los activos de un informe de avance: revisables (todo menos los tramos), habilitados, con los
     * filtros opcionales. El filtro por tipo se anade solo cuando viene: un parametro de enum a null no
     * tiene tipo para PostgreSQL ({@code could not determine data type of parameter}), al contrario que
     * los bigint. Lo comparten {@link #findReportableAssets} y {@link #findAssetIdsWorkedBetween}, que
     * tienen que hablar de los mismos activos.
     *
     * <p>Por via entra tambien el aislador que la conecta con otra: esta sobre las dos.</p>
     */
    private static String reportableAssets(CatenaryAssetType assetType) {
        return """
                where asset.enabled = true
                  and asset.type <> :section
                  and (:executionPackageId is null or asset.executionPackageId = :executionPackageId)
                  and (:trackId is null or asset.trackId = :trackId or asset.connectedTrackId = :trackId)
                """ + (assetType == null ? "" : "  and asset.type = :assetType\n");
    }

    private static <T> TypedQuery<T> ofReportableAssets(TypedQuery<T> query, Long executionPackageId, Long trackId, CatenaryAssetType assetType) {
        query.setParameter("section", CatenaryAssetType.TRACK_SECTION);
        query.setParameter("executionPackageId", executionPackageId);
        query.setParameter("trackId", trackId);
        if (assetType != null) {
            query.setParameter("assetType", assetType);
        }
        return query;
    }

    /** Los activos del informe, en su orden (paquete, via, tipo, kp, codigo), con lo que el informe lee de cada uno. */
    public List<ReportableAsset> findReportableAssets(Long executionPackageId, Long trackId, CatenaryAssetType assetType) {
        return ofReportableAssets(entityManager.createQuery("""
                select new com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceReportRepository$ReportableAsset(
                    asset.id, asset.executionPackageId, asset.trackId, asset.type, asset.startKp, asset.lastPreventiveCompletedAt)
                from CatenaryAsset asset
                """ + reportableAssets(assetType) + """
                order by asset.executionPackageId, asset.trackId, asset.type, asset.startKp, asset.code
                """, ReportableAsset.class), executionPackageId, trackId, assetType).getResultList();
    }

    /**
     * De los activos del informe, los que tienen una inspeccion o una tarea completada dentro del rango
     * (o alguna vez, sin rango). Se pregunta por los activos del informe ({@code exists}) en vez de leer
     * todas las inspecciones y todas las tareas completadas de la historia para quedarse despues con las
     * de esos activos: el resultado del informe es el mismo, y sin rango lo que cuesta ya no crece con
     * los anos sino con los activos que se piden ({@code idx_maintenance_inspection_asset} e
     * {@code idx_maintenance_task_asset_status}, una lectura de indice por activo); con rango PostgreSQL
     * sigue partiendo de las fechas ({@code idx_maintenance_inspection_date},
     * {@code idx_maintenance_task_completed_at}).
     *
     * <p>Son dos consultas, una por origen, y no una con los dos {@code exists} unidos por un
     * {@code or}: asi cada una es un semi-join que el planificador sabe costear. Con el {@code or} los
     * deja como subplanes, les calcula un coste desorbitado y, con el JIT activo, compilarlos costaba
     * mas que la consulta (0,2 s de 0,24 medidos).</p>
     *
     * <p>Los estados van como parametro y no como literal JPQL: Hibernate traduce el literal a
     * {@code 'COMPLETED'::MaintenanceTaskStatus} y ese tipo no existe en PostgreSQL (el enum de la base
     * es {@code maintenance_task_status}). Las fechas opcionales se anaden solo cuando vienen: un
     * parametro temporal a null tampoco tiene tipo para PostgreSQL.</p>
     *
     * @param zone la de los informes: una inspeccion solo tiene fecha, y el rango pasa a dias en ella
     */
    public Set<UUID> findAssetIdsWorkedBetween(Long executionPackageId, Long trackId, CatenaryAssetType assetType,
                                               Instant from, Instant to, ZoneId zone) {
        TypedQuery<UUID> inspected = ofReportableAssets(entityManager.createQuery("""
                select asset.id from CatenaryAsset asset
                """ + reportableAssets(assetType) + """
                  and exists (select 1 from MaintenanceInspection inspection
                              where inspection.asset = asset
                """ + (from == null ? "" : "                and inspection.inspectionDate >= :fromDate\n")
                    + (to == null ? "" : "                and inspection.inspectionDate <= :toDate\n") + """
                             )
                """, UUID.class), executionPackageId, trackId, assetType);
        if (from != null) {
            inspected.setParameter("fromDate", LocalDate.ofInstant(from, zone));
        }
        if (to != null) {
            inspected.setParameter("toDate", LocalDate.ofInstant(to, zone));
        }
        Set<UUID> ids = new HashSet<>(inspected.getResultList());

        TypedQuery<UUID> withTask = ofReportableAssets(entityManager.createQuery("""
                select asset.id from CatenaryAsset asset
                """ + reportableAssets(assetType) + """
                  and exists (select 1 from MaintenanceTask task
                              where task.asset = asset
                                and task.status = :completed
                """ + (from == null ? "" : "                and task.completedAt >= :from\n")
                    + (to == null ? "" : "                and task.completedAt <= :to\n") + """
                             )
                """, UUID.class), executionPackageId, trackId, assetType)
                .setParameter("completed", MaintenanceTaskStatus.COMPLETED);
        if (from != null) {
            withTask.setParameter("from", from);
        }
        if (to != null) {
            withTask.setParameter("to", to);
        }
        ids.addAll(withTask.getResultList());
        return ids;
    }

    public List<MaintenanceShift> findShiftsBetween(LocalDate from, LocalDate to, Long executionPackageId) {
        return entityManager.createQuery("""
                select shift from MaintenanceShift shift
                where shift.shiftDate >= :from and shift.shiftDate <= :to
                  and (:executionPackageId is null or shift.executionPackageId = :executionPackageId)
                order by shift.shiftDate, shift.code
                """, MaintenanceShift.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("executionPackageId", executionPackageId)
                .getResultList();
    }

    public List<MaintenanceOrder> findOrdersCompletedBetween(Instant from, Instant to, Long executionPackageId) {
        return entityManager.createQuery("""
                select o from MaintenanceOrder o
                where o.status = :completed
                  and o.actualEndDate >= :from and o.actualEndDate < :to
                  and (:executionPackageId is null or o.executionPackageId = :executionPackageId)
                """, MaintenanceOrder.class)
                .setParameter("completed", MaintenanceOrderStatus.COMPLETED)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("executionPackageId", executionPackageId)
                .getResultList();
    }

    public List<MaintenanceOrder> findOrdersCreatedBetween(Instant from, Instant to, Long executionPackageId) {
        return entityManager.createQuery("""
                select o from MaintenanceOrder o
                where o.createdAt >= :from and o.createdAt < :to
                  and (:executionPackageId is null or o.executionPackageId = :executionPackageId)
                """, MaintenanceOrder.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("executionPackageId", executionPackageId)
                .getResultList();
    }

    public List<MaintenanceTask> findTasksCompletedBetween(Instant from, Instant to, Long executionPackageId) {
        return entityManager.createQuery("""
                select task from MaintenanceTask task
                where task.status = :completed
                  and task.completedAt >= :from and task.completedAt < :to
                  and (:executionPackageId is null or task.order.executionPackageId = :executionPackageId)
                """, MaintenanceTask.class)
                .setParameter("completed", MaintenanceTaskStatus.COMPLETED)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("executionPackageId", executionPackageId)
                .getResultList();
    }

    public long countDefectsDetectedBetween(Instant from, Instant to, Long executionPackageId) {
        return entityManager.createQuery("""
                select count(defect) from CatenaryDefect defect
                where defect.detectedAt >= :from and defect.detectedAt < :to
                  and (:executionPackageId is null or defect.executionPackageId = :executionPackageId)
                """, Long.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("executionPackageId", executionPackageId)
                .getSingleResult();
    }

    public long countDefectsResolvedBetween(Instant from, Instant to, Long executionPackageId) {
        return entityManager.createQuery("""
                select count(defect) from CatenaryDefect defect
                where defect.resolvedAt >= :from and defect.resolvedAt < :to
                  and (:executionPackageId is null or defect.executionPackageId = :executionPackageId)
                """, Long.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("executionPackageId", executionPackageId)
                .getSingleResult();
    }

    public List<MaintenanceMaterialUsage> findMaterialsOfOrders(List<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                select usage from MaintenanceMaterialUsage usage
                where usage.order.id in :orderIds and usage.consumedQuantity > 0
                """, MaintenanceMaterialUsage.class)
                .setParameter("orderIds", orderIds)
                .getResultList();
    }
}
