package com.alejandro.mtomaintenance.infrastructure.persistence;

import com.alejandro.mtomaintenance.configuration.JpaAuditingConfiguration;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.InspectionTemplateRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceOrderRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceShiftRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskTypeRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.specification.CatenaryAssetSpecification;
import com.alejandro.mtomaintenance.infrastructure.persistence.specification.MaintenanceOrderSpecification;
import com.alejandro.mtomaintenance.infrastructure.persistence.specification.MaintenanceShiftSpecification;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspection;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspectionItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceStatusHistory;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTeam;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryDefectRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceInspectionRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceMaterialUsageRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceReportRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceStatusHistoryRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTeamRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.specification.CatenaryDefectSpecification;
import com.alejandro.mtomaintenance.infrastructure.persistence.specification.MaintenanceInspectionSpecification;
import java.util.EnumSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Repositorios, specifications y restricciones contra un PostgreSQL real con las migraciones aplicadas. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({JpaAuditingConfiguration.class, MaintenanceReportRepository.class})
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

    @Autowired
    private MaintenanceShiftRepository shiftRepository;

    @Autowired
    private CatenaryDefectRepository defectRepository;

    @Autowired
    private MaintenanceInspectionRepository inspectionRepository;

    @Autowired
    private MaintenanceTaskRepository taskRepository;

    @Autowired
    private MaintenanceMaterialUsageRepository materialRepository;

    @Autowired
    private MaintenanceStatusHistoryRepository historyRepository;

    @Autowired
    private MaintenanceTeamRepository teamRepository;

    @Autowired
    private MaintenanceReportRepository reportRepository;

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

    @Test
    void aShiftIsFoundByAnyOfTheTracksItCoversAndOnlyOnce() {
        long trackA = System.nanoTime();
        long trackB = trackA + 1;
        long trackC = trackA + 2;
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        shiftRepository.save(MaintenanceShift.builder().code("SH-AB-" + suffix).shiftDate(LocalDate.of(2026, 1, 27)).possessionType(PossessionType.FULL)
                .trackIds(new LinkedHashSet<>(List.of(trackA, trackB))).build());
        shiftRepository.save(MaintenanceShift.builder().code("SH-C-" + suffix).shiftDate(LocalDate.of(2026, 1, 27)).possessionType(PossessionType.PARTIAL)
                .trackIds(new LinkedHashSet<>(List.of(trackC))).build());
        entityManager.flush();
        entityManager.clear();

        List<MaintenanceShift> onB = shiftRepository.findAll(MaintenanceShiftSpecification.worksOnTrack(trackB));
        List<MaintenanceShift> onC = shiftRepository.findAll(MaintenanceShiftSpecification.worksOnTrack(trackC));
        List<MaintenanceShift> both = shiftRepository.findAll(MaintenanceShiftSpecification.worksOnTrack(trackA).or(MaintenanceShiftSpecification.worksOnTrack(trackB)));

        assertEquals(List.of("SH-AB-" + suffix), onB.stream().map(MaintenanceShift::getCode).toList());
        assertEquals(List.of("SH-C-" + suffix), onC.stream().map(MaintenanceShift::getCode).toList());
        assertEquals(1, both.size(), "Un turno con dos vias no sale dos veces");
        assertEquals(List.of(trackA, trackB), onB.getFirst().getTrackIds().stream().sorted().toList());
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

    @Test
    void theCodeSequencesExistAndAdvanceOneByOne() {
        for (String sequence : List.of("maintenance_order_code_seq", "maintenance_shift_code_seq", "maintenance_inspection_code_seq", "catenary_defect_code_seq")) {
            long first = ((Number) entityManager.createNativeQuery("select nextval('" + sequence + "')").getSingleResult()).longValue();
            long second = ((Number) entityManager.createNativeQuery("select nextval('" + sequence + "')").getSingleResult()).longValue();

            assertTrue(first > 0, sequence);
            assertEquals(first + 1, second, sequence);
            assertEquals(6, String.format("%06d", first).length(), "The codes are zero-padded to six digits");
        }
    }

    @Test
    void theSeededTeamsArePresentAndATeamKeepsItsExecutionPackages() {
        assertEquals("Rishpon", teamRepository.findByCode("A").orElseThrow().getBaseName());
        assertTrue(teamRepository.existsByCode("B"));
        MaintenanceTeam team = teamRepository.save(MaintenanceTeam.builder().code("T" + UUID.randomUUID().toString().substring(0, 6)).name("Night crew")
                .executionPackageIds(new LinkedHashSet<>(List.of(6L, 7L))).build());
        entityManager.flush();
        entityManager.clear();

        MaintenanceTeam reloaded = teamRepository.findById(team.getId()).orElseThrow();

        assertEquals(Set.of(6L, 7L), reloaded.getExecutionPackageIds());
        assertTrue(reloaded.getActive());
        assertEquals("system", reloaded.getCreatedBy(), "Outside a request the auditor is the system");
    }

    @Test
    void aStatusHistoryRowBelongsToExactlyOneOrderOrDefect() {
        long trackId = System.nanoTime();
        CatenaryAsset section = assetRepository.save(section("SEC-HIS-" + UUID.randomUUID().toString().substring(0, 8), trackId));
        MaintenanceOrder order = orderRepository.save(order("MO-HIS-" + trackId, section, MaintenanceOrderStatus.DRAFT, MaintenancePriority.LOW, null));
        CatenaryDefect defect = defectRepository.save(defect("DEF-HIS-" + trackId, section, DefectSeverity.LOW, DefectStatus.OPEN, Instant.now()));
        historyRepository.save(MaintenanceStatusHistory.builder().order(order).newStatus("DRAFT").changedBy("alejandro").build());
        historyRepository.save(MaintenanceStatusHistory.builder().defect(defect).previousStatus(null).newStatus("OPEN").changedBy("alejandro").build());
        entityManager.flush();

        assertEquals(1, historyRepository.findByOrderIdOrderByChangedAtAsc(order.getId()).size());
        assertEquals(1, historyRepository.findByDefectIdOrderByChangedAtAsc(defect.getId()).size());
        assertThrows(PersistenceException.class, () -> {
            historyRepository.save(MaintenanceStatusHistory.builder().order(order).defect(defect).newStatus("OPEN").changedBy("alejandro").build());
            entityManager.flush();
        }, "A row owned by both an order and a defect violates the CHECK");
    }

    @Test
    void defectSearchCombinesSeverityStatusAssetTrackAndDetectionWindow() {
        long trackId = System.nanoTime();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CatenaryAsset asset = assetRepository.save(profile("13-2.10-" + suffix, trackId, "13499.290"));
        CatenaryAsset other = assetRepository.save(profile("13-2.11-" + suffix, trackId, "13550.000"));
        defectRepository.save(defect("DEF-A-" + suffix, asset, DefectSeverity.HIGH, DefectStatus.OPEN, Instant.parse("2026-01-10T00:00:00Z")));
        defectRepository.save(defect("DEF-B-" + suffix, asset, DefectSeverity.HIGH, DefectStatus.RESOLVED, Instant.parse("2026-01-12T00:00:00Z")));
        defectRepository.save(defect("DEF-C-" + suffix, asset, DefectSeverity.LOW, DefectStatus.OPEN, Instant.parse("2026-01-20T00:00:00Z")));
        defectRepository.save(defect("DEF-D-" + suffix, asset, DefectSeverity.HIGH, DefectStatus.OPEN, Instant.parse("2026-02-05T00:00:00Z")));
        defectRepository.save(defect("DEF-E-" + suffix, other, DefectSeverity.HIGH, DefectStatus.OPEN, Instant.parse("2026-01-11T00:00:00Z")));
        entityManager.flush();

        List<CatenaryDefect> found = defectRepository.findAll(CatenaryDefectSpecification.severityEquals(DefectSeverity.HIGH)
                .and(CatenaryDefectSpecification.statusEquals(DefectStatus.OPEN))
                .and(CatenaryDefectSpecification.assetIdEquals(asset.getId()))
                .and(CatenaryDefectSpecification.trackIdEquals(trackId))
                .and(CatenaryDefectSpecification.detectedBetween(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-31T23:59:59Z"))));
        List<CatenaryDefect> onTrackInJanuary = defectRepository.findAll(CatenaryDefectSpecification.trackIdEquals(trackId)
                .and(CatenaryDefectSpecification.detectedBetween(null, Instant.parse("2026-01-31T23:59:59Z"))), Sort.by("code"));

        assertEquals(List.of("DEF-A-" + suffix), found.stream().map(CatenaryDefect::getCode).toList());
        assertEquals(List.of("DEF-A-" + suffix, "DEF-B-" + suffix, "DEF-C-" + suffix, "DEF-E-" + suffix), onTrackInJanuary.stream().map(CatenaryDefect::getCode).toList());
        assertEquals(1, defectRepository.findByCode("DEF-B-" + suffix).stream().count());
    }

    @Test
    void anInspectionCopiesTheSeededChecklistAndIsSearchedByAssetTypeInspectorAndDate() {
        long trackId = System.nanoTime();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CatenaryAsset profile = assetRepository.save(profile("13-2.10-" + suffix, trackId, "13499.290"));
        CatenaryAsset disconnector = assetRepository.save(CatenaryAsset.builder().code("DSC-" + suffix).name("HSA-NS5").type(CatenaryAssetType.DISCONNECTOR)
                .trackId(trackId).build());
        InspectionTemplate template = templateRepository.findFirstByAssetTypeAndActiveTrueOrderByVersionDesc(CatenaryAssetType.PROFILE).orElseThrow();
        MaintenanceInspection withChecklist = inspection("INS-A-" + suffix, profile, LocalDate.of(2026, 1, 10), "Yossi", InspectionResult.OK);
        withChecklist.setTemplate(template);
        template.getItems().forEach(item -> withChecklist.getItems().add(MaintenanceInspectionItem.fromTemplate(withChecklist, item)));
        inspectionRepository.save(withChecklist);
        inspectionRepository.save(inspection("INS-B-" + suffix, disconnector, LocalDate.of(2026, 1, 11), "yossi", InspectionResult.MINOR_DEFECT));
        inspectionRepository.save(inspection("INS-C-" + suffix, profile, LocalDate.of(2026, 2, 1), "Yossi", InspectionResult.MAJOR_DEFECT));
        entityManager.flush();
        entityManager.clear();

        MaintenanceInspection reloaded = inspectionRepository.findById(withChecklist.getId()).orElseThrow();
        List<MaintenanceInspection> profilesInJanuary = inspectionRepository.findAll(MaintenanceInspectionSpecification.assetTypeEquals(CatenaryAssetType.PROFILE)
                .and(MaintenanceInspectionSpecification.inspectorEquals("YOSSI"))
                .and(MaintenanceInspectionSpecification.dateBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))));
        List<MaintenanceInspection> onTrack = inspectionRepository.findAll(MaintenanceInspectionSpecification.trackIdEquals(trackId), Sort.by("inspectionDate"));

        assertEquals(14, reloaded.getItems().size());
        assertEquals("CW_HEIGHT", reloaded.getItems().getFirst().getCode(), "Items come back in template order");
        assertEquals(template.getId(), reloaded.getTemplate().getId());
        assertEquals(List.of("INS-A-" + suffix), profilesInJanuary.stream().map(MaintenanceInspection::getCode).toList());
        assertEquals(List.of("INS-A-" + suffix, "INS-B-" + suffix, "INS-C-" + suffix), onTrack.stream().map(MaintenanceInspection::getCode).toList());
        assertEquals(1, inspectionRepository.findAll(MaintenanceInspectionSpecification.resultEquals(InspectionResult.MINOR_DEFECT)
                .and(MaintenanceInspectionSpecification.assetIdEquals(disconnector.getId()))).size());
    }

    @Test
    void taskSequencesAreUniquePerOrderAndTheProfilesAlreadyCoveredAreListed() {
        long trackId = System.nanoTime();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CatenaryAsset section = assetRepository.save(section("SEC-TSK-" + suffix, trackId));
        CatenaryAsset first = assetRepository.save(profile("12-2.27-" + suffix, trackId, "12847.990"));
        CatenaryAsset second = assetRepository.save(profile("12-2.28-" + suffix, trackId, "12899.290"));
        MaintenanceOrder order = orderRepository.save(order("MO-TSK-" + suffix, section, MaintenanceOrderStatus.PLANNED, MaintenancePriority.MEDIUM, null));
        taskRepository.save(MaintenanceTask.builder().order(order).sequence(1).description("First").asset(first).build());
        taskRepository.save(MaintenanceTask.builder().order(order).sequence(2).description("Second").asset(second).build());
        taskRepository.save(MaintenanceTask.builder().order(order).sequence(3).description("No asset").build());
        entityManager.flush();

        assertEquals(3, taskRepository.findMaxSequence(order.getId()));
        assertEquals(0, taskRepository.findMaxSequence(UUID.randomUUID()), "An order without tasks starts from zero");
        assertEquals(Set.of(first.getId(), second.getId()), Set.copyOf(taskRepository.findAssetIdsByOrderId(order.getId())));
        assertEquals(List.of(1, 2, 3), taskRepository.findByOrderIdOrderBySequenceAsc(order.getId()).stream().map(MaintenanceTask::getSequence).toList());
        assertTrue(taskRepository.existsByOrderIdAndStatusIn(order.getId(), EnumSet.of(MaintenanceTaskStatus.PENDING)));
        assertEquals(0, taskRepository.countByOrderIdAndStatus(order.getId(), MaintenanceTaskStatus.COMPLETED));
        assertThrows(PersistenceException.class, () -> {
            taskRepository.save(MaintenanceTask.builder().order(order).sequence(2).description("Duplicate").build());
            entityManager.flush();
        });
    }

    @Test
    void theShiftReportQueriesFindDefectsAndMaterialsByTaskAndDefectsResolvedInTheShift() {
        long trackId = System.nanoTime();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CatenaryAsset section = assetRepository.save(section("SEC-RPT-" + suffix, trackId));
        MaintenanceOrder order = orderRepository.save(order("MO-RPT-" + suffix, section, MaintenanceOrderStatus.IN_PROGRESS, MaintenancePriority.MEDIUM, null));
        MaintenanceShift shift = shiftRepository.save(MaintenanceShift.builder().code("SH-RPT-" + suffix).shiftDate(LocalDate.of(2026, 1, 27))
                .possessionType(PossessionType.PARTIAL).status(ShiftStatus.IN_PROGRESS).trackIds(new LinkedHashSet<>(List.of(trackId))).build());
        MaintenanceTask worked = taskRepository.save(MaintenanceTask.builder().order(order).sequence(1).description("Worked").shift(shift).build());
        MaintenanceTask other = taskRepository.save(MaintenanceTask.builder().order(order).sequence(2).description("Other").build());
        CatenaryDefect resolvedHere = defect("DEF-RPT-A-" + suffix, section, DefectSeverity.MEDIUM, DefectStatus.RESOLVED, Instant.parse("2026-01-27T22:00:00Z"));
        resolvedHere.setFoundInTask(worked);
        resolvedHere.setResolvedInShift(shift);
        defectRepository.save(resolvedHere);
        CatenaryDefect pending = defect("DEF-RPT-B-" + suffix, section, DefectSeverity.LOW, DefectStatus.OPEN, Instant.parse("2026-01-27T23:00:00Z"));
        pending.setFoundInTask(other);
        pending.setOrder(order);
        defectRepository.save(pending);
        materialRepository.save(MaintenanceMaterialUsage.builder().order(order).task(worked).materialId(UUID.randomUUID()).materialCode("GA70")
                .warehouseId(UUID.randomUUID()).plannedQuantity(new BigDecimal("2")).unit("ud").build());
        materialRepository.save(MaintenanceMaterialUsage.builder().order(order).materialId(UUID.randomUUID()).materialCode("CL-10")
                .warehouseId(UUID.randomUUID()).plannedQuantity(new BigDecimal("4")).unit("ud").build());
        entityManager.flush();
        entityManager.clear();

        assertEquals(List.of("DEF-RPT-A-" + suffix), defectRepository.findByFoundInTaskIdIn(List.of(worked.getId())).stream().map(CatenaryDefect::getCode).toList());
        assertEquals(2, defectRepository.findByFoundInTaskIdIn(List.of(worked.getId(), other.getId())).size());
        assertEquals(1, defectRepository.countByResolvedInShiftId(shift.getId()));
        assertEquals(List.of("GA70"), materialRepository.findByTaskIdIn(List.of(worked.getId(), other.getId())).stream().map(MaintenanceMaterialUsage::getMaterialCode).toList(),
                "Order-level lines have no task and stay out of the daily report rows");
        assertEquals(2, materialRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).size());
        assertEquals(List.of(worked.getId()), taskRepository.findByShiftIdOrderBySequenceAsc(shift.getId()).stream().map(MaintenanceTask::getId).toList());
        assertEquals(1, taskRepository.findByShiftIdAndStatusIn(shift.getId(), EnumSet.of(MaintenanceTaskStatus.PENDING, MaintenanceTaskStatus.IN_PROGRESS)).size());
        assertEquals(List.of("DEF-RPT-B-" + suffix), defectRepository.findByOrderIdAndStatus(order.getId(), DefectStatus.OPEN).stream().map(CatenaryDefect::getCode).toList());
    }

    @Test
    void theMonthlyReportQueriesFilterByExecutionPackageAndWindow() {
        long executionPackageId = System.nanoTime();
        long trackId = executionPackageId + 1;
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CatenaryAsset section = section("SEC-MON-" + suffix, trackId);
        section.setExecutionPackageId(executionPackageId);
        assetRepository.save(section);
        CatenaryAsset profile = profile("12-2.27-" + suffix, trackId, "12847.990");
        profile.setExecutionPackageId(executionPackageId);
        assetRepository.save(profile);
        shiftRepository.save(closedShift("SH-MON-A-" + suffix, LocalDate.of(2026, 1, 27), executionPackageId, trackId));
        shiftRepository.save(closedShift("SH-MON-B-" + suffix, LocalDate.of(2026, 1, 28), executionPackageId + 2, trackId));
        shiftRepository.save(closedShift("SH-MON-C-" + suffix, LocalDate.of(2026, 2, 2), executionPackageId, trackId));
        MaintenanceOrder completed = order("MO-MON-" + suffix, section, MaintenanceOrderStatus.COMPLETED, MaintenancePriority.MEDIUM, LocalDate.of(2026, 1, 20));
        completed.setActualStartDate(Instant.parse("2026-01-20T21:00:00Z"));
        completed.setActualEndDate(Instant.parse("2026-01-21T05:00:00Z"));
        orderRepository.save(completed);
        MaintenanceTask task = MaintenanceTask.builder().order(completed).sequence(1).description("Profile").asset(profile).status(MaintenanceTaskStatus.COMPLETED)
                .startedAt(Instant.parse("2026-01-20T22:00:00Z")).completedAt(Instant.parse("2026-01-21T00:30:00Z")).build();
        taskRepository.save(task);
        defectRepository.save(defect("DEF-MON-" + suffix, section, DefectSeverity.HIGH, DefectStatus.RESOLVED, Instant.parse("2026-01-21T00:00:00Z")));
        MaintenanceMaterialUsage consumed = MaintenanceMaterialUsage.builder().order(completed).materialId(UUID.randomUUID()).materialCode("GA70")
                .warehouseId(UUID.randomUUID()).plannedQuantity(new BigDecimal("2")).consumedQuantity(new BigDecimal("2")).unit("ud").build();
        MaintenanceMaterialUsage untouched = MaintenanceMaterialUsage.builder().order(completed).materialId(UUID.randomUUID()).materialCode("CL-10")
                .warehouseId(UUID.randomUUID()).plannedQuantity(new BigDecimal("4")).unit("ud").build();
        materialRepository.saveAll(List.of(consumed, untouched));
        entityManager.flush();
        entityManager.clear();
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");

        assertEquals(List.of("SH-MON-A-" + suffix), reportRepository.findShiftsBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), executionPackageId)
                .stream().map(MaintenanceShift::getCode).toList());
        assertTrue(reportRepository.findShiftsBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null).size() >= 2, "Without a package every shift of the month counts");
        assertEquals(1, reportRepository.findOrdersCompletedBetween(from, to, executionPackageId).size());
        assertTrue(reportRepository.findOrdersCompletedBetween(from, to, null).size() >= 1);
        assertTrue(reportRepository.countDefectsDetectedBetween(from, to, null) >= 1);
        assertEquals(0, reportRepository.findOrdersCompletedBetween(to, to.plusSeconds(1), executionPackageId).size());
        assertEquals(1, reportRepository.findTasksCompletedBetween(from, to, executionPackageId).size());
        assertEquals(1, reportRepository.findOrdersCreatedBetween(Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), executionPackageId).size(),
                "Creation is stamped by JPA auditing at save time");
        assertEquals(1, reportRepository.countDefectsDetectedBetween(from, to, executionPackageId));
        assertEquals(1, reportRepository.countDefectsResolvedBetween(from, to, executionPackageId));
        assertEquals(0, reportRepository.countDefectsResolvedBetween(from, to, executionPackageId + 2));
        assertEquals(List.of("GA70"), reportRepository.findMaterialsOfOrders(List.of(completed.getId())).stream().map(MaintenanceMaterialUsage::getMaterialCode).toList());
        assertTrue(reportRepository.findMaterialsOfOrders(List.of()).isEmpty());
        assertEquals(List.of(profile.getId()), reportRepository.findReportableAssets(executionPackageId, null, null).stream().map(CatenaryAsset::getId).toList(),
                "Track sections are not reportable assets");
        assertEquals(1, reportRepository.findReportableAssets(executionPackageId, trackId, CatenaryAssetType.PROFILE).size());
        assertTrue(reportRepository.findReportableAssets(executionPackageId, trackId, CatenaryAssetType.DISCONNECTOR).isEmpty());
        assertTrue(reportRepository.findAssetIdsWorkedBetween(from, to).contains(profile.getId()));
        assertTrue(reportRepository.findAssetIdsWorkedBetween(null, null).contains(profile.getId()), "No window: everything ever worked");
        assertTrue(reportRepository.findAssetIdsWorkedBetween(null, to).contains(profile.getId()));
        assertFalse(reportRepository.findAssetIdsWorkedBetween(to, null).contains(profile.getId()));
    }

    @Test
    void aMaterialLineIsUniquePerOrderTaskMaterialAndWarehouseEvenWithoutATask() {
        long trackId = System.nanoTime();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CatenaryAsset section = assetRepository.save(section("SEC-MAT-" + suffix, trackId));
        MaintenanceOrder order = orderRepository.save(order("MO-MAT-" + suffix, section, MaintenanceOrderStatus.DRAFT, MaintenancePriority.MEDIUM, null));
        UUID material = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        materialRepository.save(MaintenanceMaterialUsage.builder().order(order).materialId(material).materialCode("GA70").warehouseId(warehouse)
                .plannedQuantity(new BigDecimal("2")).unit("ud").build());
        entityManager.flush();

        assertNotNull(materialRepository.findByOrderIdAndStockSyncStatus(order.getId(), com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus.NOT_REQUESTED).getFirst());
        assertThrows(PersistenceException.class, () -> {
            materialRepository.save(MaintenanceMaterialUsage.builder().order(order).materialId(material).materialCode("GA70").warehouseId(warehouse)
                    .plannedQuantity(new BigDecimal("1")).unit("ud").build());
            entityManager.flush();
        }, "NULLS NOT DISTINCT: two order-level lines of the same material and warehouse collide");
    }

    @Test
    void aCancelledOrderWithoutAReasonIsRejectedByTheDatabase() {
        long trackId = System.nanoTime();
        CatenaryAsset section = assetRepository.save(section("SEC-CAN-" + UUID.randomUUID().toString().substring(0, 8), trackId));
        MaintenanceOrder order = order("MO-CAN-" + trackId, section, MaintenanceOrderStatus.CANCELLED, MaintenancePriority.MEDIUM, null);

        assertThrows(PersistenceException.class, () -> {
            orderRepository.save(order);
            entityManager.flush();
        });
    }

    private static CatenaryDefect defect(String code, CatenaryAsset asset, DefectSeverity severity, DefectStatus status, Instant detectedAt) {
        CatenaryDefect defect = CatenaryDefect.builder().code(code).asset(asset).severity(severity).status(status).description(code).detectedAt(detectedAt).build();
        defect.locateAt(asset);
        if (status == DefectStatus.RESOLVED || status == DefectStatus.CLOSED) {
            defect.setResolvedAt(detectedAt.plusSeconds(3600));
        }
        return defect;
    }

    private static MaintenanceInspection inspection(String code, CatenaryAsset asset, LocalDate date, String inspector, InspectionResult result) {
        return MaintenanceInspection.builder().code(code).asset(asset).trackId(asset.getTrackId()).executionPackageId(asset.getExecutionPackageId())
                .inspectionDate(date).inspector(inspector).result(result).build();
    }

    private static MaintenanceShift closedShift(String code, LocalDate date, long executionPackageId, long trackId) {
        return MaintenanceShift.builder().code(code).shiftDate(date).possessionType(PossessionType.PARTIAL).status(ShiftStatus.CLOSED)
                .executionPackageId(executionPackageId).trackIds(new LinkedHashSet<>(List.of(trackId)))
                .actualStart(date.atTime(21, 0).atOffset(java.time.ZoneOffset.UTC).toInstant())
                .actualEnd(date.plusDays(1).atTime(5, 0).atOffset(java.time.ZoneOffset.UTC).toInstant())
                .netWorkMinutes(290).build();
    }
}
