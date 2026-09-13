package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.history.StatusHistoryResponse;
import com.alejandro.mtomaintenance.application.mapper.StatusHistoryMapper;
import com.alejandro.mtomaintenance.application.service.StatusHistoryService;
import com.alejandro.mtomaintenance.configuration.AuditActorResolver;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceStatusHistory;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class StatusHistoryServiceImpl implements StatusHistoryService {

    private final MaintenanceStatusHistoryRepository repository;
    private final StatusHistoryMapper mapper;

    @Override
    @Transactional
    public void recordOrderChange(MaintenanceOrder order, String previousStatus, String newStatus, String comment) {
        repository.save(MaintenanceStatusHistory.builder()
                .order(order)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedAt(Instant.now())
                .changedBy(AuditActorResolver.currentActor())
                .comment(comment)
                .build());
    }

    @Override
    @Transactional
    public void recordDefectChange(CatenaryDefect defect, String previousStatus, String newStatus, String comment) {
        repository.save(MaintenanceStatusHistory.builder()
                .defect(defect)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedAt(Instant.now())
                .changedBy(AuditActorResolver.currentActor())
                .comment(comment)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> findByOrder(UUID orderId) {
        return repository.findByOrderIdOrderByChangedAtAsc(orderId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> findByDefect(UUID defectId) {
        return repository.findByDefectIdOrderByChangedAtAsc(defectId).stream().map(mapper::toResponse).toList();
    }
}
