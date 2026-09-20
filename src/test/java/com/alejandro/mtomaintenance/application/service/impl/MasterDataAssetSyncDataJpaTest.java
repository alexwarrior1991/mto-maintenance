package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.InboxMessageCommand;
import com.alejandro.mtomaintenance.application.dto.messaging.InboxProcessingResult;
import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataChangedEvent;
import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataChangedMessage;
import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataEntityNames;
import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataEventContext;
import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataOperation;
import com.alejandro.mtomaintenance.application.service.InboxMessageService;
import com.alejandro.mtomaintenance.application.service.MasterDataEventHandler;
import com.alejandro.mtomaintenance.configuration.JpaAuditingConfiguration;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InboxMessageStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.SectionInsulatorInstallation;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetSwitch;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetSwitchRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.InboxMessageRepository;
import com.alejandro.mtomaintenance.support.PostgreSQLTestContainer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los eventos de datos maestros, de punta a punta: inbox idempotente, upsert con marca de agua y desactivacion. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
// Las agujas se escriben con JPA, no con SQL nativo como el activo, asi que necesitan el
// listener de auditoria que rellena created_at/created_by. Mismo @Import que EnversAuditDataJpaTest.
@Import(JpaAuditingConfiguration.class)
class MasterDataAssetSyncDataJpaTest extends PostgreSQLTestContainer {

    private static final String SOURCE_SERVICE = "mto-configuration";

    @DynamicPropertySource
    static void postgreSQLProperties(DynamicPropertyRegistry registry) {
        registerPostgreSQLProperties(registry);
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private InboxMessageRepository inboxMessageRepository;

    @Autowired
    private CatenaryAssetRepository assetRepository;

    @Autowired
    private CatenaryAssetSwitchRepository switchRepository;

    @Test
    void twoDeliveriesOfAProfileLeaveASingleSynchronizedAssetAndOneProcessedInboxRow() {
        InboxMessageService inbox = new InboxMessageServiceImpl(inboxMessageRepository);
        MasterDataEventHandler dispatcher = dispatcher();
        String profileId = String.valueOf(System.nanoTime());
        UUID messageId = UUID.randomUUID();
        MasterDataChangedMessage message = profileMessage(messageId, profileId, "12-2.27", "12847.99", MasterDataOperation.CREATED);

        InboxProcessingResult first = inbox.process(command(messageId, "profile", profileId), () -> dispatcher.handle(message, new MasterDataEventContext(10L)));
        entityManager.flush();
        entityManager.clear();
        InboxProcessingResult second = inbox.process(command(messageId, "profile", profileId), () -> dispatcher.handle(message, new MasterDataEventContext(10L)));
        entityManager.flush();
        entityManager.clear();

        assertEquals(InboxProcessingResult.PROCESSED, first);
        assertEquals(InboxProcessingResult.DUPLICATE_SKIPPED, second);
        CatenaryAsset asset = assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, profileId).orElseThrow();
        assertEquals("PRF-" + profileId, asset.getCode());
        assertEquals("12-2.27", asset.getName());
        assertEquals(CatenaryAssetType.PROFILE, asset.getType());
        assertEquals(2L, asset.getTrackId());
        assertEquals(6L, asset.getExecutionPackageId());
        assertEquals(0, new BigDecimal("12847.990").compareTo(asset.getStartKp()));
        assertEquals("A/S S/A", asset.getSectioning());
        assertEquals(10L, asset.getSourceSequenceNumber());
        assertEquals(InboxMessageStatus.PROCESSED, inboxMessageRepository.findByMessageIdAndSourceService(messageId.toString(), SOURCE_SERVICE).orElseThrow().getStatus());
    }

