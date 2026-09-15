package com.alejandro.mtomaintenance;

import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataEntityNames;
import com.alejandro.mtomaintenance.application.service.CatenaryAssetService;
import com.alejandro.mtomaintenance.application.service.CatenaryDefectService;
import com.alejandro.mtomaintenance.application.service.EntityAuditService;
import com.alejandro.mtomaintenance.application.service.InboxMessageService;
import com.alejandro.mtomaintenance.application.service.InspectionTemplateService;
import com.alejandro.mtomaintenance.application.service.MaintenanceCodeGenerator;
import com.alejandro.mtomaintenance.application.service.MaintenanceInspectionService;
import com.alejandro.mtomaintenance.application.service.MaintenanceMaterialUsageService;
import com.alejandro.mtomaintenance.application.service.MaintenanceOrderService;
import com.alejandro.mtomaintenance.application.service.MaintenanceReportService;
import com.alejandro.mtomaintenance.application.service.MaintenanceShiftService;
import com.alejandro.mtomaintenance.application.service.MaintenanceTaskService;
import com.alejandro.mtomaintenance.application.service.MaintenanceTaskTypeService;
import com.alejandro.mtomaintenance.application.service.MaintenanceTeamService;
import com.alejandro.mtomaintenance.application.service.MasterDataEntityHandler;
import com.alejandro.mtomaintenance.application.service.MasterDataEventHandler;
import com.alejandro.mtomaintenance.application.service.MasterDataEventProcessor;
import com.alejandro.mtomaintenance.application.service.StatusHistoryService;
import com.alejandro.mtomaintenance.application.service.StockClient;
import com.alejandro.mtomaintenance.application.service.WorkloadEstimator;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetRequest;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetResponse;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectResponse;
import com.alejandro.mtomaintenance.application.dto.history.StatusHistoryResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.CreateCorrectiveOrderRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.CreateDefectFromInspectionRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionResponse;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageRequest;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageResponse;
import com.alejandro.mtomaintenance.application.dto.order.AssignOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.CompleteOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.application.dto.order.PlanOrderRequest;
import com.alejandro.mtomaintenance.application.dto.report.MonthlyReportResponse;
import com.alejandro.mtomaintenance.application.dto.report.ProgressReportResponse;
import com.alejandro.mtomaintenance.application.dto.shift.CloseShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportResponse;
import com.alejandro.mtomaintenance.application.dto.shift.StartShiftRequest;
import com.alejandro.mtomaintenance.application.dto.task.CompleteTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksResponse;
import com.alejandro.mtomaintenance.application.dto.task.InlineDefectRequest;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskResponse;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.ShiftException;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alejandro.mtomaintenance.support.PostgreSQLTestContainer;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * El unico {@code @SpringBootTest}: arranca el contexto entero contra un PostgreSQL real sin
 * sustituir ningun servicio por un mock. Es lo que detecta un {@code @Service} que no llega a ser
 * bean (un {@code @ConditionalOnBean} en una clase escaneada, por ejemplo) antes de empaquetar.
 */
