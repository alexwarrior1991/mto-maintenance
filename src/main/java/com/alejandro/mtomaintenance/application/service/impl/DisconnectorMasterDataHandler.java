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
 * En un poste, la via y el paquete no vienen en el evento: son los de su perfil, y se toman de el.
 * Sin poste (los de los porticos de subestacion, los de puesta a tierra), mto-configuration publica
 * desde su V26 el KP y la via del propio seccionador; el paquete sigue sin venir, y es el de su via,
 * como el de un aislador de seccion.
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
        boolean onAPole = profile.has("id");
        // Un punto, no un tramo: el KP de su perfil o, sin poste, el suyo. Un evento anterior a la V26
        // de mto-configuration no trae ni el uno ni la via, y el activo se queda sin ellos, como antes.
        BigDecimal kp = onAPole ? profile.decimal("kp") : payload.decimal("kp");
        return new AssetSnapshot(
                CatenaryAssetType.DISCONNECTOR,
                CODE_PREFIX,
                payload.string("name"),
                null,
                onAPole ? null : payload.nested("track").longValue("id"),
                // La otra via de uno que pone dos en paralelo (V27 de mto-configuration), con poste o
                // sin el: es del seccionador, no de su perfil. En la misma columna que la del aislador,
                // asi que los informes por via lo encuentran en las dos, y la busqueda de activos
                // tambien por connectedTrackId.
                payload.nested("connectedTrack").longValue("id"),
                payload.nested("station").longValue("id"),
                kp,
                kp,
                onAPole ? String.valueOf(profile.longValue("id")) : null,
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

    /**
     * En un poste, la via y el paquete de su perfil; sin el, el paquete de su via. Cada sentencia
     * solo toca el caso que es suyo, y lo que aun no ha llegado lo pone el perfil al llegar.
     */
    @Override
    protected void afterSynchronized(String sourceEntityId) {
        assets().inheritLocationOfDisconnector(SOURCE_SERVICE, sourceEntityId);
        assets().inheritPackageOfTrack(SOURCE_SERVICE, sourceEntityId);
    }
}
