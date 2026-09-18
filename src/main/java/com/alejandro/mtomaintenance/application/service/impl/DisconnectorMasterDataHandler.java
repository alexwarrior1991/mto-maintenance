package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataEntityNames;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import org.springframework.stereotype.Service;

/** Seccionador: tiene inspeccion propia. Cuelga de una estacion y, si viene, de un perfil (con su kp). */
@Service
class DisconnectorMasterDataHandler extends AbstractAssetMasterDataHandler {

    static final String CODE_PREFIX = "DSC-";

    DisconnectorMasterDataHandler(CatenaryAssetRepository repository) {
        super(repository);
    }

    @Override
    public String entityName() {
        return MasterDataEntityNames.DISCONNECTOR;
    }

    @Override
    protected AssetSnapshot snapshot(MasterDataPayload payload, String sourceEntityId) {
        MasterDataPayload profile = payload.nested("profile");
        return new AssetSnapshot(
                CatenaryAssetType.DISCONNECTOR,
                CODE_PREFIX,
                payload.string("name"),
                null,
                null,
                payload.nested("station").longValue("id"),
                profile.decimal("kp"),
                profile.has("id") ? String.valueOf(profile.longValue("id")) : null,
                null,
                // mto-configuration NO publica hoy este campo para un seccionador: su entidad no lo
                // tiene, y uno que deja de existir llega como DELETED, que ya lo desactiva. Se lee de
                // todos modos, con true por defecto, para que los tres handlers traten el estado
                // igual si algun dia el origen lo anade.
                payload.bool("enabled", true)
        );
    }
}
