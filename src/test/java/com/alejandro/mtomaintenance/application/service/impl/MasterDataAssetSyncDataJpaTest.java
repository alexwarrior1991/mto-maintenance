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
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InboxMessageStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.InboxMessageRepository;
import com.alejandro.mtomaintenance.support.PostgreSQLTestContainer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los eventos de datos maestros, de punta a punta: inbox idempotente, upsert con marca de agua y desactivacion. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
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
                new ProfileMasterDataHandler(assetRepository),
                new DisconnectorMasterDataHandler(assetRepository),
                new SectionInsulatorMasterDataHandler(assetRepository),
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
