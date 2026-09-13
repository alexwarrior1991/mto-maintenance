package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructCentralConfig.class, uses = {AuditableMapper.class, CatenaryAssetMapper.class, ChecklistMapper.class})
public interface MaintenanceInspectionMapper {

    @Mapping(target = "audit", source = "inspection")
    @Mapping(target = "templateId", source = "template.id")
    @Mapping(target = "generatedDefectId", source = "generatedDefect.id")
    @Mapping(target = "generatedOrderId", source = "generatedOrder.id")
    @Mapping(target = "originOrderId", source = "originOrder.id")
    @Mapping(target = "shiftId", source = "shift.id")
    MaintenanceInspectionResponse toResponse(MaintenanceInspection inspection);
}
