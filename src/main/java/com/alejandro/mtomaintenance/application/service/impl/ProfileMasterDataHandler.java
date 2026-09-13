package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataEntityNames;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import org.springframework.stereotype.Service;

/**
 * Perfil: el punto kilometrico donde se apoya la catenaria. Es la unidad del preventivo perfil a
 * perfil. La via viaja anidada con su paquete de ejecucion; la estacion NO se deduce de
 * {@code track.stationIds} porque una via larga cruza varias.
 */
@Service
class ProfileMasterDataHandler extends AbstractAssetMasterDataHandler {

    static final String CODE_PREFIX = "PRF-";

    ProfileMasterDataHandler(CatenaryAssetRepository repository) {
        super(repository);
    }

    @Override
    public String entityName() {
        return MasterDataEntityNames.PROFILE;
    }

    @Override
    protected AssetSnapshot snapshot(MasterDataPayload payload, String sourceEntityId) {
        MasterDataPayload track = payload.nested("track");
        return new AssetSnapshot(
                CatenaryAssetType.PROFILE,
                CODE_PREFIX,
                payload.string("profileId"),
                track.longValue("executionPackageId"),
                track.longValue("id"),
                null,
                payload.decimal("kp"),
                null,
                payload.joinedCodes("sectionings"),
                true
        );
    }
}
