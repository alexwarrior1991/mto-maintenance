package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.crew.CrewPersonSummary;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamResponse;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamSummaryResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CrewPerson;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTeam;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;

@Mapper(config = MapStructCentralConfig.class, uses = AuditableMapper.class)
public interface MaintenanceTeamMapper {

    @Mapping(target = "audit", source = "team")
    @Mapping(target = "synchronizedFromResources", expression = "java(team.isSynchronized())")
    @Mapping(target = "members", expression = "java(members(team))")
    MaintenanceTeamResponse toResponse(MaintenanceTeam team);

    CrewPersonSummary toSummary(CrewPerson person);

    /** Por codigo, para que la respuesta salga igual cada vez. */
    default List<CrewPersonSummary> members(MaintenanceTeam team) {
        return team.getMembers().stream().sorted(Comparator.comparing(CrewPerson::getCode)).map(this::toSummary).toList();
    }

    MaintenanceTeamSummaryResponse toSummary(MaintenanceTeam team);
}
