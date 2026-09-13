package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionTemplateRepository extends JpaRepository<InspectionTemplate, UUID> {

    /** La plantilla activa de mayor version para un tipo de activo: la que se copia a inspecciones y tareas. */
    Optional<InspectionTemplate> findFirstByAssetTypeAndActiveTrueOrderByVersionDesc(CatenaryAssetType assetType);

    List<InspectionTemplate> findAllByOrderByAssetTypeAscVersionDesc();
}
