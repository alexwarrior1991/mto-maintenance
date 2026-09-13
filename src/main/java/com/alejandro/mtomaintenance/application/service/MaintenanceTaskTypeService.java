package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.tasktype.MaintenanceTaskTypeResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FunctionalGroup;

import java.util.List;

public interface MaintenanceTaskTypeService {

    List<MaintenanceTaskTypeResponse> findAll(FunctionalGroup functionalGroup, Boolean requiresFullPossession, Boolean diagnostic);

    MaintenanceTaskTypeResponse findByCode(String code);
}