@SpringBootTest(properties = {
        // No hay broker ni mto-stock en este test. El cableado del canal se comprueba en
        // MessagingLayerTest y el cliente de stock en StockClientTest, ninguno de los dos los necesita.
        "app.rabbitmq.enabled=false",
        "app.stock.enabled=false"
})
class MtoMaintenanceApplicationTests extends PostgreSQLTestContainer {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registerPostgreSQLProperties(registry);
    }

    @Autowired
    private ApplicationContext context;

    @Autowired(required = false)
    private MasterDataEventHandler masterDataEventHandler;

    @Autowired(required = false)
    private List<MasterDataEntityHandler> masterDataEntityHandlers;

    @Autowired(required = false)
    private Tracer tracer;

    @Autowired
    private CatenaryAssetService assetService;

    @Autowired
    private CatenaryAssetRepository assetRepository;

    @Autowired
    private MaintenanceOrderService orderService;

    @Autowired
    private MaintenanceTaskService taskService;

    @Autowired
    private MaintenanceShiftService shiftService;

    @Autowired
    private MaintenanceMaterialUsageService materialService;

    @Autowired
    private MaintenanceInspectionService inspectionService;

    @Autowired
    private CatenaryDefectService defectService;

    @Autowired
    private StatusHistoryService historyService;

    @Autowired
    private MaintenanceReportService reportService;

    @Test
    void contextLoads() {
        assertNotNull(masterDataEventHandler);
    }

    @Test
    void everyBusinessServiceIsInTheContext() {
        for (Class<?> service : BusinessServices.ALL) {
            assertThat(context.getBeanNamesForType(service))
                    .withFailMessage("""
                            No hay ningun bean de %s en el contexto. Los controladores lo piden por \
                            constructor, asi que la aplicacion no arranca. Comprobar que su impl \
                            sigue anotado con @Service y que no ha vuelto un @ConditionalOnBean: \
                            esa anotacion solo vale en autoconfiguraciones y aqui es siempre falsa.""",
                            service.getSimpleName())
                    .isNotEmpty();
        }
    }

    @Test
    void theMasterDataHandlersAreRegistered() {
        assertThat(masterDataEntityHandlers)
                .withFailMessage("No hay ningun MasterDataEntityHandler en el contexto")
                .isNotNull();
        assertThat(masterDataEntityHandlers)
                .extracting(MasterDataEntityHandler::entityName)
                .containsAll(BusinessServices.HANDLED_ENTITIES);
    }

    @Test
    void theTracingBridgeIsInTheContext() {
        assertNotNull(tracer);
    }

    /**
     * El flujo completo del preventivo contra la base real y sin ningun mock: tramo, perfiles, orden,
     * tareas generadas, turno, cierre y la traza que deja (historial de estados y revisiones de Envers).
     * Con {@code app.stock.enabled=false} las lineas de material quedan NOT_REQUESTED.
     */
    @Test
    void aPreventiveOrderRunsProfileByProfileFromDraftToCompletionAgainstTheRealDatabase() {
        long trackId = System.nanoTime();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        assetRepository.save(profile("12-2.27-" + suffix, trackId, "12847.990"));
        assetRepository.save(profile("12-2.28-" + suffix, trackId, "12899.290"));
        CatenaryAsset disabled = profile("12-2.29-" + suffix, trackId, "12950.000");
        disabled.setEnabled(false);
        assetRepository.save(disabled);
        CatenaryAssetResponse section = assetService.createTrackSection(new CatenaryAssetRequest("SEC-E2E-" + suffix, "E2E section", null, 6L, trackId, null,
                new BigDecimal("12847.990"), new BigDecimal("14078.090"), TrackKind.MAIN, 365));

        MaintenanceOrderResponse order = orderService.create(new MaintenanceOrderRequest("E2E preventive", null, MaintenanceOrderType.PREVENTIVE, null,
                section.id(), null, null, null, null));
        assertTrue(order.code().matches("MO-\\d{6}"), order.code());
        assertEquals(MaintenanceOrderStatus.DRAFT, order.status());
        assertEquals(trackId, order.trackId());

        GeneratePreventiveTasksResponse generated = taskService.generatePreventiveTasks(order.id(), new GeneratePreventiveTasksRequest(null, null));
        assertEquals(2, generated.createdTasks(), "One task per enabled profile of the section");
        assertEquals(1, generated.estimatedShifts());
        assertEquals(2, taskService.generatePreventiveTasks(order.id(), new GeneratePreventiveTasksRequest(null, null)).skippedProfiles(), "Idempotent per profile");

        MaterialUsageResponse material = materialService.register(order.id(), new MaterialUsageRequest(UUID.randomUUID(), "GA70", UUID.randomUUID(),
                new BigDecimal("4"), "ud", null, null));
        assertEquals(StockSyncStatus.NOT_REQUESTED, material.stockSyncStatus());

        orderService.plan(order.id(), new PlanOrderRequest(LocalDate.now().plusDays(1), "week 5"));
        orderService.assign(order.id(), new AssignOrderRequest(null, "yossi", null));
        assertThrows(InvalidTransitionException.class, () -> orderService.complete(order.id(), new CompleteOrderRequest("too early", null, null)));
        orderService.start(order.id(), "go");

        MaintenanceShiftResponse shift = shiftService.create(new MaintenanceShiftRequest(LocalDate.now(ZoneOffset.UTC), null, null, null, PossessionType.PARTIAL, null, null,
                null, null, null, 6L, Set.of(trackId), null, null, null, null, null));
        assertTrue(shift.code().matches("SH-\\d{6}"), shift.code());
        MaintenanceTaskResponse first = taskService.findByOrder(order.id()).getFirst();
        assertThrows(ShiftException.class, () -> taskService.complete(order.id(), first.id(),
                new CompleteTaskRequest(shift.id(), null, "done", null, true, null, null, null, null)), "The shift has not started yet");
        shiftService.start(shift.id(), new StartShiftRequest(Instant.now().minusSeconds(3600), null));

        for (MaintenanceTaskResponse task : taskService.findByOrder(order.id())) {
            boolean withDefect = task.id().equals(first.id());
            MaintenanceTaskResponse completed = taskService.complete(order.id(), task.id(), new CompleteTaskRequest(shift.id(), null, "checked", null, true, null,
                    withDefect ? java.util.List.of(new InlineDefectRequest(DefectSeverity.LOW, "Loose dropper loop", null, "Reattached", null)) : null, null, null));
            assertEquals(MaintenanceTaskStatus.COMPLETED, completed.status());
            assertEquals(shift.id(), completed.shiftId());
        }
        MaintenanceShiftResponse closed = shiftService.close(shift.id(), new CloseShiftRequest(null, null, null, "smooth night"));
        assertEquals(ShiftStatus.CLOSED, closed.status());
        assertTrue(closed.netWorkMinutes() >= 59, "About an hour from the actual start");
        ShiftReportResponse report = shiftService.report(shift.id());
        assertEquals(2, report.profilesReviewed());
        assertEquals(1, report.defectsFound());
        assertEquals(1, report.defectsResolved());
        assertEquals(2, shiftService.profiles(shift.id(), null).size());

        MaintenanceOrderResponse completed = orderService.complete(order.id(), new CompleteOrderRequest("all profiles done", null, null));

        assertEquals(MaintenanceOrderStatus.COMPLETED, completed.status());
        assertEquals(2, completed.completedTaskCount());
        Instant checkedAt = assetRepository.findById(section.id()).orElseThrow().getLastPreventiveCompletedAt();
        assertTrue(Duration.between(completed.actualEndDate(), checkedAt).abs().compareTo(Duration.ofMillis(1)) < 0,
                "The section is marked as checked at the order's end (timestamptz rounds to microseconds)");
        assertEquals(java.util.List.of("DRAFT", "PLANNED", "ASSIGNED", "IN_PROGRESS", "COMPLETED"),
                historyService.findByOrder(order.id()).stream().map(StatusHistoryResponse::newStatus).toList());
        assertTrue(orderService.findRevisions(order.id(), PageRequest.of(0, 10)).page().totalElements() >= 2, "Envers keeps every state change");
        assertEquals(DefectStatus.RESOLVED, defectService.search(null, null, null, order.id(), null, null, null, null, null, PageRequest.of(0, 10))
                .content().getFirst().status());

        // Los informes se derivan de lo guardado, sin ventana ni filtros opcionales.
        ProgressReportResponse progress = reportService.progress(null, trackId, null, null, null);
        assertEquals(2, progress.totalAssets(), "The disabled profile and the track section are not reportable");
        assertEquals(2, progress.checkedAssets());
        assertEquals(0, new BigDecimal("1.0000").compareTo(progress.completionRatio()));
        MonthlyReportResponse monthly = reportService.monthly(YearMonth.now(ZoneOffset.UTC), null);
        assertTrue(monthly.shiftsClosed() >= 1);
        assertTrue(monthly.ordersCompleted() >= 1);
        assertTrue(monthly.profilesChecked() >= 2);
        assertTrue(monthly.defectsResolved() >= 1);
    }

    /** Inspeccion -> defecto -> orden correctiva, con la idempotencia de los dos pasos y el enlace del defecto a la orden. */
    @Test
    void anInspectionWithAMajorDefectProducesADefectAndACorrectiveOrderOnlyOnce() {
        long trackId = System.nanoTime();
        CatenaryAsset profile = assetRepository.save(profile("13-2.10-" + UUID.randomUUID().toString().substring(0, 8), trackId, "13499.290"));

        MaintenanceInspectionResponse inspection = inspectionService.create(new MaintenanceInspectionRequest(profile.getId(), LocalDate.now(), "dana", null,
                InspectionResult.MAJOR_DEFECT, null, "Cracked insulator", "Replace it", null, null, null));
        assertTrue(inspection.code().matches("INS-\\d{6}"), inspection.code());
        assertEquals(14, inspection.items().size(), "The profile checklist is copied from the seeded template");

        CatenaryDefectResponse defect = inspectionService.createDefect(inspection.id(), new CreateDefectFromInspectionRequest(null, null, null, null));
        assertEquals(defect.id(), inspectionService.createDefect(inspection.id(), new CreateDefectFromInspectionRequest(null, null, null, null)).id());
        assertEquals(DefectSeverity.HIGH, defect.severity());
        assertEquals(DefectStatus.OPEN, defect.status());
        assertEquals(CatenaryAssetType.PROFILE, defect.asset().type());

        MaintenanceOrderResponse corrective = inspectionService.createCorrectiveOrder(inspection.id(), new CreateCorrectiveOrderRequest(null, null, null, null, null));
        assertEquals(corrective.id(), inspectionService.createCorrectiveOrder(inspection.id(), new CreateCorrectiveOrderRequest(null, null, null, null, null)).id());
        assertEquals(MaintenanceOrderType.CORRECTIVE, corrective.type());
        assertEquals(MaintenancePriority.HIGH, corrective.priority());
        assertEquals(inspection.id(), corrective.originInspectionId());
        assertEquals(defect.id(), corrective.originDefectId());
        assertEquals(DefectStatus.IN_PROGRESS, defectService.findById(defect.id()).status(), "The defect now waits for the corrective order");
        assertEquals(corrective.id(), defectService.findById(defect.id()).orderId());
        assertEquals(java.util.List.of("OPEN", "IN_PROGRESS"), historyService.findByDefect(defect.id()).stream().map(StatusHistoryResponse::newStatus).toList());
    }

    private static CatenaryAsset profile(String name, long trackId, String kp) {
        return CatenaryAsset.builder().code("PRF-" + name).name(name).type(CatenaryAssetType.PROFILE).trackId(trackId).executionPackageId(6L)
                .startKp(new BigDecimal(kp)).endKp(new BigDecimal(kp)).build();
    }

    /** Lista viva: cada fase que anade un servicio lo anade aqui. */
    static final class BusinessServices {
        static final List<Class<?>> ALL = List.of(
                CatenaryAssetService.class,
                CatenaryDefectService.class,
                EntityAuditService.class,
                InboxMessageService.class,
                InspectionTemplateService.class,
                MaintenanceCodeGenerator.class,
                MaintenanceInspectionService.class,
                MaintenanceMaterialUsageService.class,
                MaintenanceOrderService.class,
                MaintenanceReportService.class,
                MaintenanceShiftService.class,
                MaintenanceTaskService.class,
                MaintenanceTaskTypeService.class,
                MaintenanceTeamService.class,
                MasterDataEventProcessor.class,
                StatusHistoryService.class,
                StockClient.class,
                WorkloadEstimator.class);

        static final List<String> HANDLED_ENTITIES = List.of(
                MasterDataEntityNames.PROFILE,
                MasterDataEntityNames.DISCONNECTOR,
                MasterDataEntityNames.SECTION_INSULATOR,
                MasterDataEntityNames.TRACK);

        private BusinessServices() {
        }
    }
}
