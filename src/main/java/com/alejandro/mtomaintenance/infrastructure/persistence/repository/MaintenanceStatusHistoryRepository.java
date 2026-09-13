package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaintenanceStatusHistoryRepository extends JpaRepository<MaintenanceStatusHistory, UUID> {

    List<MaintenanceStatusHistory> findByOrderIdOrderByChangedAtAsc(UUID orderId);

    List<MaintenanceStatusHistory> findByDefectIdOrderByChangedAtAsc(UUID defectId);
}
