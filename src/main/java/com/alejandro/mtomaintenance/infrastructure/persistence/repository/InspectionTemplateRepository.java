package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionTemplateRepository extends JpaRepository<InspectionTemplate, UUID> {

    /**
     * La plantilla activa de un tipo de activo: la que se copia a inspecciones y tareas. Como mucho hay
     * una ({@code uq_inspection_template_active_asset_type}, {@code V28}); el orden queda de cuando era
     * solo una convencion.
     */
    Optional<InspectionTemplate> findFirstByAssetTypeAndActiveTrueOrderByTemplateVersionDesc(CatenaryAssetType assetType);

    List<InspectionTemplate> findAllByOrderByAssetTypeAscTemplateVersionDesc();

    /** El tipo de una plantilla sin cargarla: hace falta para tomar el cerrojo de su tipo antes de leerla. */
    @Query("select t.assetType from InspectionTemplate t where t.id = :id")
    Optional<CatenaryAssetType> findAssetTypeById(@Param("id") UUID id);

    /** El numero de la ultima version de un tipo, activa o no; 0 si no tiene ninguna. */
    @Query("select coalesce(max(t.templateVersion), 0) from InspectionTemplate t where t.assetType = :assetType")
    int findLastVersion(@Param("assetType") CatenaryAssetType assetType);

    /**
     * Cerrojo de aviso hasta el final de la transaccion, por tipo de activo: dos escrituras de las
     * plantillas de un tipo se ponen en fila y la segunda ve lo que dejo la primera.
     * {@code pg_advisory_xact_lock} devuelve {@code void}; por eso se envuelve.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:key)) as locked", nativeQuery = true)
    int lockAssetType(@Param("key") long key);
}
