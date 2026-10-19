package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.dto.tasktype.MaintenanceTaskTypeResponse;
import com.alejandro.mtomaintenance.application.dto.tasktype.MaintenanceTaskTypeUpdateRequest;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FunctionalGroup;

import java.util.List;

public interface MaintenanceTaskTypeService {

    List<MaintenanceTaskTypeResponse> findAll(FunctionalGroup functionalGroup, Boolean requiresFullPossession, Boolean diagnostic);

    MaintenanceTaskTypeResponse findByCode(String code);

    /** El equipo que exige al turno ({@code V24}); el resto del catalogo no cambia por la API. */
    MaintenanceTaskTypeResponse patch(String code, MergePatch<MaintenanceTaskTypeUpdateRequest> patch);
}
