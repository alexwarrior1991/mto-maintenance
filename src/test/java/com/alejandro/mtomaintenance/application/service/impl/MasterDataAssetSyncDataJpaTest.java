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
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DisconnectorDriveType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InboxMessageStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.SectionInsulatorInstallation;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetSwitch;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetSwitchRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.InboxMessageRepository;
import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEvent;
import com.alejandro.mtomaintenance.application.dto.messaging.IncomingEventContext;
import com.alejandro.mtomaintenance.application.service.IncomingEventHandler;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTeam;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CrewPerson;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FleetVehicle;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.UnavailablePeriod;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInstrument;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CrewPersonRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.FleetVehicleRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceInstrumentRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTeamRepository;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Autowired
    private MaintenanceTeamRepository teamRepository;

    @Autowired
    private CrewPersonRepository personRepository;

    @Autowired
    private FleetVehicleRepository vehicleRepository;

    @Autowired
    private MaintenanceInstrumentRepository instrumentRepository;

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
    void aDisconnectorKeepsHowItIsDrivenAndAnUnknownDriveIsStoredAsNothing() {
        MasterDataEventHandler dispatcher = dispatcher();
        long suffix = System.nanoTime();
        String motorId = "d-motor-" + suffix;
        String manualId = "d-manual-" + suffix;
        String unknownId = "d-unknown-" + suffix;
        String withoutId = "d-without-" + suffix;
        dispatcher.handle(withDrive(disconnectorMessage(motorId, suffix), "MOTOR"), new MasterDataEventContext(1L));
        dispatcher.handle(withDrive(disconnectorMessage(manualId, suffix), "manual"), new MasterDataEventContext(1L));
        dispatcher.handle(withDrive(disconnectorMessage(unknownId, suffix), "HYDRAULIC"), new MasterDataEventContext(1L));
        dispatcher.handle(disconnectorMessage(withoutId, suffix), new MasterDataEventContext(1L));
        entityManager.clear();

        assertEquals(DisconnectorDriveType.MOTOR, asset(motorId).getDriveType());
        assertEquals(DisconnectorDriveType.MANUAL, asset(manualId).getDriveType());
        assertNull(asset(unknownId).getDriveType(), "A drive this side does not know is no drive, never the DLQ");
        assertNull(asset(withoutId).getDriveType());

        // Un evento sin el dato (anterior a que se publicara, o porque lo han borrado) lo quita.
        dispatcher.handle(disconnectorMessage(motorId, suffix), new MasterDataEventContext(2L));
        entityManager.clear();
        assertNull(asset(motorId).getDriveType());

        // Ni un perfil ni un aislador llevan accionamiento.
        String profileId = String.valueOf(suffix + 1);
        dispatcher.handle(trackProfileMessage(suffix + 1, suffix, 6L, MasterDataOperation.CREATED), new MasterDataEventContext(3L));
        dispatcher.handle(insulatorOnTrack("si-drive-" + suffix, suffix, null), new MasterDataEventContext(3L));
        entityManager.clear();
        assertNull(asset(profileId).getDriveType());
        assertNull(asset("si-drive-" + suffix).getDriveType());
    }

    @Test
    void aDisconnectorKeepsItsFunctionCodeAsItArrivesAndWithoutOneItHasNone() {
        MasterDataEventHandler dispatcher = dispatcher();
        long suffix = System.nanoTime();
        String valveId = "d-valve-" + suffix;
        String blankId = "d-blank-" + suffix;
        dispatcher.handle(withValue(disconnectorMessage(valveId, suffix), "disconnectorFunction", Map.of("id", 14, "code", " SurgeA ")),
                new MasterDataEventContext(1L));
        dispatcher.handle(withValue(disconnectorMessage(blankId, suffix), "disconnectorFunction", Map.of("id", 15, "code", " ")),
                new MasterDataEventContext(1L));
        entityManager.clear();

        assertEquals("SurgeA", asset(valveId).getDisconnectorFunction(), "The code of mto-configuration, trimmed");
        assertNull(asset(blankId).getDisconnectorFunction(), "A blank code is no function");

        // Un evento sin ella (anterior a que se guardara, o porque la han quitado) la quita.
        dispatcher.handle(disconnectorMessage(valveId, suffix), new MasterDataEventContext(2L));
        entityManager.clear();
        assertNull(asset(valveId).getDisconnectorFunction());
    }

    /** El mismo mensaje con una clave mas en {@code values}, como la publica mto-configuration. */
    private static MasterDataChangedMessage withValue(MasterDataChangedMessage message, String key, Object value) {
        Map<String, Object> values = new HashMap<>(message.data().values());
        values.put(key, value);
        return new MasterDataChangedMessage(message.operationId(), message.referenceId(), message.origin(),
                message.creationDate(), message.eventType(),
                new MasterDataChangedEvent(message.data().entityName(), message.data().entityId(),
                        message.data().operation(), values), message.messageHash());
    }

    /** El mismo mensaje con el accionamiento que publica mto-configuration: {@code MOTOR}, {@code MANUAL} o null. */
    private static MasterDataChangedMessage withDrive(MasterDataChangedMessage message, String driveType) {
        Map<String, Object> values = new HashMap<>(message.data().values());
        values.put("driveType", driveType);
        return new MasterDataChangedMessage(message.operationId(), message.referenceId(), message.origin(),
                message.creationDate(), message.eventType(),
                new MasterDataChangedEvent(message.data().entityName(), message.data().entityId(),
                        message.data().operation(), values), message.messageHash());
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

    /**
     * mto-configuration deja la estacion del seccionador opcional: uno en plena via, en una zona
     * neutra o en una subestacion llega con {@code station} a null. El activo se queda sin estacion
     * (uno que la tenia la pierde), y su via y su paquete siguen saliendo de su poste o de su via,
     * nunca de la estacion.
     */
    @Test
    void aDisconnectorWithoutAStationKeepsTheLocationOfItsPoleOrItsTrack() {
        MasterDataEventHandler dispatcher = dispatcher();
        long trackId = System.nanoTime();
        long profileId = trackId + 7;
        String onAPole = "d-nostation-" + trackId;
        String poleLess = "d-nostation-free-" + trackId;

        dispatcher.handle(trackProfileMessage(profileId, trackId, 6L, MasterDataOperation.CREATED), new MasterDataEventContext(1L));
        dispatcher.handle(disconnectorMessage(onAPole, profileId), new MasterDataEventContext(1L));
        entityManager.clear();
        assertEquals(9L, asset(onAPole).getStationId());

        dispatcher.handle(withoutStation(disconnectorMessage(onAPole, profileId)), new MasterDataEventContext(2L));
        dispatcher.handle(withoutStation(poleLessDisconnectorMessage(poleLess, trackId, "98375.500")),
                new MasterDataEventContext(1L));
        entityManager.clear();
        assertNull(asset(onAPole).getStationId(), "The one that had a station loses it");
        assertEquals(trackId, asset(onAPole).getTrackId());
        assertEquals(6L, asset(onAPole).getExecutionPackageId());
        assertTrue(asset(onAPole).getEnabled());
        assertNull(asset(poleLess).getStationId());
        assertEquals(trackId, asset(poleLess).getTrackId());
        assertEquals(6L, asset(poleLess).getExecutionPackageId());
        assertTrue(asset(poleLess).getEnabled());
    }

    /**
     * Uno de puesta en paralelo trae desde la V27 de mto-configuration la otra via que une con la
     * suya: va a connected_track_id, la columna de la via conectada del aislador, y su perfil no la
     * toca al pasarle la suya. Un evento sin ella, de antes de la V27 o porque ya no la tiene, la quita.
     */
    @Test
    void aParallelingDisconnectorKeepsItsConnectedTrackOnAPoleOrWithoutOne() {
        MasterDataEventHandler dispatcher = dispatcher();
        long trackId = System.nanoTime();
        long otherTrackId = trackId + 1;
        long profileId = trackId + 7;
        String onAPole = "d-pp-" + trackId;
        String poleLess = "d-pp-free-" + trackId;

        dispatcher.handle(trackProfileMessage(profileId, trackId, 6L, MasterDataOperation.CREATED), new MasterDataEventContext(1L));
        dispatcher.handle(parallel(disconnectorMessage(onAPole, profileId), otherTrackId), new MasterDataEventContext(1L));
        dispatcher.handle(parallel(poleLessDisconnectorMessage(poleLess, trackId, "98375.500"), otherTrackId),
                new MasterDataEventContext(1L));
        entityManager.clear();
        assertEquals(trackId, asset(onAPole).getTrackId());
        assertEquals(otherTrackId, asset(onAPole).getConnectedTrackId());
        assertEquals(trackId, asset(poleLess).getTrackId());
        assertEquals(otherTrackId, asset(poleLess).getConnectedTrackId());

        // El perfil vuelve a pasar su via a su seccionador y no le quita la conectada.
        dispatcher.handle(trackProfileMessage(profileId, trackId, 6L, MasterDataOperation.UPDATED), new MasterDataEventContext(2L));
        entityManager.clear();
        assertEquals(otherTrackId, asset(onAPole).getConnectedTrackId());

        dispatcher.handle(disconnectorMessage(onAPole, profileId), new MasterDataEventContext(3L));
        entityManager.clear();
        assertNull(asset(onAPole).getConnectedTrackId());
        assertEquals(trackId, asset(onAPole).getTrackId());
    }

    /**
     * En un haz de vias (talleres, cocheras) un seccionador cuelga a veces del poste de una via y
     * maniobra otra, o une dos que no son la del poste: mto-configuration publica entonces su via
     * tambien con poste. Es la suya (own_track, V14): su perfil le pasa el paquete pero no la pisa, ni
     * al llegar antes o despues ni al cambiar de via, y un evento sin ella vuelve a la del poste.
     */
    @Test
    void aDisconnectorThatSwitchesAnotherTrackThanThatOfItsPoleKeepsItsOwn() {
        MasterDataEventHandler dispatcher = dispatcher();
        long poleTrackId = System.nanoTime();
        long switchedTrackId = poleTrackId + 1;
        long movedTrackId = poleTrackId + 2;
        long profileId = poleTrackId + 7;
        String early = "d-own-early-" + poleTrackId;
        String late = "d-own-late-" + poleTrackId;

        // Llega antes que su perfil: ya tiene su via, y el paquete aun no.
        dispatcher.handle(withTrack(disconnectorMessage(early, profileId), switchedTrackId), new MasterDataEventContext(1L));
        entityManager.clear();
        assertEquals(switchedTrackId, asset(early).getTrackId());
        assertTrue(asset(early).isOwnTrack());
        assertEquals("" + profileId, asset(early).getProfileSourceId());
        assertNull(asset(early).getExecutionPackageId());

        // El perfil le pasa el paquete y no la via. Uno que llega despues, igual, y une la via del
        // poste con la que maniobra.
        dispatcher.handle(trackProfileMessage(profileId, poleTrackId, 6L, MasterDataOperation.CREATED), new MasterDataEventContext(1L));
        dispatcher.handle(parallel(withTrack(disconnectorMessage(late, profileId), switchedTrackId), poleTrackId),
                new MasterDataEventContext(1L));
        entityManager.clear();
        assertEquals(switchedTrackId, asset(early).getTrackId());
        assertEquals(6L, asset(early).getExecutionPackageId());
        assertEquals(switchedTrackId, asset(late).getTrackId());
        assertEquals(poleTrackId, asset(late).getConnectedTrackId());
        assertEquals(6L, asset(late).getExecutionPackageId());
        assertEquals(0, new BigDecimal("80196.63").compareTo(asset(late).getStartKp()), "Its kp is still that of its pole");

        // El perfil cambia de via y de paquete: el paquete le sigue, la via no.
        dispatcher.handle(trackProfileMessage(profileId, movedTrackId, 7L, MasterDataOperation.UPDATED), new MasterDataEventContext(2L));
        entityManager.clear();
        assertEquals(switchedTrackId, asset(early).getTrackId());
        assertEquals(7L, asset(early).getExecutionPackageId());
        assertEquals(switchedTrackId, asset(late).getTrackId());

        // Un evento sin via es uno que ya maniobra la de su poste: la toma del perfil.
        dispatcher.handle(disconnectorMessage(early, profileId), new MasterDataEventContext(3L));
        entityManager.clear();
        assertFalse(asset(early).isOwnTrack());
        assertEquals(movedTrackId, asset(early).getTrackId());
        assertEquals(7L, asset(early).getExecutionPackageId());
    }

    /**
     * Borrar la via que maniobra lo desactiva, como a todo lo que esta sobre ella; y borrar la de su
     * poste tambien, aunque su track_id sea la otra: sin esa via no queda poste del que colgar.
     */
    @Test
    void aDisconnectorThatSwitchesAnotherTrackGoesDownWithItAndWithTheTrackOfItsPole() {
        MasterDataEventHandler dispatcher = dispatcher();
        long poleTrackId = System.nanoTime();
        long switchedA = poleTrackId + 1;
        long switchedB = poleTrackId + 2;
        long profileId = poleTrackId + 7;
        String first = "d-own-a-" + poleTrackId;
        String second = "d-own-b-" + poleTrackId;

        dispatcher.handle(trackProfileMessage(profileId, poleTrackId, 6L, MasterDataOperation.CREATED), new MasterDataEventContext(1L));
        dispatcher.handle(withTrack(disconnectorMessage(first, profileId), switchedA), new MasterDataEventContext(1L));
        dispatcher.handle(withTrack(disconnectorMessage(second, profileId), switchedB), new MasterDataEventContext(1L));

        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.TRACK, String.valueOf(switchedA), MasterDataOperation.DELETED,
                Map.of("id", switchedA)), new MasterDataEventContext(2L));
        entityManager.clear();
        assertFalse(asset(first).getEnabled());
        assertTrue(asset(second).getEnabled());
        assertTrue(asset(String.valueOf(profileId)).getEnabled());

        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.TRACK, String.valueOf(poleTrackId), MasterDataOperation.DELETED,
                Map.of("id", poleTrackId)), new MasterDataEventContext(3L));
        entityManager.clear();
        assertFalse(asset(String.valueOf(profileId)).getEnabled());
        assertFalse(asset(second).getEnabled(), "Its own track is still there, but not the pole it hangs from");
        assertEquals(3L, asset(second).getSourceSequenceNumber(), "The watermark goes up to the deletion, as on its own track");
        assertEquals(switchedB, asset(second).getTrackId());
    }

    /**
     * Desde su V29 mto-configuration admite varios seccionadores en un poste (en pocos sitios, dos). Cada
     * uno es su activo, con su codigo, su kp del poste y su paquete del perfil: el que no trae via toma la
     * del poste y el que maniobra otra conserva la suya, al llegar y cuando el perfil cambia; y borrar la
     * via del poste baja a los dos con el.
     */
    @Test
    void twoDisconnectorsOnOnePoleAreTwoAssetsAndEachKeepsWhatIsItsOwn() {
        MasterDataEventHandler dispatcher = dispatcher();
        long poleTrackId = System.nanoTime();
        long switchedTrackId = poleTrackId + 1;
        long movedTrackId = poleTrackId + 2;
        long profileId = poleTrackId + 7;
        String plain = "d-two-plain-" + poleTrackId;
        String own = "d-two-own-" + poleTrackId;

        // Llegan despues de su perfil: los dos leen de el su paquete (inherit), y el de la via del poste, su via.
        dispatcher.handle(trackProfileMessage(profileId, poleTrackId, 6L, MasterDataOperation.CREATED), new MasterDataEventContext(1L));
        dispatcher.handle(withValue(disconnectorMessage(plain, profileId), "name", "NAT-NS3"), new MasterDataEventContext(1L));
        dispatcher.handle(withValue(withTrack(disconnectorMessage(own, profileId), switchedTrackId), "name", "NAT-NS4"),
                new MasterDataEventContext(1L));
        entityManager.clear();
        CatenaryAsset first = asset(plain);
        CatenaryAsset second = asset(own);
        assertNotEquals(first.getId(), second.getId());
        assertNotEquals(first.getCode(), second.getCode());
        assertEquals(String.valueOf(profileId), first.getProfileSourceId());
        assertEquals(String.valueOf(profileId), second.getProfileSourceId());
        assertEquals(0, first.getStartKp().compareTo(second.getStartKp()), "The kp of their pole, both");
        assertEquals(poleTrackId, first.getTrackId());
        assertFalse(first.isOwnTrack());
        assertEquals(switchedTrackId, second.getTrackId());
        assertTrue(second.isOwnTrack());
        assertEquals(6L, first.getExecutionPackageId());
        assertEquals(6L, second.getExecutionPackageId());

        // El perfil cambia de via y de paquete: los dos lo reciben (propagate), cada uno con lo suyo.
        dispatcher.handle(trackProfileMessage(profileId, movedTrackId, 7L, MasterDataOperation.UPDATED), new MasterDataEventContext(2L));
        entityManager.clear();
        assertEquals(movedTrackId, asset(plain).getTrackId());
        assertEquals(7L, asset(plain).getExecutionPackageId());
        assertEquals(switchedTrackId, asset(own).getTrackId());
        assertEquals(7L, asset(own).getExecutionPackageId());

        // Borrar la via del poste baja el poste y los dos (deactivateByTrack), tambien el que tiene otra via.
        dispatcher.handle(message(UUID.randomUUID(), MasterDataEntityNames.TRACK, String.valueOf(movedTrackId), MasterDataOperation.DELETED,
                Map.of("id", movedTrackId)), new MasterDataEventContext(3L));
        entityManager.clear();
        assertFalse(asset(String.valueOf(profileId)).getEnabled());
        assertFalse(asset(plain).getEnabled());
        assertFalse(asset(own).getEnabled());
        assertEquals(3L, asset(own).getSourceSequenceNumber());
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

        // Un punto de acceso a la via (V28 de mto-configuration) es de mto-resources: se registra y no toca nada.
        UUID accessId = UUID.randomUUID();
        MasterDataChangedMessage accessPoint = message(accessId, MasterDataEntityNames.TRACK_ACCESS_POINT, "3", MasterDataOperation.CREATED,
                Map.of("id", 3, "name", "PN-12", "track", Map.of("id", 12, "name", "TRACK 1", "executionPackageId", 6)));
        assertEquals(InboxProcessingResult.PROCESSED, inbox.process(command(accessId, MasterDataEntityNames.TRACK_ACCESS_POINT, "3"),
                () -> dispatcher.handle(accessPoint, new MasterDataEventContext(11L))));
        entityManager.flush();
        assertEquals(before, assetRepository.count());
        assertEquals(9, MasterDataEntityNames.PUBLISHED_TODAY.size(), "What mto-configuration publishes today");
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

    @Test
    void aTeamOfMtoResourcesAdoptsTheLocalTeamOfItsCodeWithItsCrewAndPackagesAndALateEventUndoesNothing() {
        String code = "T" + (System.nanoTime() % 1_000_000_000L);
        MaintenanceTeam local = MaintenanceTeam.builder().code(code).name("Team " + code).baseName("Rishpon").vehicle("Truck 3").build();
        entityManager.persist(local);
        entityManager.flush();
        UUID localId = local.getId();
        UUID teamId = UUID.randomUUID();
        Map<String, Object> leader = crewMember("TEAM_LEADER");
        Map<String, Object> supervisor = crewMember("SUPERVISOR");
        List<Map<String, Object>> linemen = List.of(crewMember("WORKER"), crewMember("WORKER"));
        IncomingEventHandler handler = new ResourcesTeamHandler(teamRepository, personRepository);

        handler.handle(teamEvent(teamId, code, "Team " + code + " (resources)", leader, supervisor, linemen, List.of(4, 5, 6)),
                resourcesContext(5L));
        handler.handle(teamEvent(teamId, code, "stale", leader, supervisor, linemen.subList(0, 1), List.of(9)), resourcesContext(4L));
        entityManager.flush();
        entityManager.clear();

        MaintenanceTeam adopted = teamRepository.findByCode(code).orElseThrow();
        assertEquals(localId, adopted.getId(), "The local team is adopted, not duplicated: its shifts and orders keep pointing at it");
        assertTrue(adopted.isSynchronized());
        assertEquals(teamId, adopted.getSourceEntityId());
        assertEquals(5L, adopted.getSourceSequenceNumber());
        assertEquals("Team " + code + " (resources)", adopted.getName());
        assertNull(adopted.getBaseName(), "A team of mto-resources has no fixed base: the vehicle parks wherever it can");
        assertEquals("RR-07", adopted.getVehicle());
        assertEquals(leader.get("personId"), adopted.getLeader().getId().toString());
        assertEquals("TEAM_LEADER", adopted.getLeader().getCategory());
        assertEquals(supervisor.get("personId"), adopted.getSupervisor().getId().toString());
        assertEquals(java.util.Set.of(linemen.get(0).get("personId"), linemen.get(1).get("personId")),
                adopted.getMembers().stream().map(member -> member.getId().toString()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(java.util.Set.of(4L, 5L, 6L), adopted.getExecutionPackageIds());

        handler.handle(teamEvent(teamId, code, "Team " + code, leader, supervisor, List.of(linemen.get(0), linemen.get(1), crewMember("WORKER")),
                List.of(4, 5)), resourcesContext(6L));
        entityManager.flush();
        entityManager.clear();
        MaintenanceTeam updated = teamRepository.findByCode(code).orElseThrow();
        assertEquals(3, updated.getMembers().size());
        assertEquals(java.util.Set.of(4L, 5L), updated.getExecutionPackageIds());
    }

    @Test
    void aConstructionTeamIsNotOfMaintenanceAndIsIgnored() {
        String code = "C" + (System.nanoTime() % 1_000_000_000L);
        Map<String, Object> values = new HashMap<>(teamEvent(UUID.randomUUID(), code, "Construction", crewMember("TEAM_LEADER"), null,
                List.of(crewMember("WORKER"), crewMember("WORKER")), List.of(4)).values());
        values.put("discipline", "CONSTRUCTION");

        new ResourcesTeamHandler(teamRepository, personRepository)
                .handle(new IncomingEvent("team", UUID.randomUUID().toString(), "created", values), resourcesContext(1L));

        assertTrue(teamRepository.findByCode(code).isEmpty());
    }

    @Test
    void aPersonEventOverwritesWhatItsTeamBroughtAndALateOneIsDiscarded() {
        Map<String, Object> lineman = crewMember("WORKER");
        UUID personId = UUID.fromString((String) lineman.get("personId"));
        new ResourcesTeamHandler(teamRepository, personRepository).handle(teamEvent(UUID.randomUUID(),
                "P" + (System.nanoTime() % 1_000_000_000L), "Team", crewMember("TEAM_LEADER"), null, List.of(lineman, crewMember("WORKER")),
                List.of()), resourcesContext(3L));
        IncomingEventHandler people = new ResourcesPersonHandler(personRepository);

        people.handle(personEvent(personId, (String) lineman.get("code"), "Eli Katz", "TEAM_LEADER", true), resourcesContext(2L));
        people.handle(personEvent(personId, (String) lineman.get("code"), "Eli Katz (old)", "WORKER", false), resourcesContext(1L));
        entityManager.flush();
        entityManager.clear();

        var person = personRepository.findById(personId).orElseThrow();
        assertEquals("Eli Katz", person.getFullName(), "The team brought it without a watermark, so its own event overwrites it");
        assertEquals("TEAM_LEADER", person.getCategory());
        assertTrue(person.getActive());
        assertEquals(2L, person.getSourceSequenceNumber());
    }

    @Test
    void aPersonKeepsItsQualificationsWithTheirExpiryAndAnEventWithoutThemKeepsTheStoredOnes() {
        UUID personId = UUID.randomUUID();
        String code = "PER-" + personId.toString().substring(0, 8);
        IncomingEventHandler people = new ResourcesPersonHandler(personRepository);
        Map<String, Object> licence = qualification("LIC-B", "DRIVING_LICENCE", "B", null);
        licence.put("name", "Driving licence B"); // una clave que aqui no se guarda
        Map<String, Object> event = personValues(code, "Eli Katz", "TEAM_LEADER", true);
        event.put("qualifications", List.of(licence, qualification("RAIL-DRIVER", "VEHICLE_OPERATION", null, "2027-03-31")));

        people.handle(new IncomingEvent("person", personId.toString(), "updated", event), resourcesContext(5L));
        entityManager.flush();
        entityManager.clear();
        CrewPerson stored = personRepository.findById(personId).orElseThrow();
        assertEquals(List.of(new CrewPerson.Qualification("LIC-B", "DRIVING_LICENCE", "B", null),
                new CrewPerson.Qualification("RAIL-DRIVER", "VEHICLE_OPERATION", null, LocalDate.of(2027, 3, 31))), stored.getQualifications());

        // Un mto-resources anterior no trae la clave: lo demas se aplica y las cualificaciones se quedan.
        people.handle(personEvent(personId, code, "Eli Katz (renamed)", "TEAM_LEADER", true), resourcesContext(6L));
        // Uno que llega tarde no las vacia.
        Map<String, Object> late = personValues(code, "Eli Katz (old)", "TEAM_LEADER", true);
        late.put("qualifications", List.of());
        people.handle(new IncomingEvent("person", personId.toString(), "updated", late), resourcesContext(4L));
        entityManager.flush();
        entityManager.clear();
        CrewPerson kept = personRepository.findById(personId).orElseThrow();
        assertEquals("Eli Katz (renamed)", kept.getFullName());
        assertEquals(2, kept.getQualifications().size(), "Without the key the stored qualifications stay");
        assertEquals(6L, kept.getSourceSequenceNumber());

        Map<String, Object> none = personValues(code, "Eli Katz (renamed)", "TEAM_LEADER", true);
        none.put("qualifications", List.of());
        people.handle(new IncomingEvent("person", personId.toString(), "updated", none), resourcesContext(7L));
        entityManager.flush();
        entityManager.clear();
        assertEquals(List.of(), personRepository.findById(personId).orElseThrow().getQualifications(), "An empty list empties them");

        Map<String, Object> garbled = personValues(code, "Eli Katz", "TEAM_LEADER", true);
        garbled.put("qualifications", List.of(qualification("RAIL-DRIVER", "VEHICLE_OPERATION", null, "31/03/2027")));
        assertThrows(ValidationException.class, () -> people.handle(new IncomingEvent("person", personId.toString(), "updated", garbled),
                resourcesContext(8L)), "A date that cannot be read is not taken for one that never expires");
    }

    @Test
    void aPersonThatArrivesInsideATeamHasNoQualificationsUntilItsOwnEvent() {
        Map<String, Object> lineman = crewMember("WORKER");
        new ResourcesTeamHandler(teamRepository, personRepository).handle(teamEvent(UUID.randomUUID(),
                "Q" + (System.nanoTime() % 1_000_000_000L), "Team", crewMember("TEAM_LEADER"), null, List.of(lineman, crewMember("WORKER")),
                List.of()), resourcesContext(3L));
        entityManager.flush();
        entityManager.clear();

        assertEquals(List.of(), personRepository.findById(UUID.fromString((String) lineman.get("personId"))).orElseThrow().getQualifications());
    }

    @Test
    void aVehicleKeepsItsDocumentsAndTheQualificationItsDriverNeedsAndAnEventWithoutThemKeepsThem() {
        UUID vehicleId = UUID.randomUUID();
        String code = "RR-" + vehicleId.toString().substring(0, 6);
        IncomingEventHandler vehicles = new ResourcesVehicleHandler(vehicleRepository);
        Map<String, Object> created = vehicleValues(code, "RR-OPERATOR");
        created.put("documents", List.of(document("INSURANCE", "POL-9", "2026-12-31"), document("ROAD_INSPECTION", "ITV-2026-118", "2027-02-28")));

        vehicles.handle(new IncomingEvent("vehicle", vehicleId.toString(), "created", created), resourcesContext(3L));
        entityManager.flush();
        entityManager.clear();
        FleetVehicle stored = vehicleRepository.findById(vehicleId).orElseThrow();
        assertEquals("RR-OPERATOR", stored.getRequiredDriverQualification());
        assertEquals(List.of(new FleetVehicle.Document("INSURANCE", "POL-9", LocalDate.of(2026, 12, 31)),
                new FleetVehicle.Document("ROAD_INSPECTION", "ITV-2026-118", LocalDate.of(2027, 2, 28))), stored.getDocuments());

        // Sin documents ni type.requiredDriverQualification (un mto-resources anterior): se quedan.
        Map<String, Object> older = vehicleValues(code, null);
        ((Map<?, ?>) older.get("type")).remove("requiredDriverQualification");
        older.put("name", "Renamed");
        vehicles.handle(new IncomingEvent("vehicle", vehicleId.toString(), "updated", older), resourcesContext(4L));
        // Uno que llega tarde no los vacia.
        Map<String, Object> late = vehicleValues(code, null);
        late.put("documents", List.of());
        vehicles.handle(new IncomingEvent("vehicle", vehicleId.toString(), "updated", late), resourcesContext(2L));
        entityManager.flush();
        entityManager.clear();
        FleetVehicle kept = vehicleRepository.findById(vehicleId).orElseThrow();
        assertEquals("Renamed", kept.getName());
        assertEquals("RR-OPERATOR", kept.getRequiredDriverQualification());
        assertEquals(2, kept.getDocuments().size());

        // Con la clave a null, su modelo ya no exige nada; con la lista vacia, no tiene documentos.
        Map<String, Object> cleared = vehicleValues(code, null);
        cleared.put("documents", List.of());
        vehicles.handle(new IncomingEvent("vehicle", vehicleId.toString(), "updated", cleared), resourcesContext(5L));
        entityManager.flush();
        entityManager.clear();
        FleetVehicle now = vehicleRepository.findById(vehicleId).orElseThrow();
        assertNull(now.getRequiredDriverQualification());
        assertEquals(List.of(), now.getDocuments());
    }

    @Test
    void aPersonAndAVehicleKeepWhenTheyAreNotAvailableAndTheCapabilitiesOfTheModelAndAnEventWithoutThemKeepsThem() {
        UUID personId = UUID.randomUUID();
        String code = "PER-" + personId.toString().substring(0, 8);
        IncomingEventHandler people = new ResourcesPersonHandler(personRepository);
        Map<String, Object> absent = personValues(code, "Eli Katz", "WORKER", true);
        absent.put("unavailable", List.of(Map.of("from", "2026-11-02", "to", "2026-11-06"), period("2026-12-24", null), Map.of("to", "2027-01-01")));

        people.handle(new IncomingEvent("person", personId.toString(), "updated", absent), resourcesContext(5L));
        people.handle(personEvent(personId, code, "Eli Katz (renamed)", "WORKER", true), resourcesContext(6L));
        entityManager.flush();
        entityManager.clear();
        assertEquals(List.of(new UnavailablePeriod(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6)),
                        new UnavailablePeriod(LocalDate.of(2026, 12, 24), null)),
                personRepository.findById(personId).orElseThrow().getUnavailable(),
                "Without its from a period is skipped, and an event without the key keeps the stored ones");

        UUID vehicleId = UUID.randomUUID();
        IncomingEventHandler vehicles = new ResourcesVehicleHandler(vehicleRepository);
        Map<String, Object> inRepair = vehicleValues("RR-" + vehicleId.toString().substring(0, 6), null);
        ((Map<String, Object>) inRepair.get("type")).put("capabilities", List.of("EARTHING", "INSULATED_PLATFORM", "TOWING"));
        inRepair.put("unavailable", List.of(period("2026-10-15", null)));
        vehicles.handle(new IncomingEvent("vehicle", vehicleId.toString(), "updated", inRepair), resourcesContext(3L));
        Map<String, Object> older = vehicleValues("RR-" + vehicleId.toString().substring(0, 6), null);
        ((Map<?, ?>) older.get("type")).remove("capabilities");
        vehicles.handle(new IncomingEvent("vehicle", vehicleId.toString(), "updated", older), resourcesContext(4L));
        entityManager.flush();
        entityManager.clear();
        FleetVehicle stored = vehicleRepository.findById(vehicleId).orElseThrow();
        assertEquals(List.of(new UnavailablePeriod(LocalDate.of(2026, 10, 15), null)), stored.getUnavailable());
        assertEquals(List.of("EARTHING", "INSULATED_PLATFORM", "TOWING"), stored.getCapabilities());
        assertTrue(stored.hasCapability("INSULATED_PLATFORM"));
    }

    @Test
    void theScopeOfAPersonIsThePackagesOfTheTeamsHeLeadsSupervisesOrRidesIn() {
        Map<String, Object> leader = crewMember("TEAM_LEADER");
        leader.put("username", "Scope.Leader." + UUID.randomUUID().toString().substring(0, 6));
        Map<String, Object> supervisor = crewMember("SUPERVISOR");
        Map<String, Object> chief = crewMember("CHIEF_SUPERVISOR");
        List<Map<String, Object>> linemen = List.of(crewMember("WORKER"), crewMember("WORKER"));
        IncomingEvent event = teamEvent(UUID.randomUUID(), "S" + (System.nanoTime() % 1_000_000_000L), "Team", leader, supervisor, linemen,
                List.of(14, 15));
        event.values().put("chiefSupervisor", chief);

        new ResourcesTeamHandler(teamRepository, personRepository).handle(event, resourcesContext(2L));
        entityManager.flush();
        entityManager.clear();

        for (Map<String, Object> person : List.of(leader, supervisor, chief, linemen.get(0))) {
            assertEquals(java.util.Set.of(14L, 15L),
                    java.util.Set.copyOf(teamRepository.findExecutionPackageIdsOfPerson(UUID.fromString((String) person.get("personId")))),
                    person.get("category") + " counts");
        }
        assertEquals(List.of(), teamRepository.findExecutionPackageIdsOfPerson(UUID.randomUUID()));
        assertEquals(1, teamRepository.findTeamIdsOfPerson(UUID.fromString((String) chief.get("personId"))).size());
        assertEquals(leader.get("personId"), personRepository.findByUsername(((String) leader.get("username")).toLowerCase()).orElseThrow()
                .getId().toString(), "The username is compared as Keycloak does, without case");
    }

    @Test
    void anInstrumentArrivesAsItsSnapshotAndALateEventIsDiscarded() {
        UUID instrumentId = UUID.randomUUID();
        String code = "EP-" + instrumentId.toString().substring(0, 6).toUpperCase();
        IncomingEventHandler instruments = new ResourcesInstrumentHandler(instrumentRepository);

        instruments.handle(instrumentEvent(instrumentId, "created", code, "2027-01-10", true), resourcesContext(10L));
        instruments.handle(instrumentEvent(instrumentId, "updated", code, "2026-01-10", false), resourcesContext(9L));
        entityManager.flush();
        entityManager.clear();
        MaintenanceInstrument stored = instrumentRepository.findById(instrumentId).orElseThrow();
        assertEquals(code, stored.getCode());
        assertEquals("Earthing pole " + code, stored.getName());
        assertEquals("EARTHING_POLE", stored.getKind());
        assertEquals("SN-" + code, stored.getSerialNumber());
        assertEquals(LocalDate.of(2027, 1, 10), stored.getCalibrationExpiresOn());
        assertTrue(stored.getActive(), "The late event is discarded");
        assertEquals(10L, stored.getSourceSequenceNumber());

        instruments.handle(instrumentEvent(instrumentId, "deactivated", code, null, false), resourcesContext(11L));
        entityManager.flush();
        entityManager.clear();
        MaintenanceInstrument retired = instrumentRepository.findById(instrumentId).orElseThrow();
        assertFalse(retired.getActive());
        assertNull(retired.getCalibrationExpiresOn(), "Without a calibration there is nothing that expires");
    }

    /** El mismo mensaje con {@code station} a null: un seccionador que no es de ninguna estacion. */
    private static MasterDataChangedMessage withoutStation(MasterDataChangedMessage message) {
        Map<String, Object> values = new HashMap<>(message.data().values());
        values.put("station", null);
        return new MasterDataChangedMessage(message.operationId(), message.referenceId(), message.origin(),
                message.creationDate(), message.eventType(),
                new MasterDataChangedEvent(message.data().entityName(), message.data().entityId(),
                        message.data().operation(), values), message.messageHash());
    }

    /** El mismo mensaje con la otra via que el seccionador pone en paralelo, como desde la V27. */
    private static MasterDataChangedMessage parallel(MasterDataChangedMessage message, long connectedTrackId) {
        Map<String, Object> values = new HashMap<>(message.data().values());
        values.put("connectedTrack", Map.of("id", connectedTrackId, "name", "TRACK 2"));
        return new MasterDataChangedMessage(message.operationId(), message.referenceId(), message.origin(),
                message.creationDate(), message.eventType(),
                new MasterDataChangedEvent(message.data().entityName(), message.data().entityId(),
                        message.data().operation(), values), message.messageHash());
    }

    /**
     * El mismo mensaje con su via aunque este en un poste: la que maniobra cuando no es la del poste,
     * como la publica mto-configuration desde que la admite con poste.
     */
    private static MasterDataChangedMessage withTrack(MasterDataChangedMessage message, long trackId) {
        Map<String, Object> values = new HashMap<>(message.data().values());
        values.put("track", Map.of("id", trackId, "name", "TRACK 3"));
        return new MasterDataChangedMessage(message.operationId(), message.referenceId(), message.origin(),
                message.creationDate(), message.eventType(),
                new MasterDataChangedEvent(message.data().entityName(), message.data().entityId(),
                        message.data().operation(), values), message.messageHash());
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

    private static IncomingEventContext resourcesContext(long sequenceNumber) {
        return new IncomingEventContext("mto-resources", sequenceNumber, UUID.randomUUID().toString());
    }

    /** Una persona tal como viaja dentro de un equipo de mto-resources. */
    private static Map<String, Object> crewMember(String category) {
        UUID id = UUID.randomUUID();
        Map<String, Object> person = new HashMap<>();
        person.put("personId", id.toString());
        person.put("code", "PER-" + id.toString().substring(0, 8));
        person.put("fullName", "Person " + id.toString().substring(0, 4));
        person.put("username", null);
        person.put("category", category);
        return person;
    }

    private static IncomingEvent teamEvent(UUID teamId, String code, String name, Map<String, Object> leader, Map<String, Object> supervisor,
                                           List<Map<String, Object>> members, List<Integer> executionPackageIds) {
        Map<String, Object> values = new HashMap<>();
        values.put("code", code);
        values.put("name", name);
        values.put("discipline", "MAINTENANCE");
        values.put("active", true);
        values.put("executionPackageIds", executionPackageIds);
        values.put("defaultVehicle", Map.of("id", UUID.randomUUID().toString(), "code", "RR-07"));
        values.put("leader", leader);
        values.put("supervisor", supervisor);
        values.put("members", members);
        return new IncomingEvent("team", teamId.toString(), "updated", values);
    }

    /** Un periodo no disponible como lo publica mto-resources en {@code unavailable}; {@code to} puede ser nulo. */
    private static Map<String, Object> period(String from, String to) {
        Map<String, Object> period = new HashMap<>();
        period.put("from", from);
        period.put("to", to);
        return period;
    }

    /** Una cualificacion como la publica mto-resources en {@code person.*.qualifications}. */
    private static Map<String, Object> qualification(String code, String kind, String drivingLicence, String expiresOn) {
        Map<String, Object> qualification = new HashMap<>();
        qualification.put("code", code);
        qualification.put("kind", kind);
        qualification.put("drivingLicence", drivingLicence);
        qualification.put("expiresOn", expiresOn);
        return qualification;
    }

    private static Map<String, Object> personValues(String code, String fullName, String category, boolean active) {
        return new HashMap<>(personEvent(UUID.randomUUID(), code, fullName, category, active).values());
    }

    /** Un documento como lo publica mto-resources en {@code vehicle.*.documents}. */
    private static Map<String, Object> document(String kind, String reference, String expiresOn) {
        Map<String, Object> document = new HashMap<>();
        document.put("kind", kind);
        document.put("reference", reference);
        document.put("expiresOn", expiresOn);
        return document;
    }

    /** Un vehiculo de un modelo bivial como lo publica mto-resources, con lo que su modelo exige al conductor. */
    private static Map<String, Object> vehicleValues(String code, String requiredDriverQualification) {
        Map<String, Object> type = new HashMap<>();
        type.put("code", "RR-9A");
        type.put("name", "Road-rail 9A");
        type.put("mobility", "ROAD_RAIL");
        type.put("category", "ROAD_RAIL_PLATFORM");
        type.put("selfPropelled", true);
        type.put("portable", false);
        type.put("seats", 5);
        type.put("capabilities", List.of("PLATFORM"));
        type.put("requiredDriverQualification", requiredDriverQualification);
        Map<String, Object> values = new HashMap<>();
        values.put("code", code);
        values.put("name", "Road-rail " + code);
        values.put("type", type);
        values.put("operationalStatus", "OPERATIONAL");
        values.put("available", true);
        values.put("active", true);
        return values;
    }

    /** Un instrumento con las claves de {@code instrument.*} de mto-resources. */
    private static IncomingEvent instrumentEvent(UUID instrumentId, String eventName, String code, String calibrationExpiresOn, boolean active) {
        Map<String, Object> values = new HashMap<>();
        values.put("code", code);
        values.put("name", "Earthing pole " + code);
        values.put("kind", "EARTHING_POLE");
        values.put("serialNumber", "SN-" + code);
        values.put("calibratedOn", calibrationExpiresOn == null ? null : "2026-01-10");
        values.put("calibrationExpiresOn", calibrationExpiresOn);
        values.put("active", active);
        values.put("version", 0);
        values.put("republished", false);
        return new IncomingEvent("instrument", instrumentId.toString(), eventName, values);
    }

    private static IncomingEvent personEvent(UUID personId, String code, String fullName, String category, boolean active) {
        Map<String, Object> values = new HashMap<>();
        values.put("code", code);
        values.put("fullName", fullName);
        values.put("username", null);
        values.put("category", category);
        values.put("active", active);
        return new IncomingEvent("person", personId.toString(), "updated", values);
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
