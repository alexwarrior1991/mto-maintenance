package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.history.StatusHistoryResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;

import java.util.List;
import java.util.UUID;

/** Registra cada cambio de estado con el actor resuelto por AuditActorResolver. */
public interface StatusHistoryService {

    void recordOrderChange(MaintenanceOrder order, String previousStatus, String newStatus, String comment);

    void recordDefectChange(CatenaryDefect defect, String previousStatus, String newStatus, String comment);

    List<StatusHistoryResponse> findByOrder(UUID orderId);

    List<StatusHistoryResponse> findByDefect(UUID defectId);
}
