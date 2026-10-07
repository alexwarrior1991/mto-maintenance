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
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
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
import java.util.HashMap;
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
        assertTrue(assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, disconnectorId).orElseThrow().getEnabled(),
                "Its profile (77) never arrived, so the disconnector has no track to go down with");
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
    void aDisconnectorTakesTheTrackAndPackageOfItsProfileWhicheverArrivesFirst() {
        MasterDataEventHandler dispatcher = dispatcher();
        long trackId = System.nanoTime();
        long profileId = trackId + 7;
        String early = "d-early-" + trackId;
        String late = "d-late-" + trackId;

        // El seccionador llega antes que su perfil: mto-configuration no publica su via ni su paquete.
        dispatcher.handle(disconnectorMessage(early, profileId), new MasterDataEventContext(1L));
        entityManager.clear();
        assertNull(asset(early).getTrackId());
        assertNull(asset(early).getExecutionPackageId());

        // Llega el perfil y se los pasa; uno que llega despues los toma al momento.
        dispatcher.handle(trackProfileMessage(profileId, trackId, 6L, MasterDataOperation.CREATED), new MasterDataEventContext(1L));
        dispatcher.handle(disconnectorMessage(late, profileId), new MasterDataEventContext(1L));
        entityManager.clear();
        assertEquals(trackId, asset(early).getTrackId());
        assertEquals(6L, asset(early).getExecutionPackageId());
        assertEquals(trackId, asset(late).getTrackId());
        assertEquals(6L, asset(late).getExecutionPackageId());

        // Un evento del seccionador no se los quita: se vuelven a tomar del perfil.
        dispatcher.handle(disconnectorMessage(early, profileId), new MasterDataEventContext(2L));
        entityManager.clear();
        assertEquals(trackId, asset(early).getTrackId());

        // El perfil cambia de via y de paquete: se lleva a sus seccionadores.
        dispatcher.handle(trackProfileMessage(profileId, trackId + 1, 7L, MasterDataOperation.UPDATED), new MasterDataEventContext(2L));
        entityManager.clear();
        assertEquals(trackId + 1, asset(early).getTrackId());
        assertEquals(7L, asset(early).getExecutionPackageId());
        assertEquals(trackId + 1, asset(late).getTrackId());

        // Ya estan sobre la via, asi que su borrado en origen los desactiva con ella.
        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.TRACK, String.valueOf(trackId + 1), MasterDataOperation.DELETED,
                Map.of("id", trackId + 1)), new MasterDataEventContext(3L));
        entityManager.clear();
        assertFalse(asset(early).getEnabled());
        assertFalse(asset(late).getEnabled());
    }

    /**
     * Uno que no esta en un poste (los de portico de subestacion, los de puesta a tierra): desde la
     * V26 de mto-configuration su evento trae su KP y su via, y el paquete es el de su via, como el
     * de un aislador. Y pasar de un poste a ninguno, o al reves, cambia de regla sin dejar restos.
     */
    @Test
    void aDisconnectorWithoutAPoleBringsItsKpAndTrackAndTakesThePackageOfItsTrack() {
        MasterDataEventHandler dispatcher = dispatcher();
        long trackId = System.nanoTime();
        long profileId = trackId + 7;
        String early = "d-nopole-early-" + trackId;
        String late = "d-nopole-late-" + trackId;
        String moving = "d-moving-" + trackId;

        dispatcher.handle(poleLessDisconnectorMessage(early, trackId, "98375.500"), new MasterDataEventContext(1L));
        entityManager.clear();
        assertEquals(0, new BigDecimal("98375.5").compareTo(asset(early).getStartKp()));
        assertEquals(0, new BigDecimal("98375.5").compareTo(asset(early).getEndKp()), "A point, not a range");
        assertEquals(trackId, asset(early).getTrackId());
        assertNull(asset(early).getProfileSourceId());
        assertNull(asset(early).getExecutionPackageId(), "No profile of its track yet");

        // Un perfil de su via le pasa el paquete; uno que llega despues lo toma al momento.
        dispatcher.handle(trackProfileMessage(profileId, trackId, 6L, MasterDataOperation.CREATED), new MasterDataEventContext(1L));
        dispatcher.handle(poleLessDisconnectorMessage(late, trackId, "98400"), new MasterDataEventContext(1L));
        entityManager.clear();
        assertEquals(6L, asset(early).getExecutionPackageId());
        assertEquals(6L, asset(late).getExecutionPackageId());

        // La via pasa a otro paquete: le siguen.
        dispatcher.handle(trackProfileMessage(profileId, trackId, 8L, MasterDataOperation.UPDATED), new MasterDataEventContext(2L));
        entityManager.clear();
        assertEquals(8L, asset(early).getExecutionPackageId());
        assertEquals(8L, asset(late).getExecutionPackageId());

        // En un poste, todo es de su perfil; al dejarlo, su KP y su via, y el paquete de esa via.
        dispatcher.handle(disconnectorMessage(moving, profileId), new MasterDataEventContext(1L));
        entityManager.clear();
        assertEquals(0, new BigDecimal("80196.63").compareTo(asset(moving).getStartKp()));
        assertEquals(trackId, asset(moving).getTrackId());
        assertEquals(8L, asset(moving).getExecutionPackageId());

        dispatcher.handle(poleLessDisconnectorMessage(moving, trackId + 1, "120"), new MasterDataEventContext(2L));
        entityManager.clear();
        assertNull(asset(moving).getProfileSourceId());
        assertEquals(0, new BigDecimal("120").compareTo(asset(moving).getStartKp()));
        assertEquals(trackId + 1, asset(moving).getTrackId());
        assertNull(asset(moving).getExecutionPackageId(), "No profile on its new track: not the old package");

        // Sin via tampoco hay paquete, y el evento de antes de la V26, sin KP ni via, deja ambos vacios.
        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.DISCONNECTOR, moving, MasterDataOperation.UPDATED,
                Map.of("id", moving, "name", "HSA-FP1.1", "station", Map.of("id", 9))), new MasterDataEventContext(3L));
        entityManager.clear();
        assertNull(asset(moving).getTrackId());
        assertNull(asset(moving).getStartKp());
        assertNull(asset(moving).getExecutionPackageId());
    }

    @Test
    void aSectionInsulatorTakesThePackageOfTheProfilesOfItsTrackWhicheverArrivesFirst() {
        MasterDataEventHandler dispatcher = dispatcher();
        long trackId = System.nanoTime();
        String early = "s-early-" + trackId;
        String late = "s-late-" + trackId;

        dispatcher.handle(insulatorOnTrack(early, trackId, trackId + 1), new MasterDataEventContext(1L));
        entityManager.clear();
        assertNull(asset(early).getExecutionPackageId(), "No profile of its track yet");

        dispatcher.handle(trackProfileMessage(trackId + 11, trackId, 6L, MasterDataOperation.CREATED), new MasterDataEventContext(1L));
        // Un perfil de la otra via que conecta no cambia nada: el paquete es el de su via.
        dispatcher.handle(trackProfileMessage(trackId + 12, trackId + 1, 9L, MasterDataOperation.CREATED), new MasterDataEventContext(1L));
        dispatcher.handle(insulatorOnTrack(late, trackId, null), new MasterDataEventContext(1L));
        entityManager.clear();
        assertEquals(6L, asset(early).getExecutionPackageId());
        assertEquals(6L, asset(late).getExecutionPackageId());
        assertEquals(trackId, asset(early).getTrackId(), "The track of an insulator does come in its event");

        // La via pasa a otro paquete: sus perfiles se republican con el, y sus aisladores le siguen.
        dispatcher.handle(trackProfileMessage(trackId + 11, trackId, 8L, MasterDataOperation.UPDATED), new MasterDataEventContext(2L));
        entityManager.clear();
        assertEquals(8L, asset(early).getExecutionPackageId());
        assertEquals(8L, asset(late).getExecutionPackageId());
    }

    @Test
    void aLocalDisableSurvivesEveryMasterDataEventButDoesNotOverrideTheSource() {
        MasterDataEventHandler dispatcher = dispatcher();
        String profileId = String.valueOf(System.nanoTime());
        dispatcher.handle(profileMessage(UUID.randomUUID(), profileId, "12-2.27", "12847.99", MasterDataOperation.CREATED), new MasterDataEventContext(1L));
        entityManager.clear();
        CatenaryAsset asset = asset(profileId);
        asset.disableLocally();
        assetRepository.saveAndFlush(asset);
        entityManager.clear();

        // Un cambio y un republicado (todo UPDATED, con enabled=true) ya no lo reactivan.
        dispatcher.handle(profileMessage(UUID.randomUUID(), profileId, "12-2.27 renamed", "12847.99", MasterDataOperation.UPDATED), new MasterDataEventContext(2L));
        dispatcher.handle(profileMessage(UUID.randomUUID(), profileId, "12-2.27 renamed", "12847.99", MasterDataOperation.UPDATED), new MasterDataEventContext(3L));
        entityManager.clear();
        CatenaryAsset afterEvents = asset(profileId);
        assertEquals("12-2.27 renamed", afterEvents.getName(), "The events still apply");
        assertFalse(afterEvents.getEnabled());
        assertTrue(afterEvents.getEnabledAtSource());
        assertTrue(afterEvents.getDisabledLocally());

        // Reactivado aqui vuelve; borrado en el origen, gana el origen aunque aqui este activo.
        afterEvents.enableLocally();
        assetRepository.saveAndFlush(afterEvents);
        entityManager.clear();
        assertTrue(asset(profileId).getEnabled());
        dispatcher.handle(profileMessage(UUID.randomUUID(), profileId, "12-2.27 renamed", "12847.99", MasterDataOperation.DELETED), new MasterDataEventContext(4L));
        entityManager.clear();
        CatenaryAsset deleted = asset(profileId);
        assertFalse(deleted.getEnabled());
        assertFalse(deleted.getEnabledAtSource());
        assertFalse(deleted.getDisabledLocally());
        deleted.enableLocally();
        assertFalse(deleted.getEnabled(), "Lifting the local decision does not override the source");
    }

    @Test
    void aTrackDeletionAdvancesTheWatermarkOfWhatIsOnItAndDisablesAnOwnSectionLocally() {
        MasterDataEventHandler dispatcher = dispatcher();
        long trackId = System.nanoTime();
        long profileId = trackId + 3;
        dispatcher.handle(trackProfileMessage(profileId, trackId, 6L, MasterDataOperation.CREATED), new MasterDataEventContext(5L));
        CatenaryAsset section = assetRepository.saveAndFlush(CatenaryAsset.builder().code("SEC-DEL-" + trackId).name("Own section")
                .type(CatenaryAssetType.TRACK_SECTION).trackId(trackId).startKp(new BigDecimal("80000.000")).endKp(new BigDecimal("81000.000"))
                .trackKind(TrackKind.MAIN).build());

        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.TRACK, String.valueOf(trackId), MasterDataOperation.DELETED,
                Map.of("id", trackId)), new MasterDataEventContext(10L));
        // Un cambio del perfil anterior al borrado que llega despues: la secuencia es global, asi que se descarta.
        dispatcher.handle(trackProfileMessage(profileId, trackId, 6L, MasterDataOperation.UPDATED), new MasterDataEventContext(7L));
        entityManager.clear();

        CatenaryAsset profile = asset(String.valueOf(profileId));
        assertFalse(profile.getEnabled(), "It used to come back with the next event of the profile");
        assertEquals(10L, profile.getSourceSequenceNumber());
        CatenaryAsset ownSection = assetRepository.findById(section.getId()).orElseThrow();
        assertFalse(ownSection.getEnabled());
        assertTrue(ownSection.getDisabledLocally(), "An own section has no source: disabled here, so a person can bring it back");
        assertNull(ownSection.getEnabledAtSource());
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

    private CatenaryAsset asset(String sourceEntityId) {
        return assetRepository.findBySourceServiceAndSourceEntityId(SOURCE_SERVICE, sourceEntityId).orElseThrow();
    }

    /** Un seccionador tal como lo publica mto-configuration: estacion y perfil, sin via ni paquete. */
    private static MasterDataChangedMessage disconnectorMessage(String disconnectorId, long profileId) {
        return message(UUID.randomUUID(), MasterDataEntityNames.DISCONNECTOR, disconnectorId, MasterDataOperation.UPDATED,
                Map.of("id", disconnectorId, "name", "HSA-NS5", "station", Map.of("id", 9),
                        "profile", Map.of("id", profileId, "profileId", "80-1.04", "kp", 80196.63)));
    }

    /**
     * Un seccionador sin poste tal como lo publica mto-configuration desde su V26: su estacion, su KP
     * y su via ({@code {id, name}}), con {@code profile} a null; sin paquete.
     */
    private static MasterDataChangedMessage poleLessDisconnectorMessage(String disconnectorId, long trackId, String kp) {
        Map<String, Object> values = new HashMap<>(Map.of("id", disconnectorId, "name", "HSA-FP1.1", "station", Map.of("id", 9),
                "kp", new BigDecimal(kp), "track", Map.of("id", trackId, "name", "TRACK 1")));
        values.put("profile", null);
        return message(UUID.randomUUID(), MasterDataEntityNames.DISCONNECTOR, disconnectorId, MasterDataOperation.UPDATED, values);
    }

    private static MasterDataChangedMessage trackProfileMessage(long profileId, long trackId, long executionPackageId, MasterDataOperation operation) {
        return message(UUID.randomUUID(), MasterDataEntityNames.PROFILE, String.valueOf(profileId), operation,
                Map.of("id", profileId, "profileId", "P-" + profileId, "kp", 80196.63,
                        "track", Map.of("id", trackId, "executionPackageId", executionPackageId)));
    }

    /** Un aislador tal como lo publica mto-configuration: su via y la que conecta, sin paquete. */
    private static MasterDataChangedMessage insulatorOnTrack(String insulatorId, long trackId, Long connectedTrackId) {
        Map<String, Object> values = new HashMap<>(Map.of("id", insulatorId, "name", "SI-12", "station", Map.of("id", 9),
                "track", Map.of("id", trackId)));
        if (connectedTrackId != null) {
            values.put("connectedTrack", Map.of("id", connectedTrackId));
        }
        return message(UUID.randomUUID(), MasterDataEntityNames.SECTION_INSULATOR, insulatorId, MasterDataOperation.UPDATED, values);
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
