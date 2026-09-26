package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataChangedEvent;
import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataChangedMessage;
import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataEventContext;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.application.service.MasterDataEntityHandler;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetSwitch;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.SectionInsulatorInstallation;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetSwitchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sincronizacion de un activo desde un evento de datos maestros. CREATED y UPDATED hacen el mismo
 * upsert (la marca de agua decide si se aplica) y DELETED desactiva; nunca se borra, porque ordenes,
 * inspecciones y defectos apuntan al activo. Corre dentro de la transaccion del inbox: no abre otra.
 */
abstract class AbstractAssetMasterDataHandler implements MasterDataEntityHandler {

    static final String SOURCE_SERVICE = "mto-configuration";

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_SWITCH_CODE_LENGTH = 40;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CatenaryAssetRepository repository;
    private final CatenaryAssetSwitchRepository switchRepository;

    protected AbstractAssetMasterDataHandler(CatenaryAssetRepository repository,
                                             CatenaryAssetSwitchRepository switchRepository) {
        this.repository = repository;
        this.switchRepository = switchRepository;
    }

    /**
     * Snapshot del activo tal y como se guarda: lo que cada tipo lee del payload.
     *
     * @param startKp y {@code endKp} delimitan el activo a lo largo de la via. Lo que esta en un
     *                punto los trae iguales; un aislador sobre una conexion entre vias abarca desde
     *                la primera de sus agujas hasta la ultima
     */
    protected record AssetSnapshot(
            CatenaryAssetType type,
            String codePrefix,
            String name,
            Long executionPackageId,
            Long trackId,
            Long connectedTrackId,
            Long stationId,
            BigDecimal startKp,
            BigDecimal endKp,
            String profileSourceId,
            String sectioning,
            SectionInsulatorInstallation installationType,
            List<SwitchSnapshot> switches,
            boolean enabled
    ) {
    }

    /**
     * Una aguja del aislador: lo que hace falta para situar al equipo sobre ella.
     *
     * @param enabled si esta en servicio. Una aguja dada de baja llega igual en el evento, y se
     *                guarda marcada en lugar de descartarse: «no existe» y «esta fuera de
     *                servicio» no son lo mismo para quien va de noche
     */
    protected record SwitchSnapshot(
            String code,
            BigDecimal kp,
            Integer turnoutDenominator,
            Long trackId,
            boolean enabled
    ) {
    }

    protected abstract AssetSnapshot snapshot(MasterDataPayload payload, String sourceEntityId);

    /**
     * Si este manejador es duenno de las agujas del activo.
     *
     * <p>Falso por defecto, y eso es lo que importa: un manejador que no sabe nada de agujas no
     * puede borrar las que haya. Solo el del aislador de seccion lo pone a cierto, y entonces una
     * lista vacia SI las vacia —es lo que significa un aislador que se quedo sin agujas—.
     */
    protected boolean ownsSwitches() {
        return false;
    }

    /**
     * Lo que se deriva de otros activos tras un upsert aplicado, en la misma transaccion: la via y el
     * paquete que el origen no publica para un activo y que tiene otro (el perfil de un seccionador,
     * los perfiles de la via de un aislador). Nada por defecto.
     */
    protected void afterSynchronized(String sourceEntityId) {
    }

    protected CatenaryAssetRepository assets() {
        return repository;
    }

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
                    snapshot.executionPackageId(), snapshot.trackId(), snapshot.connectedTrackId(),
                    snapshot.stationId(), snapshot.startKp(), snapshot.endKp(),
                    snapshot.profileSourceId(), snapshot.sectioning(),
                    snapshot.installationType() == null ? null : snapshot.installationType().name(),
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
            //
            // Y por eso las agujas tampoco se tocan: reescribirlas aqui desharia lo que ya aplico un
            // evento mas nuevo, que es justo lo que la marca de agua existe para impedir.
            logger.info("{} change discarded, a newer one was already applied: sourceEntityId={}, code={}, sequenceNumber={}",
                    entityName(), sourceEntityId, code, context.sequenceNumber());
            return;
        }

        synchronizeSwitches(snapshot, sourceEntityId);
        afterSynchronized(sourceEntityId);

        logger.info("Asset synchronized from {}: sourceEntityId={}, code={}, enabled={}, switches={}, sequenceNumber={}",
                entityName(), sourceEntityId, code, snapshot.enabled(), snapshot.switches().size(),
                context.sequenceNumber());
    }

    /**
     * Reemplaza el bloque entero de agujas del activo.
     *
     * <p>El upsert devuelve un contador, no el id, asi que hay que releer el activo. No es un
     * leer-y-escribir de los que esta clase evita: el {@code insert ... on conflict do update} ya
     * tomo el bloqueo de esa fila en ESTA transaccion, de modo que dos entregas concurrentes del
     * mismo aislador se serializan sobre ella, y se llega aqui solo cuando el upsert se aplico —o
     * sea, cuando este evento es el mas nuevo—.
     *
     * <p>Borrar y reinsertar en lugar de reconciliar: el emisor manda siempre la lista completa,
     * asi que lo que llega es el estado final. Corre dentro de la transaccion del inbox, que el
     * manejador no debe partir.
     */
    private void synchronizeSwitches(AssetSnapshot snapshot, String sourceEntityId) {
        if (!ownsSwitches()) {
            return;
        }

        CatenaryAsset asset = repository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, sourceEntityId)
                .orElse(null);

        if (asset == null) {
            // No deberia pasar: el upsert acaba de aplicarse. Si pasa, no hay donde colgar las
            // agujas y lo unico honesto es decirlo en lugar de seguir como si nada.
            logger.warn("Asset synchronized from {} but could not be read back, its switches are not stored: sourceEntityId={}",
                    entityName(), sourceEntityId);
            return;
        }

        switchRepository.deleteByAssetId(asset.getId());

        List<CatenaryAssetSwitch> rows = snapshot.switches().stream()
                .filter(each -> each.code() != null && !each.code().isBlank())
                .map(each -> CatenaryAssetSwitch.builder()
                        .asset(asset)
                        .code(truncateCode(each.code()))
                        .kp(each.kp())
                        .turnoutDenominator(positiveOrNull(each.turnoutDenominator()))
                        .trackId(each.trackId())
                        .enabled(each.enabled())
                        .build())
                .toList();

        // saveAllAndFlush y no saveAll: las inserciones tienen que llegar a la base DESPUES del
        // borrado y dentro de esta transaccion. Ademas, una violacion de uq_catenary_asset_switch
        // —dos agujas con el mismo codigo en el evento— salta aqui, donde el consumidor la puede
        // mandar a la DLQ con contexto, y no al confirmar, donde ya no hay quien la explique.
        switchRepository.saveAllAndFlush(rows);
    }

    private static String truncateCode(String code) {
        String trimmed = code.trim();
        return trimmed.length() <= MAX_SWITCH_CODE_LENGTH ? trimmed : trimmed.substring(0, MAX_SWITCH_CODE_LENGTH);
    }

    /** El CHECK de la tabla exige positivo; un cero o un negativo del origen se guarda como nada. */
    private static Integer positiveOrNull(Integer turnoutDenominator) {
        return turnoutDenominator != null && turnoutDenominator > 0 ? turnoutDenominator : null;
    }

    private static String truncate(String name) {
        String trimmed = name.trim();
        return trimmed.length() <= MAX_NAME_LENGTH ? trimmed : trimmed.substring(0, MAX_NAME_LENGTH);
    }
}
