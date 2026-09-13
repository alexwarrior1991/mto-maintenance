package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.history.StatusHistoryResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceStatusHistory;
import org.mapstruct.Mapper;

@Mapper(config = MapStructCentralConfig.class)
public interface StatusHistoryMapper {

    StatusHistoryResponse toResponse(MaintenanceStatusHistory history);
}
