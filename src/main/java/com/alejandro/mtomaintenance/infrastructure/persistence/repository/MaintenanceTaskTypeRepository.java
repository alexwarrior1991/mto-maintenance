package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceTaskTypeRepository extends JpaRepository<MaintenanceTaskType, UUID> {

    Optional<MaintenanceTaskType> findByCode(String code);

    List<MaintenanceTaskType> findByCodeIn(Collection<String> codes);

    List<MaintenanceTaskType> findAllByOrderByOrderIndexAsc();
}
