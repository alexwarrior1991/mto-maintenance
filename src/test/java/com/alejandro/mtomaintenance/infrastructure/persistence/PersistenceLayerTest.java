package com.alejandro.mtomaintenance.infrastructure.persistence;

import com.alejandro.mtomaintenance.configuration.JpaAuditingConfiguration;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.InspectionTemplateRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceOrderRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskTypeRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.specification.CatenaryAssetSpecification;
import com.alejandro.mtomaintenance.infrastructure.persistence.specification.MaintenanceOrderSpecification;
import com.alejandro.mtomaintenance.support.PostgreSQLTestContainer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Repositorios, specifications y restricciones contra un PostgreSQL real con las migraciones aplicadas. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(JpaAuditingConfiguration.class)
class PersistenceLayerTest extends PostgreSQLTestContainer {

    @DynamicPropertySource
    static void postgreSQLProperties(DynamicPropertyRegistry registry) {
        registerPostgreSQLProperties(registry);
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CatenaryAssetRepository assetRepository;

    @Autowired
    private MaintenanceOrderRepository orderRepository;

    @Autowired
    private MaintenanceTaskTypeRepository taskTypeRepository;

    @Autowired
    private InspectionTemplateRepository templateRepository;

    @Test
    void theCatalogueSeedsArePresentWithTheirExecutionWindows() {
        List<MaintenanceTaskType> types = taskTypeRepository.findAllByOrderByOrderIndexAsc();

        assertEquals(30, types.size());
        assertEquals("RG-01", types.getFirst().getCode());
        assertTrue(taskTypeRepository.findByCode("RG-03").orElseThrow().getRequiresFullPossession());
        assertTrue(taskTypeRepository.findByCode("RP-12").orElseThrow().getRequiresFullPossession());
        assertTrue(taskTypeRepository.findByCode("RP-10").orElseThrow().getDiagnostic());
        assertEquals(3, templateRepository.findAll().size());
        assertEquals(14, templateRepository.findFirstByAssetTypeAndActiveTrueOrderByVersionDesc(CatenaryAssetType.PROFILE).orElseThrow().getItems().size());
    }

    @Test
    void profilesOfATrackAreReturnedInKilometricOrderWithinTheSection() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long trackId = System.nanoTime();
        assetRepository.save(profile("13-2.02-" + suffix, trackId, "13060.290"));
        assetRepository.save(profile("12-2.27-" + suffix, trackId, "12847.990"));
        CatenaryAsset disabled = profile("13-2.01-" + suffix, trackId, "13007.290");
        disabled.setEnabled(false);
        assetRepository.save(disabled);
        assetRepository.save(profile("14-2.02-" + suffix, trackId, "14078.090"));
        entityManager.flush();

        List<CatenaryAsset> profiles = assetRepository.findEnabledByTypeOnTrackBetween(CatenaryAssetType.PROFILE, trackId,
                new BigDecimal("12800.000"), new BigDecimal("13500.000"));

        assertEquals(List.of("12-2.27-" + suffix, "13-2.02-" + suffix), profiles.stream().map(CatenaryAsset::getName).toList());
    }

    @Test
    void masterDataUpsertHonoursTheSequenceWatermarkAndDeactivationAdvancesIt() {
        String sourceId = "prf-" + UUID.randomUUID();

        assertEquals(1, upsert(sourceId, "12-2.27", 10L));
        assertEquals(1, upsert(sourceId, "12-2.27 renamed", 12L));
        assertEquals(0, upsert(sourceId, "12-2.27 stale", 11L));
        CatenaryAsset asset = assetRepository.findBySourceServiceAndSourceEntityId("mto-configuration", sourceId).orElseThrow();
        assertEquals("12-2.27 renamed", asset.getName());
        assertEquals(12L, asset.getSourceSequenceNumber());

        assertEquals(0, assetRepository.deactivateFromMasterData("mto-configuration", sourceId, 5L));
        assertEquals(1, assetRepository.deactivateFromMasterData("mto-configuration", sourceId, 13L));
        entityManager.clear();
        CatenaryAsset disabled = assetRepository.findBySourceServiceAndSourceEntityId("mto-configuration", sourceId).orElseThrow();
        assertEquals(false, disabled.getEnabled());
        assertEquals(13L, disabled.getSourceSequenceNumber());
        // Sin numero de secuencia se aplica y conserva la marca.
        assertEquals(1, upsert(sourceId, "12-2.27 again", null));
        entityManager.clear();
        assertEquals(13L, assetRepository.findBySourceServiceAndSourceEntityId("mto-configuration", sourceId).orElseThrow().getSourceSequenceNumber());
    }

