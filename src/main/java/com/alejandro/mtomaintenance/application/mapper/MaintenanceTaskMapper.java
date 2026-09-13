package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Mapper(config = MapStructCentralConfig.class, uses = {AuditableMapper.class, CatenaryAssetMapper.class, ChecklistMapper.class})
public interface MaintenanceTaskMapper {

    @Mapping(target = "audit", source = "task")
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "shiftId", source = "shift.id")
    @Mapping(target = "taskTypeCodes", source = "taskTypes")
    MaintenanceTaskResponse toResponse(MaintenanceTask task);

    default List<String> toCodes(Set<MaintenanceTaskType> taskTypes) {
        if (taskTypes == null) {
            return List.of();
        }
        return taskTypes.stream()
                .sorted(Comparator.comparing(MaintenanceTaskType::getOrderIndex))
                .map(MaintenanceTaskType::getCode)
                .toList();
    }
}
