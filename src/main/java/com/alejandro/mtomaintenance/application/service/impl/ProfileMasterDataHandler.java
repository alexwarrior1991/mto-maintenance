package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataEntityNames;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetSwitchRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Perfil: el punto kilometrico donde se apoya la catenaria. Es la unidad del preventivo perfil a
 * perfil. La via viaja anidada con su paquete de ejecucion; la estacion NO se deduce de
 * {@code track.stationIds} porque una via larga cruza varias.
 */
@Service
class ProfileMasterDataHandler extends AbstractAssetMasterDataHandler {

    static final String CODE_PREFIX = "PRF-";

    ProfileMasterDataHandler(CatenaryAssetRepository repository,
                             CatenaryAssetSwitchRepository switchRepository) {
        super(repository, switchRepository);
    }

    @Override
    public String entityName() {
        return MasterDataEntityNames.PROFILE;
    }

    @Override
    protected AssetSnapshot snapshot(MasterDataPayload payload, String sourceEntityId) {
        MasterDataPayload track = payload.nested("track");
        // Un perfil esta en un punto, no en un tramo: el mismo kp abre y cierra su rango.
        BigDecimal kp = payload.decimal("kp");
        return new AssetSnapshot(
                CatenaryAssetType.PROFILE,
                CODE_PREFIX,
                payload.string("profileId"),
                track.longValue("executionPackageId"),
                track.longValue("id"),
                null,
                null,
                kp,
                kp,
                null,
                payload.joinedCodes("sectionings"),
                null,
                List.of(),
                true
        );
    }
}
