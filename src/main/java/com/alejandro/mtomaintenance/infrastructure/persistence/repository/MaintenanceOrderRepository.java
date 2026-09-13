package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface MaintenanceOrderRepository extends JpaRepository<MaintenanceOrder, UUID>, JpaSpecificationExecutor<MaintenanceOrder> {

    Optional<MaintenanceOrder> findByCode(String code);

    Page<MaintenanceOrder> findByAssetId(UUID assetId, Pageable pageable);

    boolean existsByAssetIdAndStatusNotIn(UUID assetId, java.util.Collection<MaintenanceOrderStatus> statuses);
}
