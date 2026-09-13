package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatenaryDefectRepository extends JpaRepository<CatenaryDefect, UUID>, JpaSpecificationExecutor<CatenaryDefect> {

    Optional<CatenaryDefect> findByCode(String code);

    List<CatenaryDefect> findByOrderIdAndStatus(UUID orderId, DefectStatus status);

    List<CatenaryDefect> findByFoundInTaskIdIn(Collection<UUID> taskIds);

    long countByResolvedInShiftId(UUID shiftId);
}
