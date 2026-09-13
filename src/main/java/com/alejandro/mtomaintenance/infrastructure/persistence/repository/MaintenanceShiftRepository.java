package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceShiftRepository extends JpaRepository<MaintenanceShift, UUID>, JpaSpecificationExecutor<MaintenanceShift> {

    Optional<MaintenanceShift> findByCode(String code);

    List<MaintenanceShift> findByShiftDateBetweenOrderByShiftDateAsc(LocalDate from, LocalDate to);
}
