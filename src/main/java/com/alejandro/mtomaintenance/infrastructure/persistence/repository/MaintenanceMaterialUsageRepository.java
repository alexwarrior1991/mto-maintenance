package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceMaterialUsageRepository extends JpaRepository<MaintenanceMaterialUsage, UUID> {

    Optional<MaintenanceMaterialUsage> findByIdAndOrderId(UUID id, UUID orderId);

    List<MaintenanceMaterialUsage> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    List<MaintenanceMaterialUsage> findByOrderIdAndStockSyncStatus(UUID orderId, StockSyncStatus status);

    boolean existsByOrderIdAndStockSyncStatus(UUID orderId, StockSyncStatus status);

    List<MaintenanceMaterialUsage> findByTaskIdIn(Collection<UUID> taskIds);

    /** Las lineas en ese estado, solo su id y el de su orden: las que el reintento automatico sincroniza. */
    @Query("select usage.id as id, usage.order.id as orderId from MaintenanceMaterialUsage usage where usage.stockSyncStatus = :status")
    List<LineRef> findRefsByStockSyncStatus(@Param("status") StockSyncStatus status);

    /**
     * Las mismas, sin las que cambiaron despues de {@code before}: la vuelta periodica no toca una linea
     * que una transicion acaba de cortar por su plazo, cuya llamada a stock puede seguir en el aire (en
     * esta instancia o en otra) y que ya reintenta su propio reintento diferido.
     */
    @Query("""
            select usage.id as id, usage.order.id as orderId from MaintenanceMaterialUsage usage
             where usage.stockSyncStatus = :status and usage.updatedAt < :before
            """)
    List<LineRef> findRefsByStockSyncStatusUnchangedSince(@Param("status") StockSyncStatus status, @Param("before") java.time.Instant before);

    /** Una linea por su id y el de su orden, que es como la pide {@code MaintenanceMaterialUsageService.sync}. */
    interface LineRef {

        UUID getId();

        UUID getOrderId();
    }
}
