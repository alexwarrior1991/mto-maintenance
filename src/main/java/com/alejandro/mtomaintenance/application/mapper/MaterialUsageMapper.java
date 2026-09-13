package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructCentralConfig.class, uses = AuditableMapper.class)
public interface MaterialUsageMapper {

    @Mapping(target = "audit", source = "usage")
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "taskId", source = "task.id")
    MaterialUsageResponse toResponse(MaintenanceMaterialUsage usage);
}
