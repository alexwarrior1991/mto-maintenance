package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructCentralConfig.class, uses = {AuditableMapper.class, MaintenanceTeamMapper.class})
public interface MaintenanceShiftMapper {

    @Mapping(target = "audit", source = "shift")
    @Mapping(target = "blockADisconnectorId", source = "blockADisconnector.id")
    @Mapping(target = "blockADisconnectorCode", source = "blockADisconnector.code")
    @Mapping(target = "blockBDisconnectorId", source = "blockBDisconnector.id")
    @Mapping(target = "blockBDisconnectorCode", source = "blockBDisconnector.code")
    MaintenanceShiftResponse toResponse(MaintenanceShift shift);
}
