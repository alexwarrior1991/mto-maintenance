package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataEntityNames;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import org.springframework.stereotype.Service;

/** Aislador de seccion: inspeccion propia; cuelga de una estacion y trae su propio 'enabled'. */
@Service
class SectionInsulatorMasterDataHandler extends AbstractAssetMasterDataHandler {

    static final String CODE_PREFIX = "SIN-";

    SectionInsulatorMasterDataHandler(CatenaryAssetRepository repository) {
        super(repository);
    }

    @Override
    public String entityName() {
        return MasterDataEntityNames.SECTION_INSULATOR;
    }

    @Override
    protected AssetSnapshot snapshot(MasterDataPayload payload, String sourceEntityId) {
        return new AssetSnapshot(
                CatenaryAssetType.SECTION_INSULATOR,
                CODE_PREFIX,
                payload.string("name"),
                null,
                null,
                payload.nested("station").longValue("id"),
                null,
                null,
                null,
                payload.bool("enabled", true)
        );
    }
}
