package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataChangedEvent;
import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataChangedMessage;
import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataEventContext;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.application.service.MasterDataEntityHandler;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

/**
 * Sincronizacion de un activo desde un evento de datos maestros. CREATED y UPDATED hacen el mismo
 * upsert (la marca de agua decide si se aplica) y DELETED desactiva; nunca se borra, porque ordenes,
 * inspecciones y defectos apuntan al activo. Corre dentro de la transaccion del inbox: no abre otra.
 */
abstract class AbstractAssetMasterDataHandler implements MasterDataEntityHandler {

    static final String SOURCE_SERVICE = "mto-configuration";

    private static final int MAX_NAME_LENGTH = 255;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CatenaryAssetRepository repository;

    protected AbstractAssetMasterDataHandler(CatenaryAssetRepository repository) {
        this.repository = repository;
    }

    /** Snapshot del activo tal y como se guarda: lo que cada tipo lee del payload. */
    protected record AssetSnapshot(
            CatenaryAssetType type,
            String codePrefix,
            String name,
            Long executionPackageId,
            Long trackId,
            Long stationId,
            BigDecimal kp,
            String profileSourceId,
            String sectioning,
            boolean enabled
    ) {
    }

    protected abstract AssetSnapshot snapshot(MasterDataPayload payload, String sourceEntityId);

    @Override
    public void onCreated(MasterDataChangedMessage message, MasterDataEventContext context) {
        synchronize(message, context);
    }

    @Override
    public void onUpdated(MasterDataChangedMessage message, MasterDataEventContext context) {
        synchronize(message, context);
    }

    @Override
    public void onDeleted(MasterDataChangedMessage message, MasterDataEventContext context) {
        String sourceEntityId = MasterDataPayload.sourceEntityId(message.data(), entityName());
        int deactivated = repository.deactivateFromMasterData(SOURCE_SERVICE, sourceEntityId, context.sequenceNumber());
        if (deactivated == 0) {
            boolean known = repository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, sourceEntityId).isPresent();
            logger.info(known
                            ? "{} deletion discarded, a newer change was already applied: sourceEntityId={}, sequenceNumber={}"
                            : "{} deleted but no asset came from it, nothing to do: sourceEntityId={}, sequenceNumber={}",
                    entityName(), sourceEntityId, context.sequenceNumber());
            return;
        }
        logger.info("Asset disabled after its {} was deleted: sourceEntityId={}, sequenceNumber={}",
                entityName(), sourceEntityId, context.sequenceNumber());
    }

    private void synchronize(MasterDataChangedMessage message, MasterDataEventContext context) {
        MasterDataChangedEvent event = message.data();
        String sourceEntityId = MasterDataPayload.sourceEntityId(event, entityName());
        AssetSnapshot snapshot = snapshot(MasterDataPayload.of(event), sourceEntityId);
        String code = snapshot.codePrefix() + sourceEntityId;
        String name = truncate(snapshot.name() == null || snapshot.name().isBlank() ? code : snapshot.name());
        int applied;
        try {
            applied = repository.upsertFromMasterData(
                    SOURCE_SERVICE, sourceEntityId, code, name, snapshot.type().name(),
                    snapshot.executionPackageId(), snapshot.trackId(), snapshot.stationId(),
                    snapshot.kp(), snapshot.kp(), snapshot.profileSourceId(), snapshot.sectioning(),
                    snapshot.enabled(), context.sequenceNumber());
        } catch (DataIntegrityViolationException exception) {
            // El choque realista es con uq_catenary_asset_code: alguien creo a mano un activo con ese
            // codigo. Sin este mensaje, en la DLQ solo se lee una violacion de restriccion sin contexto.
            throw new ValidationException(("Asset '%s' cannot be synchronized from %s %s: another asset already uses "
                    + "that code, or the source columns collide. Rename or remove the conflicting asset.")
                    .formatted(code, entityName(), sourceEntityId));
        }
        if (applied == 0) {
            // La sentencia no toca la fila cuando el evento viene por detras de lo ya aplicado. No es
            // un fallo: es exactamente lo que tiene que pasar con un cambio que llega tarde.
            logger.info("{} change discarded, a newer one was already applied: sourceEntityId={}, code={}, sequenceNumber={}",
                    entityName(), sourceEntityId, code, context.sequenceNumber());
            return;
        }
        logger.info("Asset synchronized from {}: sourceEntityId={}, code={}, enabled={}, sequenceNumber={}",
                entityName(), sourceEntityId, code, snapshot.enabled(), context.sequenceNumber());
    }

    private static String truncate(String name) {
        String trimmed = name.trim();
        return trimmed.length() <= MAX_NAME_LENGTH ? trimmed : trimmed.substring(0, MAX_NAME_LENGTH);
    }
}
