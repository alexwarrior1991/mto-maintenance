package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageRequest;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageResponse;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface MaintenanceMaterialUsageService {

    List<MaterialUsageResponse> findByOrder(UUID orderId);

    MaterialUsageResponse register(UUID orderId, MaterialUsageRequest request);

    MaterialUsageResponse update(UUID orderId, UUID usageId, MaterialUsageUpdateRequest request);

    /** Reintenta la conversacion pendiente con stock. Solo aqui un stock caido responde 503. */
    MaterialUsageResponse sync(UUID orderId, UUID usageId);
}
