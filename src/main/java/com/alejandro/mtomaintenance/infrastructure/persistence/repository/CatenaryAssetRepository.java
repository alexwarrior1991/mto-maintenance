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
     * Un cerrojo de aviso de PostgreSQL ligado a la transaccion en curso: {@code true} si esta
     * transaccion lo tiene, {@code false} si otra lo tenia ya (no se espera). Se suelta solo al
     * terminar la transaccion. Es lo que reparte un trabajo diario entre varias instancias sin una
     * tabla de arrendamientos: la que lo consigue trabaja, las demas no hacen nada.
     */
    @Query(value = "select pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    boolean tryAdvisoryTransactionLock(@Param("key") long key);

    /**
     * Alta o actualizacion desde un evento de datos maestros. La marca de agua se compara DENTRO del
     * where: un evento mas antiguo que lo aplicado no toca la fila (devuelve 0), y dos entregas
     * concurrentes no pueden pisarse. Sin numero de secuencia se aplica y se conserva la marca.
     * Nunca leer-y-escribir. Al ser SQL nativo no deja revision de Envers.
     *
     * <p>{@code enabled} del evento va a {@code enabled_at_source}; el efectivo se recalcula sin tocar
     * {@code disabled_locally}, asi que una desactivacion hecha aqui sobrevive a cualquier evento.</p>
     *
     * <p>Todas las escrituras nativas de esta interfaz suben {@code version}: Hibernate no las ve, y
     * sin eso un PUT que leyo antes del evento lo pisaria sin que nadie se enterase.</p>
     */
    @Modifying
    @Query(value = """
            insert into catenary_asset (
                id, code, name, type, execution_package_id, track_id, connected_track_id, station_id,
                start_kp, end_kp, profile_source_id, sectioning, installation_type,
                source_service, source_entity_id, source_sequence_number, enabled_at_source, enabled,
                created_at, updated_at, created_by, updated_by
            ) values (
                gen_random_uuid(), :code, :name, cast(:type as catenary_asset_type), :executionPackageId, :trackId,
                :connectedTrackId, :stationId, :startKp, :endKp, :profileSourceId, :sectioning,
                cast(:installationType as section_insulator_installation),
                :sourceService, :sourceEntityId, :sourceSequenceNumber, :enabled, :enabled, now(), now(), 'system', 'system'
            ) on conflict (source_service, source_entity_id) do update
               set code = excluded.code,
                   name = excluded.name,
                   execution_package_id = excluded.execution_package_id,
                   track_id = excluded.track_id,
                   connected_track_id = excluded.connected_track_id,
                   station_id = excluded.station_id,
                   start_kp = excluded.start_kp,
                   end_kp = excluded.end_kp,
                   profile_source_id = excluded.profile_source_id,
                   sectioning = excluded.sectioning,
                   installation_type = excluded.installation_type,
                   enabled_at_source = excluded.enabled_at_source,
                   enabled = excluded.enabled_at_source and not catenary_asset.disabled_locally,
                   source_sequence_number = coalesce(
                       excluded.source_sequence_number, catenary_asset.source_sequence_number),
                   version = catenary_asset.version + 1,
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
            @Param("connectedTrackId") Long connectedTrackId,
            @Param("stationId") Long stationId,
            @Param("startKp") BigDecimal startKp,
            @Param("endKp") BigDecimal endKp,
            @Param("profileSourceId") String profileSourceId,
            @Param("sectioning") String sectioning,
            @Param("installationType") String installationType,
            @Param("enabled") boolean enabled,
            @Param("sourceSequenceNumber") Long sourceSequenceNumber
    );

    /** Desactivacion desde un borrado en origen. Avanza la marca aunque la fila ya estuviera inactiva. */
    @Modifying
    @Query(value = """
            update catenary_asset
               set enabled_at_source = false,
                   enabled = false,
                   source_sequence_number = coalesce(:sourceSequenceNumber, source_sequence_number),
                   version = version + 1,
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

    /**
     * Un seccionador esta sobre la via y en el paquete de su perfil: mto-configuration no publica
     * ninguno de los dos para el. Tras el upsert del seccionador, los toma del perfil con el que
     * cuelga, si ese perfil ya llego; si no, los pone {@link #propagateLocationToDisconnectors}
     * cuando llegue. SQL nativo como el upsert, asi que tampoco deja revision de Envers.
     */
    @Modifying
    @Query(value = """
            update catenary_asset disconnector
               set track_id = profile.track_id,
                   execution_package_id = profile.execution_package_id,
                   version = disconnector.version + 1,
                   updated_at = now()
              from catenary_asset profile
             where disconnector.source_service = :sourceService
               and disconnector.source_entity_id = :sourceEntityId
               and disconnector.type = 'DISCONNECTOR'
               and profile.source_service = disconnector.source_service
               and profile.source_entity_id = disconnector.profile_source_id
               and profile.type = 'PROFILE'
               and (disconnector.track_id is distinct from profile.track_id
                    or disconnector.execution_package_id is distinct from profile.execution_package_id)
            """, nativeQuery = true)
    int inheritLocationOfDisconnector(@Param("sourceService") String sourceService, @Param("sourceEntityId") String sourceEntityId);

    /**
     * Un aislador de seccion esta en el paquete de su via, que mto-configuration tampoco publica para
     * el. Todos los perfiles de una via traen el mismo (el de la via): se toma el del ultimo que
     * llego. Sin perfiles de su via aun, lo pone {@link #propagatePackageToSectionInsulators}.
     */
    @Modifying
    @Query(value = """
            update catenary_asset insulator
               set execution_package_id = (
                       select profile.execution_package_id
                         from catenary_asset profile
                        where profile.source_service = insulator.source_service
                          and profile.type = 'PROFILE'
                          and profile.track_id = insulator.track_id
                          and profile.execution_package_id is not null
                        order by profile.updated_at desc, profile.id
                        limit 1),
                   version = insulator.version + 1,
                   updated_at = now()
             where insulator.source_service = :sourceService
               and insulator.source_entity_id = :sourceEntityId
               and insulator.type = 'SECTION_INSULATOR'
               and insulator.track_id is not null
            """, nativeQuery = true)
    int inheritPackageOfSectionInsulator(@Param("sourceService") String sourceService, @Param("sourceEntityId") String sourceEntityId);

    /**
     * Tras el upsert de un perfil, sus seccionadores le siguen: da igual que evento llegue antes, y un
     * perfil que cambia de via se lleva al seccionador con el.
     */
    @Modifying
    @Query(value = """
            update catenary_asset disconnector
               set track_id = profile.track_id,
                   execution_package_id = profile.execution_package_id,
                   version = disconnector.version + 1,
                   updated_at = now()
              from catenary_asset profile
             where profile.source_service = :sourceService
               and profile.source_entity_id = :profileSourceEntityId
               and profile.type = 'PROFILE'
               and disconnector.source_service = profile.source_service
               and disconnector.profile_source_id = profile.source_entity_id
               and disconnector.type = 'DISCONNECTOR'
               and (disconnector.track_id is distinct from profile.track_id
                    or disconnector.execution_package_id is distinct from profile.execution_package_id)
            """, nativeQuery = true)
    int propagateLocationToDisconnectors(@Param("sourceService") String sourceService,
                                         @Param("profileSourceEntityId") String profileSourceEntityId);

    /** Tras el upsert de un perfil, los aisladores de su via toman su paquete: cambia con el de la via. */
    @Modifying
    @Query(value = """
            update catenary_asset insulator
               set execution_package_id = profile.execution_package_id,
                   version = insulator.version + 1,
                   updated_at = now()
              from catenary_asset profile
             where profile.source_service = :sourceService
               and profile.source_entity_id = :profileSourceEntityId
               and profile.type = 'PROFILE'
               and profile.execution_package_id is not null
               and insulator.source_service = profile.source_service
               and insulator.track_id = profile.track_id
               and insulator.type = 'SECTION_INSULATOR'
               and insulator.execution_package_id is distinct from profile.execution_package_id
            """, nativeQuery = true)
    int propagatePackageToSectionInsulators(@Param("sourceService") String sourceService,
                                            @Param("profileSourceEntityId") String profileSourceEntityId);

    /**
     * Una via borrada en origen deja sin sentido todo lo que hay sobre ella. En lo sincronizado es el
     * origen quien lo dice ({@code enabled_at_source}), y la marca avanza hasta el borrado: la
     * secuencia es global en mto-configuration, asi que un evento de un perfil anterior al borrado que
     * llegue despues se descarta en lugar de reactivarlo. Un tramo propio no tiene origen: queda
     * desactivado aqui, y una persona puede reactivarlo.
     */
    @Modifying
    @Query(value = """
            update catenary_asset
               set enabled_at_source = case when source_service is null then null else false end,
                   disabled_locally = disabled_locally or source_service is null,
                   enabled = false,
                   source_sequence_number = case when source_service is null then source_sequence_number
                                                 else greatest(source_sequence_number, cast(:sequenceNumber as bigint)) end,
                   version = version + 1,
                   updated_at = now()
             where track_id = :trackId
               and (source_service is null
                    or source_sequence_number is null
                    or cast(:sequenceNumber as bigint) is null
                    or cast(:sequenceNumber as bigint) >= source_sequence_number)
            """, nativeQuery = true)
    int deactivateByTrack(@Param("trackId") Long trackId, @Param("sequenceNumber") Long sequenceNumber);
}
