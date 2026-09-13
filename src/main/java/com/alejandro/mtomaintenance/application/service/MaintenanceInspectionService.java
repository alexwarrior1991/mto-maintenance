package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.audit.EntityRevisionResponse;
import com.alejandro.mtomaintenance.application.dto.common.PageResponse;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.CheckItemUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.CreateCorrectiveOrderRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.CreateDefectFromInspectionRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface MaintenanceInspectionService {

    MaintenanceInspectionResponse create(MaintenanceInspectionRequest request);

    MaintenanceInspectionResponse update(UUID id, MaintenanceInspectionUpdateRequest request);

    MaintenanceInspectionResponse updateItem(UUID id, UUID itemId, CheckItemUpdateRequest request);

    MaintenanceInspectionResponse findById(UUID id);

    PageResponse<MaintenanceInspectionResponse> search(InspectionResult result, UUID assetId, CatenaryAssetType assetType, Long trackId,
                                                       Long stationId, Long executionPackageId, LocalDate from, LocalDate to,
                                                       String inspector, UUID originOrderId, Pageable pageable);

    /** Idempotente: si ya hay defecto generado lo devuelve. */
    CatenaryDefectResponse createDefect(UUID id, CreateDefectFromInspectionRequest request);

    /** Idempotente: si ya hay orden generada la devuelve. */
    MaintenanceOrderResponse createCorrectiveOrder(UUID id, CreateCorrectiveOrderRequest request);

    PageResponse<EntityRevisionResponse<MaintenanceInspectionResponse>> findRevisions(UUID id, Pageable pageable);
}
