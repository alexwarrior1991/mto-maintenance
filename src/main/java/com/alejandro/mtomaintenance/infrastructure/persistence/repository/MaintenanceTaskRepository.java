package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceTaskRepository extends JpaRepository<MaintenanceTask, UUID> {

    Optional<MaintenanceTask> findByIdAndOrderId(UUID id, UUID orderId);

    List<MaintenanceTask> findByOrderIdOrderBySequenceAsc(UUID orderId);

    List<MaintenanceTask> findByShiftIdOrderBySequenceAsc(UUID shiftId);

    List<MaintenanceTask> findByShiftIdAndStatusIn(UUID shiftId, Collection<MaintenanceTaskStatus> statuses);

    boolean existsByOrderIdAndStatusIn(UUID orderId, Collection<MaintenanceTaskStatus> statuses);

    long countByOrderIdAndStatus(UUID orderId, MaintenanceTaskStatus status);

    @Query("select coalesce(max(task.sequence), 0) from MaintenanceTask task where task.order.id = :orderId")
    int findMaxSequence(@Param("orderId") UUID orderId);

    @Query("select task.asset.id from MaintenanceTask task where task.order.id = :orderId and task.asset is not null")
    List<UUID> findAssetIdsByOrderId(@Param("orderId") UUID orderId);
}
