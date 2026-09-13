package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamResponse;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamSummaryResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTeam;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructCentralConfig.class, uses = AuditableMapper.class)
public interface MaintenanceTeamMapper {

    @Mapping(target = "audit", source = "team")
    MaintenanceTeamResponse toResponse(MaintenanceTeam team);

    MaintenanceTeamSummaryResponse toSummary(MaintenanceTeam team);
}