    @Test
    void orderSearchCombinesStatusPriorityTrackDateRangeAndAssetType() {
        long trackId = System.nanoTime();
        CatenaryAsset section = assetRepository.save(section("SEC-" + UUID.randomUUID().toString().substring(0, 8), trackId));
        orderRepository.save(order("MO-A-" + trackId, section, MaintenanceOrderStatus.PLANNED, MaintenancePriority.HIGH, LocalDate.of(2026, 1, 20)));
        orderRepository.save(order("MO-B-" + trackId, section, MaintenanceOrderStatus.PLANNED, MaintenancePriority.LOW, LocalDate.of(2026, 1, 21)));
        orderRepository.save(order("MO-C-" + trackId, section, MaintenanceOrderStatus.DRAFT, MaintenancePriority.HIGH, LocalDate.of(2026, 1, 22)));
        orderRepository.save(order("MO-D-" + trackId, section, MaintenanceOrderStatus.PLANNED, MaintenancePriority.HIGH, LocalDate.of(2026, 3, 1)));
        entityManager.flush();

        Specification<MaintenanceOrder> specification = MaintenanceOrderSpecification.statusEquals(MaintenanceOrderStatus.PLANNED)
                .and(MaintenanceOrderSpecification.priorityEquals(MaintenancePriority.HIGH))
                .and(MaintenanceOrderSpecification.trackIdEquals(trackId))
                .and(MaintenanceOrderSpecification.plannedBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .and(MaintenanceOrderSpecification.assetTypeEquals(CatenaryAssetType.TRACK_SECTION));

        List<MaintenanceOrder> found = orderRepository.findAll(specification, PageRequest.of(0, 10, Sort.by("plannedDate"))).getContent();

        assertEquals(List.of("MO-A-" + trackId), found.stream().map(MaintenanceOrder::getCode).toList());
    }

    @Test
    void preventiveDueFilterUsesTheSqlFunctionAndTreatsNeverDoneAsDue() {
        long trackId = System.nanoTime();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CatenaryAsset neverDone = section("SEC-NEVER-" + suffix, trackId);
        neverDone.setPreventiveIntervalDays(30);
        CatenaryAsset recent = section("SEC-RECENT-" + suffix, trackId);
        recent.setPreventiveIntervalDays(30);
        recent.setLastPreventiveCompletedAt(Instant.now().minus(5, ChronoUnit.DAYS));
        CatenaryAsset overdue = section("SEC-OVERDUE-" + suffix, trackId);
        overdue.setPreventiveIntervalDays(30);
        overdue.setLastPreventiveCompletedAt(Instant.now().minus(60, ChronoUnit.DAYS));
        CatenaryAsset noInterval = section("SEC-NOINT-" + suffix, trackId);
        assetRepository.saveAll(List.of(neverDone, recent, overdue, noInterval));
        entityManager.flush();

        List<CatenaryAsset> due = assetRepository.findAll(CatenaryAssetSpecification.trackIdEquals(trackId)
                .and(CatenaryAssetSpecification.preventiveDueBefore(Instant.now())), Sort.by("code"));

        assertEquals(List.of("SEC-NEVER-" + suffix, "SEC-OVERDUE-" + suffix), due.stream().map(CatenaryAsset::getCode).toList());
    }

    @Test
    void aTrackSectionWithoutTrackKindIsRejectedByTheDatabase() {
        CatenaryAsset section = section("SEC-BAD-" + UUID.randomUUID().toString().substring(0, 8), 1L);
        section.setTrackKind(null);

        assertThrows(PersistenceException.class, () -> {
            assetRepository.save(section);
            entityManager.flush();
        });
    }

    private int upsert(String sourceId, String name, Long sequence) {
        int applied = assetRepository.upsertFromMasterData("mto-configuration", sourceId, "PRF-" + sourceId, name, "PROFILE",
                6L, 2L, null, new BigDecimal("12847.990"), new BigDecimal("12847.990"), null, "A/S", true, sequence);
        entityManager.clear();
        return applied;
    }

    private static CatenaryAsset profile(String name, long trackId, String kp) {
        return CatenaryAsset.builder().code("PRF-" + name).name(name).type(CatenaryAssetType.PROFILE).trackId(trackId).executionPackageId(6L)
                .startKp(new BigDecimal(kp)).endKp(new BigDecimal(kp)).build();
    }

    private static CatenaryAsset section(String code, long trackId) {
        return CatenaryAsset.builder().code(code).name(code).type(CatenaryAssetType.TRACK_SECTION).trackId(trackId).executionPackageId(6L)
                .startKp(new BigDecimal("12847.990")).endKp(new BigDecimal("14078.090")).trackKind(TrackKind.MAIN).build();
    }

    private static MaintenanceOrder order(String code, CatenaryAsset asset, MaintenanceOrderStatus status, MaintenancePriority priority, LocalDate planned) {
        MaintenanceOrder order = MaintenanceOrder.builder().code(code).title(code).type(MaintenanceOrderType.PREVENTIVE).status(status)
                .priority(priority).asset(asset).plannedDate(planned).build();
        order.locateAt(asset);
        return order;
    }
}
