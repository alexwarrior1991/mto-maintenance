package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;

import java.util.List;
import java.util.UUID;

public record InspectionTemplateResponse(
        UUID id,
        CatenaryAssetType assetType,
        Integer version,
        String name,
        Boolean active,
        List<InspectionTemplateItemResponse> items
) {
}
