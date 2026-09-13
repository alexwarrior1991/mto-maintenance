package com.alejandro.mtomaintenance.application.dto.asset;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;

import java.math.BigDecimal;
import java.util.UUID;

public record CatenaryAssetSummaryResponse(
        UUID id,
        String code,
        String name,
        CatenaryAssetType type,
        Long trackId,
        BigDecimal startKp,
        BigDecimal endKp,
        String sectioning,
        Boolean enabled
) {
}
