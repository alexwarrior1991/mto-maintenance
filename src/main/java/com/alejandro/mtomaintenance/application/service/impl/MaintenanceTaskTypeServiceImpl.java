package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.dto.tasktype.MaintenanceTaskTypeResponse;
import com.alejandro.mtomaintenance.application.dto.tasktype.MaintenanceTaskTypeUpdateRequest;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.exception.StaleVersionException;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceTaskTypeMapper;
import com.alejandro.mtomaintenance.application.service.MaintenanceTaskTypeService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FunctionalGroup;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskType;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskTypeRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
class MaintenanceTaskTypeServiceImpl implements MaintenanceTaskTypeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaintenanceTaskTypeServiceImpl.class);

    /** Lo unico que un PATCH puede vaciar. */
    private static final Set<String> CLEARABLE = Set.of("requiredEquipment");

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
        return mapper.toResponse(taskType(code));
    }

    /**
     * El equipo que exige al turno: el conjunto entero sustituye al anterior y {@code null} lo vacia. Lo demas
     * del catalogo es de las migraciones. Afecta a lo que se asigne, se inicie o se complete desde ahora; lo
     * ya hecho no se revisa.
     */
    @Override
    @Transactional
    public MaintenanceTaskTypeResponse patch(String code, MergePatch<MaintenanceTaskTypeUpdateRequest> patch) {
        MaintenanceTaskType type = taskType(code);
        MaintenanceTaskTypeUpdateRequest request = patch.values();
        StaleVersionException.check("Task type " + type.getCode(), type.getVersion(), request.version());
        PatchRules.requireClearable(patch, CLEARABLE);
        if (request.requiredEquipment() != null || patch.clears("requiredEquipment")) {
            type.getRequiredEquipment().clear();
            if (request.requiredEquipment() != null) {
                type.getRequiredEquipment().addAll(request.requiredEquipment());
            }
        }
        MaintenanceTaskType saved = repository.saveAndFlush(type);
        LOGGER.info("Task type {} requires {}", saved.getCode(), saved.getRequiredEquipment());
        return mapper.toResponse(saved);
    }

    private MaintenanceTaskType taskType(String code) {
        return repository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new NotFoundException("Maintenance task type '" + code + "' was not found"));
    }
}
