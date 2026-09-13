package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetResponse;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Mapper(config = MapStructCentralConfig.class, uses = AuditableMapper.class)
public interface CatenaryAssetMapper {

    @Mapping(target = "audit", source = "asset")
    @Mapping(target = "nextPreventiveDueAt", expression = "java(nextPreventiveDueAt(asset))")
    CatenaryAssetResponse toResponse(CatenaryAsset asset);

    CatenaryAssetSummaryResponse toSummary(CatenaryAsset asset);

    /** Sin intervalo no vence; sin ultima ejecucion, vence ya (se devuelve el instante de creacion). */
    default Instant nextPreventiveDueAt(CatenaryAsset asset) {
        if (asset.getPreventiveIntervalDays() == null) {
            return null;
        }
        if (asset.getLastPreventiveCompletedAt() == null) {
            return asset.getCreatedAt();
        }
        return asset.getLastPreventiveCompletedAt().plus(asset.getPreventiveIntervalDays(), ChronoUnit.DAYS);
    }
}