    @Test
    void aLateUpdateIsDiscardedAndADeletionDisablesTheAssetAdvancingTheWatermark() {
        MasterDataEventHandler dispatcher = dispatcher();
        String profileId = String.valueOf(System.nanoTime());

        dispatcher.handle(profileMessage(UUID.randomUUID(), profileId, "13-2.01", "13007.29", MasterDataOperation.CREATED), new MasterDataEventContext(20L));
        dispatcher.handle(profileMessage(UUID.randomUUID(), profileId, "13-2.01 stale", "13007.29", MasterDataOperation.UPDATED), new MasterDataEventContext(19L));
        entityManager.clear();
        assertEquals("13-2.01", assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, profileId).orElseThrow().getName());

        dispatcher.handle(profileMessage(UUID.randomUUID(), profileId, "13-2.01", "13007.29", MasterDataOperation.DELETED), new MasterDataEventContext(21L));
        entityManager.clear();
        CatenaryAsset disabled = assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, profileId).orElseThrow();
        assertFalse(disabled.getEnabled());
        assertEquals(21L, disabled.getSourceSequenceNumber());
    }

    @Test
    void disconnectorsAndSectionInsulatorsGetTheirOwnAssetsAndATrackDeletionDisablesTheTrack() {
        MasterDataEventHandler dispatcher = dispatcher();
        long trackId = System.nanoTime();
        String profileId = "p" + trackId;
        String disconnectorId = "d" + trackId;
        String insulatorId = "s" + trackId;

        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.PROFILE, profileId, MasterDataOperation.CREATED,
                Map.of("id", profileId, "profileId", "80-1.04", "kp", 80196.63, "track", Map.of("id", trackId, "executionPackageId", 6))), new MasterDataEventContext(1L));
        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.DISCONNECTOR, disconnectorId, MasterDataOperation.CREATED,
                Map.of("id", disconnectorId, "name", "HSA-NS5", "onLoad", true, "station", Map.of("id", 9, "name", "Herzliya"),
                        "profile", Map.of("id", 77, "profileId", "80-1.04", "kp", 80196.63))), new MasterDataEventContext(1L));
        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.SECTION_INSULATOR, insulatorId, MasterDataOperation.CREATED,
                Map.of("id", insulatorId, "name", "SI-12", "enabled", false, "station", Map.of("id", 9))), new MasterDataEventContext(1L));
        entityManager.clear();

        CatenaryAsset disconnector = assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, disconnectorId).orElseThrow();
        assertEquals(CatenaryAssetType.DISCONNECTOR, disconnector.getType());
        assertEquals("DSC-" + disconnectorId, disconnector.getCode());
        assertEquals(9L, disconnector.getStationId());
        assertEquals("77", disconnector.getProfileSourceId());
        assertEquals(0, new BigDecimal("80196.630").compareTo(disconnector.getStartKp()));
        CatenaryAsset insulator = assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, insulatorId).orElseThrow();
        assertEquals(CatenaryAssetType.SECTION_INSULATOR, insulator.getType());
        assertFalse(insulator.getEnabled());

        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.TRACK, String.valueOf(trackId), MasterDataOperation.DELETED, Map.of("id", trackId)), new MasterDataEventContext(2L));
        entityManager.clear();
        assertFalse(assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, profileId).orElseThrow().getEnabled());
        assertTrue(assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, disconnectorId).orElseThrow().getEnabled());
    }

    @Test
    void aSectionInsulatorBringsItsSwitchesItsTracksAndAKpRangeThatSpansThem() {
        MasterDataEventHandler dispatcher = dispatcher();
        String insulatorId = "s" + System.nanoTime();

        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.SECTION_INSULATOR, insulatorId, MasterDataOperation.CREATED,
                insulatorPayload(insulatorId, List.of(
                        // A proposito en orden INVERSO de kp: start_kp y end_kp tienen que salir
                        // ordenados pase lo que pase, o chk_catenary_asset_kp_range revienta.
                        switchPayload("W41", 110249.0, 12, 4),
                        switchPayload("W31", 110176.0, 9, 3)))), new MasterDataEventContext(1L));
        entityManager.clear();

        CatenaryAsset insulator = assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, insulatorId).orElseThrow();
        assertEquals(CatenaryAssetType.SECTION_INSULATOR, insulator.getType());
        assertEquals(SectionInsulatorInstallation.TRACK_CONNECTION, insulator.getInstallationType());
        assertEquals(3L, insulator.getTrackId());
        assertEquals(4L, insulator.getConnectedTrackId());
        assertEquals(0, new BigDecimal("110176.000").compareTo(insulator.getStartKp()));
        assertEquals(0, new BigDecimal("110249.000").compareTo(insulator.getEndKp()));

        List<CatenaryAssetSwitch> switches = switchRepository.findByAssetIdOrderByKpAscCodeAsc(insulator.getId());
        assertEquals(List.of("W31", "W41"), switches.stream().map(CatenaryAssetSwitch::getCode).toList());
        assertEquals(List.of("1:9", "1:12"), switches.stream().map(CatenaryAssetSwitch::turnoutRate).toList());
        assertEquals(List.of(3L, 4L), switches.stream().map(CatenaryAssetSwitch::getTrackId).toList());
    }

    @Test
    void anInsulatorThatLosesASwitchLosesItsRowToo() {
        MasterDataEventHandler dispatcher = dispatcher();
        String insulatorId = "s" + System.nanoTime();

        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.SECTION_INSULATOR, insulatorId, MasterDataOperation.CREATED,
                insulatorPayload(insulatorId, List.of(
                        switchPayload("W31", 110176.0, 9, 3),
                        switchPayload("W41", 110249.0, 12, 4)))), new MasterDataEventContext(1L));
        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.SECTION_INSULATOR, insulatorId, MasterDataOperation.UPDATED,
                insulatorPayload(insulatorId, List.of(
                        switchPayload("W31", 110176.0, 9, 3)))), new MasterDataEventContext(2L));
        entityManager.clear();

        UUID assetId = assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, insulatorId).orElseThrow().getId();
        assertEquals(List.of("W31"), switchRepository.findByAssetIdOrderByKpAscCodeAsc(assetId).stream()
                .map(CatenaryAssetSwitch::getCode).toList());
    }

    /**
     * Un evento atrasado no toca las agujas. Es la mitad del contrato de la marca de agua que se
     * escapaba: la fila del activo ya estaba protegida por el {@code where} del upsert, pero las
     * agujas se escriben despues, asi que hay que comprobar que tampoco se reescriben.
     */
    @Test
    void aLateEventDoesNotRewriteTheSwitches() {
        MasterDataEventHandler dispatcher = dispatcher();
        String insulatorId = "s" + System.nanoTime();

        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.SECTION_INSULATOR, insulatorId, MasterDataOperation.UPDATED,
                insulatorPayload(insulatorId, List.of(
                        switchPayload("W31", 110176.0, 9, 3),
                        switchPayload("W41", 110249.0, 12, 4)))), new MasterDataEventContext(20L));
        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.SECTION_INSULATOR, insulatorId, MasterDataOperation.UPDATED,
                insulatorPayload(insulatorId, List.of(
                        switchPayload("W99", 110900.0, 8, 5)))), new MasterDataEventContext(5L));
        entityManager.clear();

        UUID assetId = assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, insulatorId).orElseThrow().getId();
        assertEquals(List.of("W31", "W41"), switchRepository.findByAssetIdOrderByKpAscCodeAsc(assetId).stream()
                .map(CatenaryAssetSwitch::getCode).toList());
    }

    /** Un aislador sin agujas ni vias sigue siendo valido: es el que esta en medio de una via. */
    @Test
    void aSectionInsulatorWithoutSwitchesStillSynchronizes() {
        MasterDataEventHandler dispatcher = dispatcher();
        String insulatorId = "s" + System.nanoTime();

        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.SECTION_INSULATOR, insulatorId, MasterDataOperation.CREATED,
                Map.of("id", insulatorId, "name", "B7", "enabled", true, "station", Map.of("id", 9),
                        "installationType", "IN_TRACK", "kp", 110176.0)), new MasterDataEventContext(1L));
        entityManager.clear();

        CatenaryAsset insulator = assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, insulatorId).orElseThrow();
        assertEquals(SectionInsulatorInstallation.IN_TRACK, insulator.getInstallationType());
        assertEquals(0, new BigDecimal("110176.000").compareTo(insulator.getStartKp()));
        assertEquals(0, new BigDecimal("110176.000").compareTo(insulator.getEndKp()));
        assertTrue(switchRepository.findByAssetIdOrderByKpAscCodeAsc(insulator.getId()).isEmpty());
    }

    /**
     * El emisor puede mandar un {@code installationType} que este lado no conozca —es un servicio
     * distinto, con su propio ciclo de despliegue—. Eso se guarda como nada, nunca va a la DLQ.
     */
    @Test
    void anUnknownInstallationTypeIsStoredAsNothing() {
        MasterDataEventHandler dispatcher = dispatcher();
        String insulatorId = "s" + System.nanoTime();

        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.SECTION_INSULATOR, insulatorId, MasterDataOperation.CREATED,
                Map.of("id", insulatorId, "name", "B7", "enabled", true, "station", Map.of("id", 9),
                        "installationType", "SOMETHING_NEW")), new MasterDataEventContext(1L));
        entityManager.clear();

        assertNull(assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, insulatorId)
                .orElseThrow().getInstallationType());
    }

    private static Map<String, Object> insulatorPayload(String insulatorId, List<Map<String, Object>> switches) {
        return Map.of("id", insulatorId, "name", "B7", "enabled", true,
                "station", Map.of("id", 9),
                "installationType", "TRACK_CONNECTION",
                "track", Map.of("id", 3, "name", "TRACK 1"),
                "connectedTrack", Map.of("id", 4, "name", "TRACK 2"),
                "switches", switches);
    }

    private static Map<String, Object> switchPayload(String code, double kp, int denominator, long trackId) {
        return switchPayload(code, kp, denominator, trackId, true);
    }

    private static Map<String, Object> switchPayload(String code, double kp, int denominator, long trackId,
                                                     boolean enabled) {
        return Map.of("code", code, "kp", kp, "turnoutDenominator", denominator,
                "turnoutRate", "1:" + denominator, "trackId", trackId, "enabled", enabled);
    }

    /**
     * Una aguja dada de baja en el origen llega igual en el evento —la coleccion de
     * mto-configuration solo filtra los borrados logicos, no las deshabilitadas— y se guarda
     * marcada, no se descarta: para el equipo no es lo mismo que la aguja no exista a que este
     * fuera de servicio. Sin campo, activa, como el resto del payload.
     */
    @Test
    void aSwitchOutOfServiceIsStoredAsSuchInsteadOfBeingDropped() {
        MasterDataEventHandler dispatcher = dispatcher();
        String insulatorId = "s" + System.nanoTime();

        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.SECTION_INSULATOR, insulatorId, MasterDataOperation.CREATED,
                insulatorPayload(insulatorId, List.of(
                        switchPayload("W31", 110176.0, 9, 3, false),
                        switchPayload("W41", 110249.0, 12, 4),
                        // Sin la clave 'enabled': un emisor mas antiguo que no la publicaba.
                        Map.of("code", "W51", "kp", 110300.0, "turnoutDenominator", 8, "trackId", 5)))),
                new MasterDataEventContext(1L));
        entityManager.clear();

        UUID assetId = assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, insulatorId).orElseThrow().getId();
        List<CatenaryAssetSwitch> switches = switchRepository.findByAssetIdOrderByKpAscCodeAsc(assetId);

        assertEquals(List.of("W31", "W41", "W51"), switches.stream().map(CatenaryAssetSwitch::getCode).toList());
        assertEquals(List.of(false, true, true), switches.stream().map(CatenaryAssetSwitch::getEnabled).toList());
    }

    @Test
    void aDisconnectorIsActiveUnlessThePayloadSaysOtherwise() {
        MasterDataEventHandler dispatcher = dispatcher();
        String activeId = "d-on-" + System.nanoTime();
        String disabledId = "d-off-" + System.nanoTime();

        // mto-configuration no publica hoy 'enabled' para un seccionador: sin el campo, activo.
        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.DISCONNECTOR, activeId, MasterDataOperation.CREATED,
                Map.of("id", activeId, "name", "HSA-NS5", "station", Map.of("id", 9))), new MasterDataEventContext(1L));
        // Y si algun dia lo anade, el handler lo respeta igual que el del aislador de seccion.
        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.DISCONNECTOR, disabledId, MasterDataOperation.CREATED,
                Map.of("id", disabledId, "name", "HSA-NS7", "enabled", false, "station", Map.of("id", 9))), new MasterDataEventContext(1L));
        entityManager.clear();

        assertTrue(assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, activeId).orElseThrow().getEnabled());
        assertFalse(assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, disabledId).orElseThrow().getEnabled());
    }

    @Test
    void aCantileverChangeIsRecordedInTheInboxAndTouchesNoAsset() {
        InboxMessageService inbox = new InboxMessageServiceImpl(inboxMessageRepository);
        MasterDataEventHandler dispatcher = dispatcher();
        UUID messageId = UUID.randomUUID();
        long before = assetRepository.count();
        MasterDataChangedMessage cantilever = message(messageId, MasterDataEntityNames.CANTILEVER, "7", MasterDataOperation.UPDATED, Map.of("stagger", "0.20"));

        InboxProcessingResult result = inbox.process(command(messageId, "cantilever", "7"), () -> dispatcher.handle(cantilever, new MasterDataEventContext(10L)));
        entityManager.flush();
        entityManager.clear();

        assertEquals(InboxProcessingResult.PROCESSED, result);
        assertEquals(before, assetRepository.count());
    }

    private MasterDataEventHandler dispatcher() {
        return new DispatchingMasterDataEventHandler(List.of(
                new ProfileMasterDataHandler(assetRepository, switchRepository),
                new DisconnectorMasterDataHandler(assetRepository, switchRepository),
                new SectionInsulatorMasterDataHandler(assetRepository, switchRepository),
                new TrackMasterDataHandler(assetRepository)));
    }

    private static MasterDataChangedMessage profileMessage(UUID messageId, String profileId, String name, String kp, MasterDataOperation operation) {
        return message(messageId, MasterDataEntityNames.PROFILE, profileId, operation, Map.of(
                "id", profileId, "profileId", name, "kp", new BigDecimal(kp), "orderInTrack", 27,
                "track", Map.of("id", 2, "name", "TRACK 2 RAAN-HER", "enabled", true, "stationIds", List.of(5, 9), "executionPackageId", 6),
                "sectionings", List.of(Map.of("id", 1, "code", "A/S"), Map.of("id", 2, "code", "S/A"))));
    }

    private static MasterDataChangedMessage message(UUID messageId, String entityName, String entityId, MasterDataOperation operation, Map<String, Object> values) {
        return new MasterDataChangedMessage(messageId, entityName + "-" + entityId, SOURCE_SERVICE, Instant.now(),
                "MASTER_DATA_" + entityName.toUpperCase().replace('-', '_') + "_" + operation,
                new MasterDataChangedEvent(entityName, entityId, operation, values), "hash");
    }

    private static InboxMessageCommand command(UUID messageId, String entity, String entityId) {
        return new InboxMessageCommand(messageId.toString(), SOURCE_SERVICE, "MASTER_DATA_" + entity.toUpperCase() + "_CREATED", entity,
                entityId, "mto.master-data.exchange", "mto.master-data." + entity + ".created",
                "mto.maintenance.master-data.queue", "9f2c1b0d", "{\"operationId\":\"" + messageId + "\"}", 10L);
    }
}
