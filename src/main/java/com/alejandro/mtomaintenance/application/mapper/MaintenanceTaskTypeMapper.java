package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.tasktype.MaintenanceTaskTypeResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskType;
import org.mapstruct.Mapper;

@Mapper(config = MapStructCentralConfig.class)
public interface MaintenanceTaskTypeMapper {

    MaintenanceTaskTypeResponse toResponse(MaintenanceTaskType taskType);
}
