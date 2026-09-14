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

import java.time.Instant;
import java.time.LocalDate;
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
     * Activos revisables (todo menos los tramos), habilitados, con los filtros opcionales. El filtro
     * por tipo se anade solo cuando viene: un parametro de enum a null no tiene tipo para PostgreSQL
     * ({@code could not determine data type of parameter}), al contrario que los bigint.
     */
    public List<CatenaryAsset> findReportableAssets(Long executionPackageId, Long trackId, CatenaryAssetType assetType) {
        String typeFilter = assetType == null ? "" : "  and asset.type = :assetType\n";
        TypedQuery<CatenaryAsset> query = entityManager.createQuery("""
                select asset from CatenaryAsset asset
                where asset.enabled = true
                  and asset.type <> :section
                  and (:executionPackageId is null or asset.executionPackageId = :executionPackageId)
                  and (:trackId is null or asset.trackId = :trackId)
                """ + typeFilter + """
                order by asset.executionPackageId, asset.trackId, asset.type, asset.startKp, asset.code
                """, CatenaryAsset.class);
        query.setParameter("section", CatenaryAssetType.TRACK_SECTION);
        query.setParameter("executionPackageId", executionPackageId);
        query.setParameter("trackId", trackId);
        if (assetType != null) {
            query.setParameter("assetType", assetType);
        }
        return query.getResultList();
    }

    /**
     * Activos con una inspeccion o una tarea completada dentro del rango. Los estados van como
     * parametro y no como literal JPQL: Hibernate traduce el literal a {@code 'COMPLETED'::MaintenanceTaskStatus}
     * y ese tipo no existe en PostgreSQL (el enum de la base es {@code maintenance_task_status}).
     */
    public Set<UUID> findAssetIdsWorkedBetween(Instant from, Instant to) {
        // Las fechas opcionales se anaden solo cuando vienen: un parametro temporal a null tampoco
        // tiene tipo para PostgreSQL, y el informe de avance se pide casi siempre sin ventana.
        TypedQuery<UUID> inspections = entityManager.createQuery("""
                select distinct inspection.asset.id from MaintenanceInspection inspection
                where 1 = 1
                """ + (from == null ? "" : "  and inspection.inspectionDate >= :fromDate\n")
                    + (to == null ? "" : "  and inspection.inspectionDate <= :toDate\n"), UUID.class);
        if (from != null) {
            inspections.setParameter("fromDate", LocalDate.ofInstant(from, java.time.ZoneOffset.UTC));
        }
        if (to != null) {
            inspections.setParameter("toDate", LocalDate.ofInstant(to, java.time.ZoneOffset.UTC));
        }
        Set<UUID> ids = new HashSet<>(inspections.getResultList());

        TypedQuery<UUID> tasks = entityManager.createQuery("""
                select distinct task.asset.id from MaintenanceTask task
                where task.asset is not null
                  and task.status = :completed
                """ + (from == null ? "" : "  and task.completedAt >= :from\n")
                    + (to == null ? "" : "  and task.completedAt <= :to\n"), UUID.class)
                .setParameter("completed", MaintenanceTaskStatus.COMPLETED);
        if (from != null) {
            tasks.setParameter("from", from);
        }
        if (to != null) {
            tasks.setParameter("to", to);
        }
        ids.addAll(tasks.getResultList());
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
