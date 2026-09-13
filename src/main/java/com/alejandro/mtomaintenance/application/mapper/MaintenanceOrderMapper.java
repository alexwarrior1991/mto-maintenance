package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(config = MapStructCentralConfig.class, uses = {AuditableMapper.class, CatenaryAssetMapper.class, MaintenanceTeamMapper.class})
public interface MaintenanceOrderMapper {

    @Mapping(target = "audit", source = "order")
    @Mapping(target = "originInspectionId", source = "order.originInspection.id")
    @Mapping(target = "originDefectId", source = "order.originDefect.id")
    @Mapping(target = "id", source = "order.id")
    @Mapping(target = "taskCount", source = "taskCount")
    @Mapping(target = "completedTaskCount", source = "completedTaskCount")
    @Mapping(target = "estimatedMinutes", source = "estimatedMinutes")
    @Mapping(target = "estimatedShifts", source = "estimatedShifts")
    MaintenanceOrderResponse toResponse(MaintenanceOrder order,
                                        int taskCount,
                                        int completedTaskCount,
                                        BigDecimal estimatedMinutes,
                                        int estimatedShifts);
}
