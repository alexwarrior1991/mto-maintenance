package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.AuditableEntity;
import org.mapstruct.Mapper;

/**
 * Maps audit metadata shared by persisted resources into response DTO fragments.
 */
@Mapper(config = MapStructCentralConfig.class)
public interface AuditableMapper {

    AuditMetadataResponse toAuditMetadata(AuditableEntity entity);
}