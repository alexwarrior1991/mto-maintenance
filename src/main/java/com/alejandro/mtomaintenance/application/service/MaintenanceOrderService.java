package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.dto.order.AssignOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.CancelOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.CompleteOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.order.PlanOrderRequest;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Toda la logica de cambio de estado de una orden vive aqui. */
public interface MaintenanceOrderService {

    MaintenanceOrderResponse create(MaintenanceOrderRequest request);

    MaintenanceOrderResponse update(UUID id, MaintenanceOrderUpdateRequest request);

    MaintenanceOrderResponse findById(UUID id);

    PageResponse<MaintenanceOrderResponse> search(MaintenanceOrderStatus status, MaintenanceOrderType type, MaintenancePriority priority,
                                                  UUID assetId, CatenaryAssetType assetType, Long trackId, Long stationId,
                                                  Long executionPackageId, LocalDate plannedFrom, LocalDate plannedTo,
                                                  Instant actualStartFrom, Instant actualStartTo, String assignedUser,
                                                  UUID teamId, String code, Pageable pageable);

    PageResponse<MaintenanceOrderResponse> findByAsset(UUID assetId, Pageable pageable);

    MaintenanceOrderResponse plan(UUID id, PlanOrderRequest request);

    MaintenanceOrderResponse assign(UUID id, AssignOrderRequest request);

    MaintenanceOrderResponse start(UUID id, String comment);

    MaintenanceOrderResponse complete(UUID id, CompleteOrderRequest request);

    MaintenanceOrderResponse cancel(UUID id, CancelOrderRequest request);

    PageResponse<EntityRevisionResponse<MaintenanceOrderResponse>> findRevisions(UUID id, Pageable pageable);
}
