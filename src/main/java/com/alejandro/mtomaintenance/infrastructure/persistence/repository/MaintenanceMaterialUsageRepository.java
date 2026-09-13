package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
