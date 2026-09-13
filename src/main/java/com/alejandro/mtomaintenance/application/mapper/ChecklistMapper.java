package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.inspection.CheckItemResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.AbstractChecklistItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspectionItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskCheckItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructCentralConfig.class)
public interface ChecklistMapper {

    @Mapping(target = "outOfRange", expression = "java(item.isOutOfRange())")
    CheckItemResponse toResponse(AbstractChecklistItem item);

    default CheckItemResponse toResponse(MaintenanceTaskCheckItem item) {
        return toResponse((AbstractChecklistItem) item);
    }

    default CheckItemResponse toResponse(MaintenanceInspectionItem item) {
        return toResponse((AbstractChecklistItem) item);
    }

    default List<CheckItemResponse> toTaskItemResponses(List<MaintenanceTaskCheckItem> items) {
        return items == null ? List.of() : items.stream().map(this::toResponse).toList();
    }

    default List<CheckItemResponse> toInspectionItemResponses(List<MaintenanceInspectionItem> items) {
        return items == null ? List.of() : items.stream().map(this::toResponse).toList();
    }
}
