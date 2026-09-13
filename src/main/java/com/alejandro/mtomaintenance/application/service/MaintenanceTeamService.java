package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamRequest;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamResponse;

import java.util.List;
import java.util.UUID;

public interface MaintenanceTeamService {

    MaintenanceTeamResponse create(MaintenanceTeamRequest request);

    MaintenanceTeamResponse update(UUID id, MaintenanceTeamRequest request);

    MaintenanceTeamResponse findById(UUID id);

    List<MaintenanceTeamResponse> findAll();
}
