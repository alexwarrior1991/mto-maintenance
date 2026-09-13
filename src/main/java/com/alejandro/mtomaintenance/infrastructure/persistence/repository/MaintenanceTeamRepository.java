package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MaintenanceTeamRepository extends JpaRepository<MaintenanceTeam, UUID> {

    Optional<MaintenanceTeam> findByCode(String code);

    boolean existsByCode(String code);
}
