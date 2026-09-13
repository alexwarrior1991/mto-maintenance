package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface MaintenanceInspectionRepository extends JpaRepository<MaintenanceInspection, UUID>, JpaSpecificationExecutor<MaintenanceInspection> {

    Optional<MaintenanceInspection> findByCode(String code);

    boolean existsByOriginOrderId(UUID orderId);
}
