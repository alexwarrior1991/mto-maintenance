package com.alejandro.mtomaintenance.application.dto.asset;

import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CatenaryAssetResponse(
        UUID id,
        String code,
        String name,
        CatenaryAssetType type,
        String description,
        Long executionPackageId,
        Long trackId,
        Long stationId,
        BigDecimal startKp,
        BigDecimal endKp,
        String profileSourceId,
        String sectioning,
        TrackKind trackKind,
        String sourceService,
        String sourceEntityId,
        Long sourceSequenceNumber,
        Boolean enabled,
        Integer preventiveIntervalDays,
        Instant lastPreventiveCompletedAt,
        Instant nextPreventiveDueAt,
        AuditMetadataResponse audit
) {
}
