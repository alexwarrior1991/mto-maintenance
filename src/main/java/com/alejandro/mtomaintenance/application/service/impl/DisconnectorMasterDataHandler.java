package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataEntityNames;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetSwitchRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seccionador: tiene inspeccion propia. Cuelga de una estacion y, si viene, de un perfil (con su kp).
 * La via y el paquete no vienen en el evento: son los de su perfil, y se toman de el.
 */
@Service
class DisconnectorMasterDataHandler extends AbstractAssetMasterDataHandler {

    static final String CODE_PREFIX = "DSC-";

    DisconnectorMasterDataHandler(CatenaryAssetRepository repository,
                                  CatenaryAssetSwitchRepository switchRepository) {
        super(repository, switchRepository);
    }

    @Override
    public String entityName() {
        return MasterDataEntityNames.DISCONNECTOR;
    }

    @Override
    protected AssetSnapshot snapshot(MasterDataPayload payload, String sourceEntityId) {
        MasterDataPayload profile = payload.nested("profile");
        // Como el perfil del que cuelga: un punto, no un tramo.
        BigDecimal kp = profile.decimal("kp");
        return new AssetSnapshot(
                CatenaryAssetType.DISCONNECTOR,
                CODE_PREFIX,
                payload.string("name"),
                null,
                null,
                null,
                payload.nested("station").longValue("id"),
                kp,
                kp,
                profile.has("id") ? String.valueOf(profile.longValue("id")) : null,
                null,
                null,
                List.of(),
                // mto-configuration NO publica hoy este campo para un seccionador: su entidad no lo
                // tiene, y uno que deja de existir llega como DELETED, que ya lo desactiva. Se lee de
                // todos modos, con true por defecto, para que los tres handlers traten el estado
                // igual si algun dia el origen lo anade.
                payload.bool("enabled", true)
        );
    }

    /** Si su perfil aun no ha llegado, la via y el paquete los pone el perfil al llegar. */
    @Override
    protected void afterSynchronized(String sourceEntityId) {
        assets().inheritLocationOfDisconnector(SOURCE_SERVICE, sourceEntityId);
    }
}
