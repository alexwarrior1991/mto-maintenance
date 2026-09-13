package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.exception.AssetDisabledException;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTeam;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.InspectionTemplateRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceOrderRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceShiftRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskTypeRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Cargas por id con el 404 ya resuelto, compartidas por los servicios del paquete. */
@Component
@RequiredArgsConstructor
class MaintenanceLookups {

    private final CatenaryAssetRepository assetRepository;
    private final MaintenanceOrderRepository orderRepository;
    private final MaintenanceShiftRepository shiftRepository;
    private final MaintenanceTeamRepository teamRepository;
    private final MaintenanceTaskTypeRepository taskTypeRepository;
    private final InspectionTemplateRepository templateRepository;

    CatenaryAsset asset(UUID id) {
        return assetRepository.findById(id).orElseThrow(() -> new NotFoundException("Catenary asset", id));
    }

    /** Un activo desactivado no admite ordenes ni inspecciones nuevas. */
    CatenaryAsset enabledAsset(UUID id) {
        CatenaryAsset asset = asset(id);
        if (!asset.isEnabled()) {
            throw new AssetDisabledException("Catenary asset " + asset.getCode() + " is disabled and cannot receive new work");
        }
        return asset;
    }

    MaintenanceOrder order(UUID id) {
        return orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Maintenance order", id));
    }

    MaintenanceShift shift(UUID id) {
        return shiftRepository.findById(id).orElseThrow(() -> new NotFoundException("Maintenance shift", id));
    }

    MaintenanceTeam team(UUID id) {
        return teamRepository.findById(id).orElseThrow(() -> new NotFoundException("Maintenance team", id));
    }

    Optional<InspectionTemplate> activeTemplate(CatenaryAsset asset) {
        return templateRepository.findFirstByAssetTypeAndActiveTrueOrderByVersionDesc(asset.getType());
    }

    /** Resuelve codigos RG/RP; un codigo desconocido es un 400, no un silencio. */
    Set<MaintenanceTaskType> taskTypes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<String> normalized = codes.stream().map(String::trim).map(String::toUpperCase).distinct().toList();
        List<MaintenanceTaskType> found = taskTypeRepository.findByCodeIn(normalized);
        if (found.size() != normalized.size()) {
            List<String> missing = new ArrayList<>(normalized);
            found.forEach(type -> missing.remove(type.getCode()));
            throw new ValidationException("Unknown task type codes: " + String.join(", ", missing));
        }
        Set<MaintenanceTaskType> ordered = new LinkedHashSet<>();
        for (String code : normalized) {
            found.stream().filter(type -> type.getCode().equals(code)).findFirst().ifPresent(ordered::add);
        }
        return ordered;
    }
}
