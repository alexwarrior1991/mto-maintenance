package com.alejandro.mtomaintenance.infrastructure.persistence.audit;

import com.alejandro.mtomaintenance.configuration.JpaAuditingConfiguration;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspection;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CrewPerson;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FleetVehicle;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShiftVehicle;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CrewPersonRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.FleetVehicleRepository;
import com.alejandro.mtomaintenance.support.PostgreSQLTestContainer;
import org.hibernate.envers.RevisionType;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Historial de cambios de punta a punta. Desactiva la transaccion del test a proposito: Envers
 * escribe al completar la transaccion, no al hacer flush, y un slice test con rollback no registraria
 * nada y pareceria que Envers esta roto.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(JpaAuditingConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EnversAuditDataJpaTest extends PostgreSQLTestContainer {

    @DynamicPropertySource
    static void postgreSQLProperties(DynamicPropertyRegistry registry) {
        registerPostgreSQLProperties(registry);
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CatenaryAssetRepository assetRepository;

    @Autowired
    private CrewPersonRepository personRepository;

    @Autowired
    private FleetVehicleRepository vehicleRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updatingAnOrderRecordsARevisionWithTheAuthenticatedUsername() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("preferred_username", "alejandro").build(),
                AuthorityUtils.createAuthorityList("ROLE_MAINTENANCE_WRITE")));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID orderId = inTransaction(em -> {
            CatenaryAsset asset = CatenaryAsset.builder().code("SEC-AUD-" + suffix).name("Section").type(CatenaryAssetType.TRACK_SECTION)
                    .trackId(2L).startKp(new BigDecimal("100.000")).endKp(new BigDecimal("200.000")).trackKind(TrackKind.MAIN).build();
            em.persist(asset);
            MaintenanceOrder order = MaintenanceOrder.builder().code("MO-AUD-" + suffix).title("Before").type(MaintenanceOrderType.CORRECTIVE)
                    .priority(MaintenancePriority.LOW).asset(asset).build();
            em.persist(order);
            return order.getId();
        });

        inTransaction(em -> {
            MaintenanceOrder order = em.find(MaintenanceOrder.class, orderId);
            order.setTitle("After");
            order.setPriority(MaintenancePriority.HIGH);
            return null;
        });

        List<Number> revisions = reading(reader -> reader.getRevisions(MaintenanceOrder.class, orderId));
        assertEquals(2, revisions.size());
        assertEquals("Before", reading(reader -> reader.find(MaintenanceOrder.class, orderId, revisions.get(0))).getTitle());
        MaintenanceOrder current = reading(reader -> reader.find(MaintenanceOrder.class, orderId, revisions.get(1)));
        assertEquals("After", current.getTitle());
        assertEquals(MaintenancePriority.HIGH, current.getPriority());
        AuditRevision revision = reading(reader -> reader.findRevision(AuditRevision.class, revisions.get(1)));
        assertEquals("alejandro", revision.getUsername());
    }

    @Test
    void theStatusHistoryAndTheCataloguesHaveNoAuditTwinsWhileTheAuditedTablesDo() {
        List<String> tables = inTransaction(em -> em.createNativeQuery(
                "select table_name from information_schema.tables where table_schema = 'public' and table_name like '%_aud'", String.class).getResultList());

        assertTrue(tables.containsAll(List.of("catenary_asset_aud", "maintenance_order_aud", "maintenance_task_aud", "maintenance_shift_aud",
                "maintenance_inspection_aud", "catenary_defect_aud", "maintenance_material_usage_aud", "maintenance_task_task_type_aud",
                "maintenance_shift_track_aud", "maintenance_shift_disconnector_aud", "maintenance_shift_vehicle_aud",
                "maintenance_shift_lineman_aud", "maintenance_shift_instrument_aud")));
        assertFalse(tables.contains("crew_person_aud"), "A snapshot of mto-resources written only with native SQL");
        assertFalse(tables.contains("fleet_vehicle_aud"));
        assertFalse(tables.contains("maintenance_instrument_aud"));
        assertFalse(tables.contains("maintenance_status_history_aud"));
        assertFalse(tables.contains("maintenance_task_type_aud"));
        assertFalse(tables.contains("inbox_message_aud"));
    }

    @Test
    void theCrewAndTheVehiclesOfAShiftAreInItsHistoryAndWhereAVehicleWasLeftHasItsOwnRevision() {
        // La dotacion y el vehiculo llegan de mto-resources por SQL nativo, sin revision: se leen como estan.
        UUID leaderId = UUID.randomUUID();
        UUID linemanA = UUID.randomUUID();
        UUID linemanB = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        inTransaction(em -> {
            personRepository.upsertFromResources(leaderId, "PER-" + leaderId.toString().substring(0, 8), "Yossi Cohen", "equipo.jefe", "TEAM_LEADER", true, null, 1L);
            personRepository.upsertFromResources(linemanA, "PER-" + linemanA.toString().substring(0, 8), "Eli Katz", null, "WORKER", true, null, 1L);
            personRepository.upsertFromResources(linemanB, "PER-" + linemanB.toString().substring(0, 8), "Noam Biton", null, "WORKER", true, null, 1L);
            vehicleRepository.upsertFromResources(vehicleId, "DR-" + vehicleId.toString().substring(0, 6), "Tower car", "MTW", "Plasser MTW",
                    "RAIL_ONLY", "CATENARY_TOWER_CAR", true, false, true, 8, "OPERATIONAL", true, true, null, null, false, 1L);
            return null;
        });
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID shiftId = inTransaction(em -> {
            MaintenanceShift shift = MaintenanceShift.builder().code("SH-AUD-" + suffix).shiftDate(LocalDate.of(2026, 10, 16))
                    .possessionType(PossessionType.FULL).trackIds(new java.util.LinkedHashSet<>(List.of(2L))).build();
            shift.setTeamLeader(em.find(CrewPerson.class, leaderId));
            shift.getLinemen().add(em.find(CrewPerson.class, linemanA));
            shift.getLinemen().add(em.find(CrewPerson.class, linemanB));
            shift.getVehicles().add(MaintenanceShiftVehicle.of(shift, em.find(FleetVehicle.class, vehicleId)));
            em.persist(shift);
            return shift.getId();
        });
        UUID shiftVehicleId = inTransaction(em -> {
            MaintenanceShiftVehicle vehicle = em.find(MaintenanceShift.class, shiftId).getVehicles().getFirst();
            vehicle.setParkingTrackId(31L);
            vehicle.setParkingKp(new BigDecimal("4.250"));
            return vehicle.getId();
        });

        List<Number> shiftRevisions = reading(reader -> reader.getRevisions(MaintenanceShift.class, shiftId));
        assertEquals(1, shiftRevisions.size(), "Parking a vehicle changes the vehicle line, not the shift");
        List<Object> crewAtFirst = reading(reader -> {
            MaintenanceShift first = reader.find(MaintenanceShift.class, shiftId, shiftRevisions.getFirst());
            return List.of(first.getTeamLeader().getFullName(), first.getLinemen().size(), first.getVehicles().size());
        });
        assertEquals(List.of("Yossi Cohen", 2, 1), crewAtFirst);
        List<Number> vehicleRevisions = reading(reader -> reader.getRevisions(MaintenanceShiftVehicle.class, shiftVehicleId));
        assertEquals(2, vehicleRevisions.size());
        assertNull(reading(reader -> reader.find(MaintenanceShiftVehicle.class, shiftVehicleId, vehicleRevisions.getFirst()).getParkingTrackId()));
        MaintenanceShiftVehicle parked = reading(reader -> {
            MaintenanceShiftVehicle line = reader.find(MaintenanceShiftVehicle.class, shiftVehicleId, vehicleRevisions.getLast());
            line.getVehicle().getCode();
            return line;
        });
        assertEquals(31L, parked.getParkingTrackId());
        assertEquals("DR-" + vehicleId.toString().substring(0, 6), parked.getVehicle().getCode(), "The vehicle is read as it is now");
    }

    private <T> T inTransaction(Function<EntityManager, T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.apply(entityManager));
    }

    private <T> T reading(Function<AuditReader, T> work) {
        return inTransaction(em -> work.apply(AuditReaderFactory.get(em)));
    }

    @Test
    void removingAMaterialLineLeavesADeletedRevisionWithItsLastState() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID[] ids = inTransaction(em -> {
            CatenaryAsset asset = CatenaryAsset.builder().code("SEC-MAT-" + suffix).name("Section").type(CatenaryAssetType.TRACK_SECTION)
                    .trackId(2L).startKp(new BigDecimal("100.000")).endKp(new BigDecimal("200.000")).trackKind(TrackKind.MAIN).build();
            em.persist(asset);
            MaintenanceOrder order = MaintenanceOrder.builder().code("MO-MAT-" + suffix).title("Materials").type(MaintenanceOrderType.CORRECTIVE)
                    .priority(MaintenancePriority.LOW).asset(asset).build();
            em.persist(order);
            MaintenanceMaterialUsage line = MaintenanceMaterialUsage.builder().order(order).materialId(UUID.randomUUID()).materialCode("GA70")
                    .warehouseId(UUID.randomUUID()).plannedQuantity(new BigDecimal("2")).unit("ud").build();
            order.getMaterials().add(line);
            em.persist(line);
            return new UUID[] {order.getId(), line.getId()};
        });

        // Lo mismo que hace el servicio al quitar la linea.
        inTransaction(em -> {
            MaintenanceOrder order = em.find(MaintenanceOrder.class, ids[0]);
            MaintenanceMaterialUsage line = em.find(MaintenanceMaterialUsage.class, ids[1]);
            order.getMaterials().remove(line);
            em.remove(line);
            return null;
        });

        assertNull(inTransaction(em -> em.find(MaintenanceMaterialUsage.class, ids[1])));
        List<Object[]> rows = reading(reader -> {
            @SuppressWarnings("unchecked")
            List<Object[]> result = reader.createQuery().forRevisionsOfEntity(MaintenanceMaterialUsage.class, false, true)
                    .add(org.hibernate.envers.query.AuditEntity.id().eq(ids[1]))
                    .addOrder(org.hibernate.envers.query.AuditEntity.revisionNumber().asc()).getResultList();
            return result;
        });
        assertEquals(2, rows.size());
        assertEquals(RevisionType.ADD, rows.get(0)[2]);
        assertEquals(RevisionType.DEL, rows.get(1)[2]);
        MaintenanceMaterialUsage lastState = (MaintenanceMaterialUsage) rows.get(1)[0];
        assertEquals("GA70", lastState.getMaterialCode(), "store_data_at_delete keeps what the line was when it was removed");
        assertEquals(0, new BigDecimal("2").compareTo(lastState.getPlannedQuantity()));
    }

    @Test
    void theHistoryOfWhatHangsFromAMasterDataAssetReadsTheAssetAsItIsNow() {
        // Un activo que solo ha llegado por datos maestros no tiene revisiones (es SQL nativo), asi que
        // el historial de la orden, la tarea, la inspeccion o el defecto que cuelgan de el no puede ir a
        // buscarlo a catenary_asset_aud: ahi no esta, y leer su codigo era un 500.
        String sourceId = "prf-" + UUID.randomUUID();
        inTransaction(em -> assetRepository.upsertFromMasterData("mto-configuration", sourceId, "PRF-" + sourceId, "12-2.27", "PROFILE",
                6L, 2L, false, null, null, new BigDecimal("12847.990"), new BigDecimal("12847.990"), null, "A/S", null, null, null, true, 10L));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID[] ids = inTransaction(em -> {
            CatenaryAsset asset = assetRepository.findBySourceServiceAndSourceEntityId("mto-configuration", sourceId).orElseThrow();
            MaintenanceOrder order = MaintenanceOrder.builder().code("MO-MD-" + suffix).title("On a profile").type(MaintenanceOrderType.CORRECTIVE)
                    .priority(MaintenancePriority.LOW).asset(asset).build();
            em.persist(order);
            MaintenanceTask task = MaintenanceTask.builder().order(order).sequence(1).description("Dropper check").asset(asset).build();
            order.getTasks().add(task);
            em.persist(task);
            MaintenanceInspection inspection = MaintenanceInspection.builder().code("INS-MD-" + suffix).asset(asset)
                    .inspectionDate(LocalDate.of(2026, 9, 1)).result(InspectionResult.MINOR_DEFECT).build();
            em.persist(inspection);
            CatenaryDefect defect = CatenaryDefect.builder().code("DEF-MD-" + suffix).asset(asset).severity(DefectSeverity.LOW)
                    .description("Worn dropper").detectedAt(Instant.parse("2026-09-01T00:00:00Z")).build();
            em.persist(defect);
            return new UUID[] {order.getId(), task.getId(), inspection.getId(), defect.getId()};
        });
        assertTrue(reading(reader -> reader.getRevisions(CatenaryAsset.class, inTransaction(em ->
                assetRepository.findBySourceServiceAndSourceEntityId("mto-configuration", sourceId).orElseThrow().getId()))).isEmpty());

        // Y el activo que se lee es el de ahora: el evento siguiente lo renombra.
        inTransaction(em -> assetRepository.upsertFromMasterData("mto-configuration", sourceId, "PRF-" + sourceId, "12-2.27 renamed", "PROFILE",
                6L, 2L, false, null, null, new BigDecimal("12847.990"), new BigDecimal("12847.990"), null, "A/S", null, null, null, true, 11L));

        assertEquals("12-2.27 renamed", assetNameInFirstRevision(MaintenanceOrder.class, ids[0], MaintenanceOrder::getAsset));
        assertEquals("12-2.27 renamed", assetNameInFirstRevision(MaintenanceTask.class, ids[1], MaintenanceTask::getAsset));
        assertEquals("12-2.27 renamed", assetNameInFirstRevision(MaintenanceInspection.class, ids[2], MaintenanceInspection::getAsset));
        assertEquals("12-2.27 renamed", assetNameInFirstRevision(CatenaryDefect.class, ids[3], CatenaryDefect::getAsset));
        String orderCode = reading(reader -> {
            MaintenanceOrder order = reader.find(MaintenanceOrder.class, ids[0], reader.getRevisions(MaintenanceOrder.class, ids[0]).getFirst());
            return order.getTasks().getFirst().getAsset().getCode();
        });
        assertEquals("PRF-" + sourceId, orderCode, "The tasks of an order revision read their asset the same way");
    }

    private <E> String assetNameInFirstRevision(Class<E> entityType, UUID id, Function<E, CatenaryAsset> asset) {
        return reading(reader -> {
            Number first = reader.getRevisions(entityType, id).getFirst();
            return asset.apply(reader.find(entityType, id, first)).getName();
        });
    }

    @Test
    void masterDataUpsertsLeaveNoRevisionWhileAnApiEditOfTheSameAssetDoes() {
        String sourceId = "prf-" + UUID.randomUUID();
        inTransaction(em -> assetRepository.upsertFromMasterData("mto-configuration", sourceId, "PRF-" + sourceId, "12-2.27", "PROFILE",
                6L, 2L, false, null, null, new BigDecimal("12847.990"), new BigDecimal("12847.990"), null, "A/S", null, null, null, true, 10L));
        UUID assetId = inTransaction(em -> assetRepository.findBySourceServiceAndSourceEntityId("mto-configuration", sourceId).orElseThrow().getId());

        assertTrue(reading(reader -> reader.getRevisions(CatenaryAsset.class, assetId)).isEmpty(), "Native SQL bypasses Envers on purpose");

        inTransaction(em -> assetRepository.upsertFromMasterData("mto-configuration", sourceId, "PRF-" + sourceId, "12-2.27 renamed", "PROFILE",
                6L, 2L, false, null, null, new BigDecimal("12847.990"), new BigDecimal("12847.990"), null, "A/S", null, null, null, true, 11L));
        assertTrue(reading(reader -> reader.getRevisions(CatenaryAsset.class, assetId)).isEmpty());

        inTransaction(em -> {
            CatenaryAsset asset = em.find(CatenaryAsset.class, assetId);
            asset.setDescription("Edited through the API");
            asset.setPreventiveIntervalDays(180);
            return null;
        });

        List<Number> revisions = reading(reader -> reader.getRevisions(CatenaryAsset.class, assetId));
        assertEquals(1, revisions.size());
        CatenaryAsset audited = reading(reader -> reader.find(CatenaryAsset.class, assetId, revisions.getFirst()));
        assertEquals("Edited through the API", audited.getDescription());
        assertEquals("12-2.27 renamed", audited.getName(), "The revision snapshots the row as the event left it");
        List<Object[]> rows = reading(reader -> {
            @SuppressWarnings("unchecked")
            List<Object[]> result = reader.createQuery().forRevisionsOfEntity(CatenaryAsset.class, false, true)
                    .add(org.hibernate.envers.query.AuditEntity.id().eq(assetId)).getResultList();
            return result;
        });
        assertEquals(RevisionType.MOD, rows.getFirst()[2], "Without an ADD revision the first API edit is recorded as a modification");
        assertEquals("system", ((AuditRevision) rows.getFirst()[1]).getUsername(), "No authenticated user: the system is the author");

        // Desactivarlo aqui es una escritura de la API: deja revision, y el siguiente evento no la deshace.
        inTransaction(em -> {
            em.find(CatenaryAsset.class, assetId).disableLocally();
            return null;
        });
        inTransaction(em -> assetRepository.upsertFromMasterData("mto-configuration", sourceId, "PRF-" + sourceId, "12-2.27 renamed", "PROFILE",
                6L, 2L, false, null, null, new BigDecimal("12847.990"), new BigDecimal("12847.990"), null, "A/S", null, null, null, true, 12L));
        List<Number> afterDisable = reading(reader -> reader.getRevisions(CatenaryAsset.class, assetId));
        assertEquals(2, afterDisable.size());
        CatenaryAsset disabled = reading(reader -> reader.find(CatenaryAsset.class, assetId, afterDisable.getLast()));
        assertTrue(disabled.getDisabledLocally());
        assertFalse(disabled.getEnabled());
        Boolean enabledNow = inTransaction(em -> em.find(CatenaryAsset.class, assetId).getEnabled());
        assertFalse(enabledNow, "The event after it does not undo the local decision");
    }
}
