package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.tasktype.MaintenanceTaskTypeResponse;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceTaskTypeMapper;
import com.alejandro.mtomaintenance.application.service.MaintenanceTaskTypeService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FunctionalGroup;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
class MaintenanceTaskTypeServiceImpl implements MaintenanceTaskTypeService {

    private final MaintenanceTaskTypeRepository repository;
    private final MaintenanceTaskTypeMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceTaskTypeResponse> findAll(FunctionalGroup functionalGroup, Boolean requiresFullPossession, Boolean diagnostic) {
        return repository.findAllByOrderByOrderIndexAsc().stream()
                .filter(type -> functionalGroup == null || type.getFunctionalGroup() == functionalGroup)
                .filter(type -> requiresFullPossession == null || type.getRequiresFullPossession().equals(requiresFullPossession))
                .filter(type -> diagnostic == null || type.getDiagnostic().equals(diagnostic))
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceTaskTypeResponse findByCode(String code) {
        return repository.findByCode(code.trim().toUpperCase())
                .map(mapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Maintenance task type '" + code + "' was not found"));
    }
}
