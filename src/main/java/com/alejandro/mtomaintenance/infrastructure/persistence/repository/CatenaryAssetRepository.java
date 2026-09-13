package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatenaryAssetRepository extends JpaRepository<CatenaryAsset, UUID>, JpaSpecificationExecutor<CatenaryAsset> {

    Optional<CatenaryAsset> findByCode(String code);

    boolean existsByCode(String code);

    Optional<CatenaryAsset> findBySourceServiceAndSourceEntityId(String sourceService, String sourceEntityId);

    /** Perfiles habilitados de una via entre dos puntos kilometricos, en orden de kp: el preventivo perfil a perfil. */
    @Query("""
            select asset
            from CatenaryAsset asset
            where asset.type = :type
              and asset.enabled = true
              and asset.trackId = :trackId
              and asset.startKp >= :fromKp
              and asset.startKp <= :toKp
            order by asset.startKp asc, asset.code asc
            """)
    List<CatenaryAsset> findEnabledByTypeOnTrackBetween(
            @Param("type") CatenaryAssetType type,
            @Param("trackId") Long trackId,
            @Param("fromKp") BigDecimal fromKp,
            @Param("toKp") BigDecimal toKp
    );

    /**
     * Alta o actualizacion desde un evento de datos maestros. La marca de agua se compara DENTRO del
     * where: un evento mas antiguo que lo aplicado no toca la fila (devuelve 0), y dos entregas
     * concurrentes no pueden pisarse. Sin numero de secuencia se aplica y se conserva la marca.
     * Nunca leer-y-escribir. Al ser SQL nativo no deja revision de Envers.
     */
    @Modifying
    @Query(value = """
            insert into catenary_asset (
                id, code, name, type, execution_package_id, track_id, station_id, start_kp, end_kp,
                profile_source_id, sectioning, source_service, source_entity_id, source_sequence_number, enabled,
                created_at, updated_at, created_by, updated_by
            ) values (
                gen_random_uuid(), :code, :name, cast(:type as catenary_asset_type), :executionPackageId, :trackId,
                :stationId, :startKp, :endKp, :profileSourceId, :sectioning, :sourceService, :sourceEntityId,
                :sourceSequenceNumber, :enabled, now(), now(), 'system', 'system'
            ) on conflict (source_service, source_entity_id) do update
               set code = excluded.code,
                   name = excluded.name,
                   execution_package_id = excluded.execution_package_id,
                   track_id = excluded.track_id,
                   station_id = excluded.station_id,
                   start_kp = excluded.start_kp,
                   end_kp = excluded.end_kp,
                   profile_source_id = excluded.profile_source_id,
                   sectioning = excluded.sectioning,
                   enabled = excluded.enabled,
                   source_sequence_number = coalesce(
                       excluded.source_sequence_number, catenary_asset.source_sequence_number),
                   updated_at = now()
             where catenary_asset.source_sequence_number is null
                or excluded.source_sequence_number is null
                or excluded.source_sequence_number >= catenary_asset.source_sequence_number
            """, nativeQuery = true)
    int upsertFromMasterData(
            @Param("sourceService") String sourceService,
            @Param("sourceEntityId") String sourceEntityId,
            @Param("code") String code,
            @Param("name") String name,
            @Param("type") String type,
            @Param("executionPackageId") Long executionPackageId,
            @Param("trackId") Long trackId,
            @Param("stationId") Long stationId,
            @Param("startKp") BigDecimal startKp,
            @Param("endKp") BigDecimal endKp,
            @Param("profileSourceId") String profileSourceId,
            @Param("sectioning") String sectioning,
            @Param("enabled") boolean enabled,
            @Param("sourceSequenceNumber") Long sourceSequenceNumber
    );

    /** Desactivacion desde un borrado en origen. Avanza la marca aunque la fila ya estuviera inactiva. */
    @Modifying
    @Query(value = """
            update catenary_asset
               set enabled = false,
                   source_sequence_number = coalesce(:sourceSequenceNumber, source_sequence_number),
                   updated_at = now()
             where source_service = :sourceService
               and source_entity_id = :sourceEntityId
               and (source_sequence_number is null
                    or cast(:sourceSequenceNumber as bigint) is null
                    or cast(:sourceSequenceNumber as bigint) >= source_sequence_number)
            """, nativeQuery = true)
    int deactivateFromMasterData(
            @Param("sourceService") String sourceService,
            @Param("sourceEntityId") String sourceEntityId,
            @Param("sourceSequenceNumber") Long sourceSequenceNumber
    );

    /** Una via borrada en origen deja sin sentido todo lo que hay sobre ella, tramos incluidos. */
    @Modifying
    @Query(value = """
            update catenary_asset
               set enabled = false,
                   updated_at = now()
             where track_id = :trackId
               and enabled = true
            """, nativeQuery = true)
    int deactivateByTrack(@Param("trackId") Long trackId);
}
