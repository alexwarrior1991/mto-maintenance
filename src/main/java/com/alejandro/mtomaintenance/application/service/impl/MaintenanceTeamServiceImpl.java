package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamRequest;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamResponse;
import com.alejandro.mtomaintenance.application.exception.DuplicateCodeException;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceTeamMapper;
import com.alejandro.mtomaintenance.application.service.MaintenanceTeamService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTeam;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class MaintenanceTeamServiceImpl implements MaintenanceTeamService {

    private final MaintenanceTeamRepository repository;
    private final MaintenanceTeamMapper mapper;
    private final MaintenanceLookups lookups;

    @Override
    @Transactional
    public MaintenanceTeamResponse create(MaintenanceTeamRequest request) {
        String code = request.code().trim().toUpperCase();
        if (repository.existsByCode(code)) {
            throw new DuplicateCodeException("Maintenance team", code);
        }
        MaintenanceTeam team = MaintenanceTeam.builder()
                .code(code)
                .name(request.name().trim())
                .baseName(request.baseName())
                .vehicle(request.vehicle())
                .active(request.active() == null || request.active())
                .executionPackageIds(request.executionPackageIds() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(request.executionPackageIds()))
                .build();
        return mapper.toResponse(repository.save(team));
    }

    @Override
    @Transactional
    public MaintenanceTeamResponse update(UUID id, MaintenanceTeamRequest request) {
        MaintenanceTeam team = lookups.team(id);
        String code = request.code().trim().toUpperCase();
        if (!team.getCode().equals(code) && repository.existsByCode(code)) {
            throw new DuplicateCodeException("Maintenance team", code);
        }
        team.setCode(code);
        team.setName(request.name().trim());
        team.setBaseName(request.baseName());
        team.setVehicle(request.vehicle());
        if (request.active() != null) {
            team.setActive(request.active());
        }
        if (request.executionPackageIds() != null) {
            team.getExecutionPackageIds().clear();
            team.getExecutionPackageIds().addAll(request.executionPackageIds());
        }
        return mapper.toResponse(repository.save(team));
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceTeamResponse findById(UUID id) {
        return mapper.toResponse(lookups.team(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceTeamResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
