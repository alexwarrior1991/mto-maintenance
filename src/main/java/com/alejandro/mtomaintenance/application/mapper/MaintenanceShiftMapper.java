package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructCentralConfig.class, uses = {AuditableMapper.class, MaintenanceTeamMapper.class})
public interface MaintenanceShiftMapper {

    @Mapping(target = "audit", source = "shift")
    @Mapping(target = "blockADisconnectorId", source = "blockADisconnector.id")
    @Mapping(target = "blockADisconnectorCode", source = "blockADisconnector.code")
    @Mapping(target = "blockBDisconnectorId", source = "blockBDisconnector.id")
    @Mapping(target = "blockBDisconnectorCode", source = "blockBDisconnector.code")
    @Mapping(target = "trackIds", expression = "java(sortedTracks(shift))")
    MaintenanceShiftResponse toResponse(MaintenanceShift shift);

    default List<Long> sortedTracks(MaintenanceShift shift) {
        return shift.getTrackIds() == null ? List.of() : shift.getTrackIds().stream().sorted().toList();
    }
}
