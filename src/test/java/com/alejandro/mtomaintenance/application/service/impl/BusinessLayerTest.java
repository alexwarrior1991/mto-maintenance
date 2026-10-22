package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.tasktype.MaintenanceTaskTypeUpdateRequest;
import com.alejandro.mtomaintenance.application.exception.ScopeException;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.UnavailablePeriod;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TaskEquipment;
import java.util.EnumSet;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetRequest;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.asset.PreventiveDueSoonReport;
import com.alejandro.mtomaintenance.application.dto.messaging.DomainEvent;
import com.alejandro.mtomaintenance.application.dto.shift.VehicleParkingRequest;
import com.alejandro.mtomaintenance.application.exception.SynchronizedTeamException;
import com.alejandro.mtomaintenance.application.service.DomainEventPublisher;
import com.alejandro.mtomaintenance.configuration.events.PreventiveDueSoonProperties;
import com.alejandro.mtomaintenance.configuration.reports.ReportProperties;
import com.alejandro.mtomaintenance.configuration.security.CurrentUserService;
import com.alejandro.mtomaintenance.configuration.shifts.ScopeProperties;
import com.alejandro.mtomaintenance.configuration.shifts.ShiftProperties;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CrewPerson;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FleetVehicle;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInstrument;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShiftVehicle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectRequest;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.defect.DefectCommentRequest;
import com.alejandro.mtomaintenance.application.dto.defect.ResolveDefectRequest;
import com.alejandro.mtomaintenance.application.dto.history.StatusHistoryResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.CheckItemUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.CreateCorrectiveOrderRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.CreateDefectFromInspectionRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.InspectionTemplateResponse;
import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageRequest;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageResponse;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.order.AssignOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.CancelOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.CompleteOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.order.PlanOrderRequest;
import com.alejandro.mtomaintenance.application.dto.export.ExportedReport;
import com.alejandro.mtomaintenance.application.dto.export.ReportDocument;
import com.alejandro.mtomaintenance.application.dto.export.ReportFormat;
import com.alejandro.mtomaintenance.application.dto.export.ReportValue;
import com.alejandro.mtomaintenance.application.dto.report.MonthlyMaterialLineResponse;
import com.alejandro.mtomaintenance.application.dto.report.MonthlyReportResponse;
import com.alejandro.mtomaintenance.application.dto.report.ProgressReportResponse;
import com.alejandro.mtomaintenance.application.dto.report.ProgressRowResponse;
import com.alejandro.mtomaintenance.application.dto.shift.CancelShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.CloseShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportInspectionResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportRowResponse;
import com.alejandro.mtomaintenance.application.dto.shift.StartShiftRequest;
import com.alejandro.mtomaintenance.application.dto.stock.StockMaterial;
import com.alejandro.mtomaintenance.application.dto.stock.StockReservation;
import com.alejandro.mtomaintenance.application.dto.task.CancelTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.CompleteTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksResponse;
import com.alejandro.mtomaintenance.application.dto.task.GenerationOutcome;
import com.alejandro.mtomaintenance.application.dto.task.GenerationPreviewLine;
import com.alejandro.mtomaintenance.application.dto.task.GenerationSelection;
import com.alejandro.mtomaintenance.application.dto.task.InlineDefectRequest;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.task.StartTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.TaskMaterialRequest;
import com.alejandro.mtomaintenance.application.dto.tasktype.MaintenanceTaskTypeResponse;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamRequest;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamSummaryResponse;
import com.alejandro.mtomaintenance.application.exception.AssetDisabledException;
import com.alejandro.mtomaintenance.application.exception.DuplicateCodeException;
import com.alejandro.mtomaintenance.application.exception.InspectionException;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.MaterialUsageException;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.exception.ShiftException;
import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.exception.StaleVersionException;
import com.alejandro.mtomaintenance.application.exception.StockRejectedException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.exception.UnsyncedMaterialsException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.application.mapper.CatenaryAssetMapper;
import com.alejandro.mtomaintenance.application.mapper.CatenaryDefectMapper;
import com.alejandro.mtomaintenance.application.mapper.InspectionTemplateMapper;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceInspectionMapper;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceOrderMapper;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceShiftMapper;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceTaskMapper;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceTaskTypeMapper;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceTeamMapper;
import com.alejandro.mtomaintenance.application.mapper.MaterialUsageMapper;
import com.alejandro.mtomaintenance.application.mapper.StatusHistoryMapper;
import com.alejandro.mtomaintenance.application.service.EntityAuditService;
import com.alejandro.mtomaintenance.application.service.MaintenanceCodeGenerator;
import com.alejandro.mtomaintenance.application.service.MaintenanceMaterialUsageService;
import com.alejandro.mtomaintenance.application.service.MaintenanceOrderService;
import com.alejandro.mtomaintenance.application.service.ReportExporter;
import com.alejandro.mtomaintenance.application.service.StatusHistoryService;
import com.alejandro.mtomaintenance.application.service.StockClient;
import com.alejandro.mtomaintenance.application.service.StockSyncRetryService;
import com.alejandro.mtomaintenance.application.service.WorkloadEstimator;
import com.alejandro.mtomaintenance.domain.model.Quantity;
import com.alejandro.mtomaintenance.domain.model.WorkloadEstimate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetSwitch;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CheckItemResult;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DisconnectorDriveType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FunctionalGroup;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplateItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspection;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspectionItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceStatusHistory;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskCheckItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTeam;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import com.alejandro.mtomaintenance.configuration.stock.StockSyncRetryConfiguration;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockRequestType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TaskUnit;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryDefectRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CrewPersonRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.FleetVehicleRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceInstrumentRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.InspectionTemplateRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceInspectionRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceMaterialUsageRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceMaterialUsageRepository.LineRef;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.SessionAdvisoryLock;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceOrderRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceReportRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceShiftRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceStatusHistoryRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskTypeRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTeamRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas de negocio de los servicios, con los repositorios y colaboradores simulados (sin extension de Mockito: los fixtures comparten stubs que no todos los casos usan). */
class BusinessLayerTest {

    // ----------------------------------------------------------------- orders

    @Test
    void creatingAnOrderStartsInDraftWithAGeneratedCodeAndTheAssetLocation() {
        OrderFixture fixture = new OrderFixture();
        CatenaryAsset section = trackSection();
        when(fixture.lookups.enabledAsset(section.getId())).thenReturn(section);
        when(fixture.codeGenerator.nextOrderCode()).thenReturn("MO-000001");

        fixture.service.create(new MaintenanceOrderRequest("Preventive T2", null, MaintenanceOrderType.PREVENTIVE, null,
                section.getId(), null, null, null, null));

        MaintenanceOrder saved = fixture.savedOrder();
        assertEquals("MO-000001", saved.getCode());
        assertEquals(MaintenanceOrderStatus.DRAFT, saved.getStatus());
        assertEquals(MaintenancePriority.MEDIUM, saved.getPriority());
        assertEquals(2L, saved.getTrackId());
        assertEquals(6L, saved.getExecutionPackageId());
        assertEquals(section.getStartKp(), saved.getStartKp());
        verify(fixture.history).recordOrderChange(saved, null, "DRAFT", "Order created");
    }

    @Test
    void urgentOrdersAreAlwaysCriticalAndStartFromDraft() {
        OrderFixture fixture = new OrderFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.URGENT, MaintenanceOrderStatus.DRAFT);
        when(fixture.lookups.order(order.getId())).thenReturn(order);

        fixture.service.start(order.getId(), "emergency");

        assertEquals(MaintenanceOrderStatus.IN_PROGRESS, order.getStatus());
        assertNotNull(order.getActualStartDate());
        verify(fixture.history).recordOrderChange(order, "DRAFT", "IN_PROGRESS", "emergency");
    }

    @Test
    void planningRejectsPastDatesAndReservesTheMaterials() {
        OrderFixture fixture = new OrderFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        MaintenanceMaterialUsage line = line(order);
        when(fixture.lookups.order(order.getId())).thenReturn(order);

        assertThrows(ValidationException.class, () -> fixture.service.plan(order.getId(), new PlanOrderRequest(LocalDate.now().minusDays(1), null)));

        fixture.service.plan(order.getId(), new PlanOrderRequest(LocalDate.now().plusDays(3), "week 4"));

        assertEquals(MaintenanceOrderStatus.PLANNED, order.getStatus());
        verify(fixture.stock).reserveAll(order);
        assertTrue(order.getMaterials().contains(line));
    }

    @Test
    void startingRequiresAPlannedOrAssignedOrderUnlessUrgent() {
        OrderFixture fixture = new OrderFixture();
        MaintenanceOrder draft = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.DRAFT);
        MaintenanceOrder cancelled = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.CANCELLED);
        MaintenanceOrder completed = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.COMPLETED);
        when(fixture.lookups.order(draft.getId())).thenReturn(draft);
        when(fixture.lookups.order(cancelled.getId())).thenReturn(cancelled);
        when(fixture.lookups.order(completed.getId())).thenReturn(completed);

        assertThrows(InvalidTransitionException.class, () -> fixture.service.start(draft.getId(), null));
        assertThrows(InvalidTransitionException.class, () -> fixture.service.start(cancelled.getId(), null));
        assertThrows(InvalidTransitionException.class, () -> fixture.service.start(completed.getId(), null));
        assertNull(draft.getActualStartDate());
    }

    @Test
    void assigningInProgressReassignsWithoutChangingTheState() {
        OrderFixture fixture = new OrderFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        when(fixture.lookups.order(order.getId())).thenReturn(order);

        fixture.service.assign(order.getId(), new AssignOrderRequest(null, "yossi", null));

        assertEquals(MaintenanceOrderStatus.IN_PROGRESS, order.getStatus());
        assertEquals("yossi", order.getAssignedUser());
        verify(fixture.history).recordOrderChange(order, "IN_PROGRESS", "IN_PROGRESS", "Reassigned");
    }

    @Test
    void completingRequiresActualStartClosedTasksAndSomethingToShow() {
        OrderFixture fixture = new OrderFixture();
        MaintenanceOrder noStart = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        when(fixture.lookups.order(noStart.getId())).thenReturn(noStart);
        assertThrows(InvalidTransitionException.class, () -> fixture.service.complete(noStart.getId(), new CompleteOrderRequest(null, false, null)));

        MaintenanceOrder pendingTask = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        pendingTask.setActualStartDate(Instant.now());
        pendingTask.getTasks().add(task(pendingTask, MaintenanceTaskStatus.PENDING));
        when(fixture.lookups.order(pendingTask.getId())).thenReturn(pendingTask);
        assertThrows(InvalidTransitionException.class, () -> fixture.service.complete(pendingTask.getId(), new CompleteOrderRequest(null, false, null)));

        MaintenanceOrder nothingDone = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        nothingDone.setActualStartDate(Instant.now());
        when(fixture.lookups.order(nothingDone.getId())).thenReturn(nothingDone);
        assertThrows(InvalidTransitionException.class, () -> fixture.service.complete(nothingDone.getId(), new CompleteOrderRequest(" ", false, null)));

        MaintenanceOrder cancelled = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.CANCELLED);
        when(fixture.lookups.order(cancelled.getId())).thenReturn(cancelled);
        assertThrows(InvalidTransitionException.class, () -> fixture.service.complete(cancelled.getId(), new CompleteOrderRequest("done", false, null)));
    }

    @Test
    void completingAPreventiveOrderSetsTheEndDateConsumesMaterialsAndMarksTheProfilesChecked() {
        OrderFixture fixture = new OrderFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        order.setActualStartDate(Instant.now().minusSeconds(3600));
        MaintenanceTask done = task(order, MaintenanceTaskStatus.COMPLETED);
        done.setAsset(profile("12-2.27", "12847.990"));
        done.setCompletedAt(Instant.now());
        order.getTasks().add(done);
        MaintenanceMaterialUsage line = line(order);
        when(fixture.lookups.order(order.getId())).thenReturn(order);

        fixture.service.complete(order.getId(), new CompleteOrderRequest(null, false, "all good"));

        assertEquals(MaintenanceOrderStatus.COMPLETED, order.getStatus());
        assertNotNull(order.getActualEndDate());
        assertEquals(order.getActualEndDate(), order.getAsset().getLastPreventiveCompletedAt());
        assertEquals(done.getCompletedAt(), done.getAsset().getLastPreventiveCompletedAt());
        verify(fixture.stock).consumeAll(order);
        verify(fixture.history).recordOrderChange(order, "IN_PROGRESS", "COMPLETED", "all good");
    }

    @Test
    void completingWithALineNotSynchronizedNeedsForceAndARejectionLeavesTheOrderUntouched() {
        OrderFixture fixture = new OrderFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        order.setActualStartDate(Instant.now());
        order.getTasks().add(task(order, MaintenanceTaskStatus.COMPLETED));
        MaintenanceMaterialUsage failed = line(order);
        failed.markFailed("reserve: stock down");
        MaintenanceMaterialUsage rejected = line(order);
        rejected.markRejected("reserve: mto-stock rejected 'reserve 2 of GA70' with 409 STK-001: Insufficient available stock");
        when(fixture.lookups.order(order.getId())).thenReturn(order);

        assertThrows(UnsyncedMaterialsException.class, () -> fixture.service.complete(order.getId(), new CompleteOrderRequest("done", false, null)));

        // Stock ya se ha llamado por cada linea, y ese rechazo no deshace la transaccion: la orden no puede quedar tocada.
        verify(fixture.stock).consumeAll(order);
        assertEquals(MaintenanceOrderStatus.IN_PROGRESS, order.getStatus());
        assertNull(order.getClosingNotes());
        assertNull(order.getActualEndDate());
        verify(fixture.history, never()).recordOrderChange(any(), any(), any(), any());
        verify(fixture.repository, never()).save(any());

        fixture.service.complete(order.getId(), new CompleteOrderRequest("done", true, null));

        assertEquals(MaintenanceOrderStatus.COMPLETED, order.getStatus());
        assertEquals("done\nCompleted with 2 material line(s) pending stock synchronization", order.getClosingNotes());
    }

    @Test
    void whatStockAlreadyDidSurvivesARejectedCompletionOrAFailedSync() throws NoSuchMethodException {
        // Un test unitario no ve la transaccion: esto guarda que esos rechazos no la deshagan.
        Transactional complete = MaintenanceOrderServiceImpl.class.getMethod("complete", UUID.class, CompleteOrderRequest.class)
                .getAnnotation(Transactional.class);
        assertEquals(List.of(UnsyncedMaterialsException.class), List.of(complete.noRollbackFor()));
        Transactional sync = MaintenanceMaterialUsageServiceImpl.class.getMethod("sync", UUID.class, UUID.class)
                .getAnnotation(Transactional.class);
        assertEquals(Set.of(StockUnavailableException.class, StockRejectedException.class), Set.of(sync.noRollbackFor()));
    }

    @Test
    void cancellingReleasesReservationsCancelsOpenTasksAndReopensLinkedDefects() {
        OrderFixture fixture = new OrderFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.ASSIGNED);
        MaintenanceTask open = task(order, MaintenanceTaskStatus.PENDING);
        order.getTasks().add(open);
        MaintenanceMaterialUsage line = line(order);
        CatenaryDefect defect = CatenaryDefect.builder().code("DEF-000001").asset(order.getAsset()).severity(DefectSeverity.HIGH)
                .status(DefectStatus.IN_PROGRESS).description("kink").detectedAt(Instant.now()).order(order).build();
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.defectRepository.findByOrderIdAndStatus(order.getId(), DefectStatus.IN_PROGRESS)).thenReturn(List.of(defect));

        fixture.service.cancel(order.getId(), new CancelOrderRequest("no possession granted"));

        assertEquals(MaintenanceOrderStatus.CANCELLED, order.getStatus());
        assertEquals("no possession granted", order.getCancellationReason());
        assertEquals(MaintenanceTaskStatus.CANCELLED, open.getStatus());
        assertEquals(DefectStatus.OPEN, defect.getStatus());
        verify(fixture.stock).releaseAll(order);
        verify(fixture.history).recordDefectChange(eq(defect), eq("IN_PROGRESS"), eq("OPEN"), anyString());
        verify(fixture.history).recordOrderChange(order, "ASSIGNED", "CANCELLED", "no possession granted");
    }

    // ------------------------------------------------------------- materials

    @Test
    void registeringAMaterialInDraftLeavesItNotRequestedAndInPlannedReservesIt() {
        MaterialFixture fixture = new MaterialFixture();
        MaintenanceOrder draft = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        when(fixture.lookups.order(draft.getId())).thenReturn(draft);
        UUID warehouse = UUID.randomUUID();
        UUID material = UUID.randomUUID();

        fixture.service.register(draft.getId(), new MaterialUsageRequest(material, "GA70", warehouse, new BigDecimal("4"), "ud", null, null));

        MaintenanceMaterialUsage saved = fixture.savedLine();
        assertEquals(StockSyncStatus.NOT_REQUESTED, saved.getStockSyncStatus());
        assertEquals(0, new BigDecimal("4").compareTo(saved.getPlannedQuantity()));
        verify(fixture.stock, never()).reserve(any());

        MaintenanceOrder planned = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        planned.setStockProjectId(UUID.randomUUID());
        when(fixture.lookups.order(planned.getId())).thenReturn(planned);
        fixture.service.register(planned.getId(), new MaterialUsageRequest(material, "GA70", warehouse, new BigDecimal("4"), "ud", null, null));

        verify(fixture.stock).reserve(any(MaintenanceMaterialUsage.class));
    }

    @Test
    void materialQuantitiesAreValidatedAndOverConsumptionNeedsTheFlag() {
        MaterialFixture fixture = new MaterialFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        when(fixture.lookups.order(order.getId())).thenReturn(order);

        assertThrows(ValidationException.class, () -> fixture.service.register(order.getId(),
                new MaterialUsageRequest(UUID.randomUUID(), "GA70", UUID.randomUUID(), new BigDecimal("-1"), "ud", null, null)));

        MaintenanceMaterialUsage line = line(order);
        ReflectionTestUtils.setField(line, "id", UUID.randomUUID());
        when(fixture.repository.findByIdAndOrderId(line.getId(), order.getId())).thenReturn(Optional.of(line));

        assertThrows(MaterialUsageException.class, () -> fixture.service.update(order.getId(), line.getId(),
                new MaterialUsageUpdateRequest(null, new BigDecimal("3"), null, null)));
        fixture.service.update(order.getId(), line.getId(), new MaterialUsageUpdateRequest(null, new BigDecimal("3"), true, null));
        assertEquals(0, new BigDecimal("3").compareTo(line.getConsumedQuantity()));
    }

    @Test
    void takingTheOverConsumptionFlagOffALineThatAlreadyConsumedMoreIsAMaterialRuleNotAConstraintError() {
        MaterialFixture fixture = new MaterialFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        MaintenanceMaterialUsage line = line(order);
        line.setAllowOverConsumption(true);
        line.setConsumedQuantity(new BigDecimal("3"));
        when(fixture.repository.findByIdAndOrderId(line.getId(), order.getId())).thenReturn(Optional.of(line));

        // Solo el permiso: no viaja ninguna cantidad y nada lo comprobaba; lo rechazaba el CHECK de la
        // base al confirmar, con un 409 generico en vez del MAT-001 de una linea.
        assertThrows(MaterialUsageException.class, () -> fixture.service.update(order.getId(), line.getId(),
                new MaterialUsageUpdateRequest(null, null, false, null)));
        verify(fixture.repository, never()).save(any());

        // Con lo consumido de vuelta dentro de lo previsto, si.
        line.setAllowOverConsumption(true);
        fixture.service.update(order.getId(), line.getId(), new MaterialUsageUpdateRequest(null, new BigDecimal("2"), false, null));
        assertFalse(line.getAllowOverConsumption());
        assertEquals(0, new BigDecimal("2").compareTo(line.getConsumedQuantity()));
    }

    @Test
    void stockOutagesLeaveTheLineFailedAndSyncRetriesIt() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher());
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        order.setStockProjectId(UUID.randomUUID());
        MaintenanceMaterialUsage line = line(order);
        when(stockClient.reserve(any(), any(), any(), any(), any())).thenThrow(new StockUnavailableException("stock down"));

        synchronizer.reserve(line);

        assertEquals(StockSyncStatus.FAILED, line.getStockSyncStatus());
        assertTrue(line.getStockSyncError().contains("stock down"));

        UUID reservationId = UUID.randomUUID();
        org.mockito.Mockito.reset(stockClient);
        when(stockClient.isEnabled()).thenReturn(true);
        when(stockClient.reserve(any(), any(), any(), any(), any())).thenReturn(new StockReservation(reservationId, null, null, null, BigDecimal.ONE, "ACTIVE"));

        synchronizer.syncNow(line);

        assertEquals(StockSyncStatus.RESERVED, line.getStockSyncStatus());
        assertEquals(reservationId, line.getStockReservationId());
    }

    @Test
    void withoutAStockProjectTheConsumptionIsADirectOutput() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher());
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceMaterialUsage line = line(order);
        line.setConsumedQuantity(new BigDecimal("2"));

        synchronizer.consume(line);

        assertEquals(StockSyncStatus.CONSUMED, line.getStockSyncStatus());
        verify(stockClient).output(eq(line.getMaterialId()), eq(line.getWarehouseId()), isNull(), eq(new BigDecimal("2")), eq(order.getCode()), anyString(), any());
        verify(stockClient, never()).consume(any());
    }

    // ----------------------------------------------------------------- tasks

    @Test
    void preventiveTasksAreGeneratedOneProfileAtATimeAndNeverDuplicated() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        CatenaryAsset first = profile("12-2.27", "12847.990");
        CatenaryAsset second = profile("12-2.28", "12899.290");
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.assetRepository.findEnabledByTypeOnTrackBetween(CatenaryAssetType.PROFILE, 2L, order.getAsset().getStartKp(), order.getAsset().getEndKp()))
                .thenReturn(List.of(first, second));
        when(fixture.repository.findAssetIdsByOrderId(order.getId())).thenReturn(List.of(second.getId()));
        when(fixture.repository.findMaxSequence(order.getId())).thenReturn(3);
        when(fixture.lookups.taskTypes(MaintenanceTaskServiceImpl.DEFAULT_PREVENTIVE_TASK_TYPES)).thenReturn(taskTypes("RG-01", "RG-12"));
        when(fixture.workloadEstimator.estimate(order)).thenReturn(WorkloadEstimate.ofMinutes(new BigDecimal("540")));

        GeneratePreventiveTasksResponse response = fixture.service.generatePreventiveTasks(order.getId(), new GeneratePreventiveTasksRequest(null, null, null, null, null));

        assertEquals(1, response.createdTasks());
        assertEquals(1, response.skippedProfiles());
        assertEquals(2, response.estimatedShifts());
        MaintenanceTask created = fixture.savedTask();
        assertEquals(4, created.getSequence());
        assertEquals(first, created.getAsset());
        assertTrue(created.getDescription().contains("12-2.27"));
        assertEquals(2, created.getTaskTypes().size());
    }

    @Test
    void aGenerationTakesItsTurnInThisInstanceBeforeItsTransactionAndThePackageLockInsideIt() {
        AtomicBoolean inTurn = new AtomicBoolean();
        AtomicBoolean inTransaction = new AtomicBoolean();
        TransactionOperations transactions = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                assertTrue(inTurn.get(), "The transaction, and its connection, wait for the turn");
                inTransaction.set(true);
                try {
                    return TransactionOperations.withoutTransaction().execute(action);
                } finally {
                    inTransaction.set(false);
                }
            }
        };
        TaskFixture fixture = new TaskFixture(transactions);
        doAnswer(call -> {
            inTurn.set(true);
            try {
                return call.callRealMethod();
            } finally {
                inTurn.set(false);
            }
        }).when(fixture.generationLocks).withLock(anyString(), any());
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        CatenaryAsset first = profile("12-2.27", "12847.990");
        List<Boolean> orderReadsInTransaction = new ArrayList<>();
        when(fixture.lookups.order(order.getId())).thenAnswer(call -> {
            orderReadsInTransaction.add(inTransaction.get());
            return order;
        });
        when(fixture.assetRepository.findEnabledByTypeOnTrackBetween(CatenaryAssetType.PROFILE, 2L, order.getAsset().getStartKp(), order.getAsset().getEndKp()))
                .thenReturn(List.of(first));
        when(fixture.lookups.taskTypes(MaintenanceTaskServiceImpl.DEFAULT_PREVENTIVE_TASK_TYPES)).thenReturn(taskTypes("RG-01", "RG-12"));
        when(fixture.workloadEstimator.estimate(order)).thenReturn(WorkloadEstimate.ofMinutes(new BigDecimal("60")));
        long key = "mto-maintenance:generate-tasks:EP-6".hashCode();

        GeneratePreventiveTasksResponse response = fixture.service.generatePreventiveTasks(order.getId(), new GeneratePreventiveTasksRequest(null, null, null, null, null));

        assertEquals(1, response.createdTasks());
        verify(fixture.generationLocks).withLock(eq("EP-6"), any());
        // Entre instancias, el cerrojo de aviso del EP, antes de mirar que esta libre.
        InOrder inOrder = inOrder(fixture.repository);
        inOrder.verify(fixture.repository).advisoryTransactionLock(key);
        inOrder.verify(fixture.repository).findAssetIdsByOrderId(order.getId());
        // La orden se lee para saber su EP antes del turno, y otra vez dentro de la transaccion: como la dejo la anterior.
        assertEquals(List.of(false, true), orderReadsInTransaction);
        verify(fixture.repository, times(1)).save(any());
        assertEquals("EP-6", fixture.service.generationScope(order.getId()));
    }

    @Test
    void generationLocksQueueTheWorkOfOneScopeAndLetAnotherScopeThrough() throws Exception {
        GenerationLocks locks = new GenerationLocks();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean secondRan = new AtomicBoolean();
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> first = threads.submit(() -> locks.withLock("EP-6", () -> {
                holding.countDown();
                awaitLatch(release);
                return "first";
            }));
            assertTrue(holding.await(5, TimeUnit.SECONDS));
            Future<String> second = threads.submit(() -> locks.withLock("EP-6", () -> {
                secondRan.set(true);
                return "second";
            }));

            assertEquals("other", locks.withLock("EP-7", () -> "other"), "Another package does not wait");
            assertFalse(secondRan.get(), "The same package waits for the one that has the turn");
            assertFalse(second.isDone());

            release.countDown();
            assertEquals("first", first.get(5, TimeUnit.SECONDS));
            assertEquals("second", second.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void aGenerationInterruptedWhileWaitingForItsTurnFailsAndKeepsItsInterrupt() throws Exception {
        GenerationLocks locks = new GenerationLocks();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread owner = Thread.ofVirtual().start(() -> locks.withLock("EP-6", () -> {
            holding.countDown();
            awaitLatch(release);
            return null;
        }));
        assertTrue(holding.await(5, TimeUnit.SECONDS));
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        AtomicBoolean interruptKept = new AtomicBoolean();
        Thread waiting = Thread.ofVirtual().start(() -> {
            try {
                locks.withLock("EP-6", () -> fail("It never gets the turn"));
            } catch (RuntimeException expected) {
                failure.set(expected);
                interruptKept.set(Thread.currentThread().isInterrupted());
            }
        });

        waiting.interrupt();
        assertTrue(waiting.join(Duration.ofSeconds(5)));

        assertTrue(failure.get() instanceof IllegalStateException, () -> "Failed with " + failure.get());
        assertTrue(failure.get().getCause() instanceof InterruptedException);
        assertTrue(interruptKept.get(), "Whoever called it still sees the interrupt");
        release.countDown();
        assertTrue(owner.join(Duration.ofSeconds(5)), "The one with the turn is not disturbed");
        assertEquals("free", locks.withLock("EP-6", () -> "free"));
    }

    @Test
    void operationScopeLocksQueueTheWorkOfOneScopeAndLetAnotherScopeThrough() throws Exception {
        OperationScopeLocks locks = new OperationScopeLocks();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean secondRan = new AtomicBoolean();
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> first = threads.submit(() -> locks.withLock("EP-6", () -> {
                holding.countDown();
                awaitLatch(release);
                return "first";
            }));
            assertTrue(holding.await(5, TimeUnit.SECONDS));
            Future<String> second = threads.submit(() -> locks.withLock("EP-6", () -> {
                secondRan.set(true);
                return "second";
            }));

            assertEquals("other", locks.withLock("EP-7", () -> "other"), "Another package does not wait");
            assertFalse(secondRan.get(), "The same package waits for the operation that has the turn");
            assertFalse(second.isDone());

            release.countDown();
            assertEquals("first", first.get(5, TimeUnit.SECONDS));
            assertEquals("second", second.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void anOperationInterruptedWhileWaitingForItsScopeFailsAndKeepsItsInterrupt() throws Exception {
        OperationScopeLocks locks = new OperationScopeLocks();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread owner = Thread.ofVirtual().start(() -> locks.withLock("EP-6", () -> {
            holding.countDown();
            awaitLatch(release);
            return null;
        }));
        assertTrue(holding.await(5, TimeUnit.SECONDS));
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        AtomicBoolean interruptKept = new AtomicBoolean();
        Thread waiting = Thread.ofVirtual().start(() -> {
            try {
                locks.withLock("EP-6", () -> fail("It never gets the turn"));
            } catch (RuntimeException expected) {
                failure.set(expected);
                interruptKept.set(Thread.currentThread().isInterrupted());
            }
        });

        waiting.interrupt();
        assertTrue(waiting.join(Duration.ofSeconds(5)));

        assertTrue(failure.get() instanceof IllegalStateException, () -> "Failed with " + failure.get());
        assertTrue(failure.get().getCause() instanceof InterruptedException);
        assertTrue(interruptKept.get(), "Whoever called it still sees the interrupt");
        release.countDown();
        assertTrue(owner.join(Duration.ofSeconds(5)), "The one with the turn is not disturbed");
        assertEquals("free", locks.withLock("EP-6", () -> "free"));
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    @Test
    void theInsulatorsOfTheSectionAreGeneratedWithTheirRevisionsBetweenTheProfilesAroundThemWhenAsked() {
        // Un fin de semana con posesion total: los perfiles de la via y los aisladores que la tocan,
        // tambien el de la via de al lado que conecta con esta, en el orden en que se recorren. El que
        // tiene agujas lleva tambien las de sus agujas (RG-04 y RP-03); el otro, solo las del aislador.
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        CatenaryAsset first = profile("12-2.27", "12847.990");
        CatenaryAsset second = profile("12-2.28", "12899.290");
        CatenaryAsset own = withSwitch(insulator("SI-W31", 2L, null, "12850.000", "12890.000"), "W31");
        CatenaryAsset fromNextTrack = insulator("SI-W41", 3L, 2L, "12920.000", "12960.000");
        BigDecimal from = order.getAsset().getStartKp();
        BigDecimal to = order.getAsset().getEndKp();
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.assetRepository.findEnabledByTypeOnTrackBetween(CatenaryAssetType.PROFILE, 2L, from, to)).thenReturn(List.of(first, second));
        when(fixture.assetRepository.findEnabledByTypeTouchingSection(CatenaryAssetType.SECTION_INSULATOR, 2L, from, to))
                .thenReturn(List.of(own, fromNextTrack));
        when(fixture.repository.findAssetIdsByOrderId(order.getId())).thenReturn(List.of());
        when(fixture.repository.findMaxSequence(order.getId())).thenReturn(0);
        when(fixture.lookups.taskTypes(MaintenanceTaskServiceImpl.DEFAULT_PREVENTIVE_TASK_TYPES)).thenReturn(taskTypes("RG-01", "RG-12"));
        when(fixture.lookups.taskTypes(MaintenanceTaskServiceImpl.DEFAULT_INSULATOR_TASK_TYPES)).thenReturn(fullPossessionTypesOf("RG-03", "RP-02"));
        when(fixture.lookups.taskTypes(MaintenanceTaskServiceImpl.DEFAULT_INSULATOR_WITH_SWITCHES_TASK_TYPES))
                .thenReturn(fullPossessionTypesOf("RG-03", "RG-04", "RP-02", "RP-03"));
        when(fixture.lookups.activeTemplate(any())).thenReturn(Optional.of(template("RUNNERS", "BODY")));
        when(fixture.workloadEstimator.estimate(order)).thenReturn(WorkloadEstimate.ofMinutes(new BigDecimal("300")));

        GeneratePreventiveTasksResponse response = fixture.service.generatePreventiveTasks(order.getId(), new GeneratePreventiveTasksRequest(null, true,
                List.of(CatenaryAssetType.PROFILE, CatenaryAssetType.SECTION_INSULATOR), null, null));

        assertEquals(4, response.createdTasks());
        assertEquals(0, response.skippedAssets());
        ArgumentCaptor<MaintenanceTask> saved = ArgumentCaptor.forClass(MaintenanceTask.class);
        verify(fixture.repository, times(4)).save(saved.capture());
        List<MaintenanceTask> tasks = saved.getAllValues();
        assertEquals(List.of(first, own, second, fromNextTrack), tasks.stream().map(MaintenanceTask::getAsset).toList(), "By kp along the track");
        assertEquals(List.of(1, 2, 3, 4), tasks.stream().map(MaintenanceTask::getSequence).toList());
        assertEquals(Set.of("RG-03", "RG-04", "RP-02", "RP-03"), codesOf(tasks.get(1)),
                "An insulator with turnouts gets its own revisions and those of its turnouts, not the profile's types");
        assertEquals(Set.of("RG-03", "RP-02"), codesOf(tasks.get(3)), "One without turnouts, only its own");
        assertTrue(tasks.get(1).requiresFullPossession(), "So it waits for a FULL shift");
        assertEquals(Set.of("RG-01", "RG-12"), codesOf(tasks.get(0)));
        assertEquals("Preventive maintenance of section insulator SI-W41 (kp 12920.000)", tasks.get(3).getDescription());
        assertEquals(2, tasks.get(3).getCheckItems().size(), "Its checklist is the template of its own type");
        verify(fixture.lookups).activeTemplate(fromNextTrack);
    }

    @Test
    void generatingAgainSkipsWhatAlreadyHasATaskAndChosenTypesGoToEverything() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        CatenaryAsset profile = profile("12-2.27", "12847.990");
        CatenaryAsset covered = insulator("SI-W31", 2L, null, "12850.000", "12890.000");
        CatenaryAsset fresh = insulator("SI-W41", 2L, 3L, "12920.000", "12960.000");
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.assetRepository.findEnabledByTypeTouchingSection(eq(CatenaryAssetType.SECTION_INSULATOR), eq(2L), any(), any()))
                .thenReturn(List.of(covered, fresh));
        when(fixture.repository.findAssetIdsByOrderId(order.getId())).thenReturn(List.of(profile.getId(), covered.getId()));
        when(fixture.repository.findMaxSequence(order.getId())).thenReturn(7);
        when(fixture.lookups.taskTypes(List.of("RG-03", "RG-04"))).thenReturn(taskTypes("RG-03", "RG-04"));
        when(fixture.workloadEstimator.estimate(order)).thenReturn(WorkloadEstimate.ofMinutes(new BigDecimal("60")));

        // Solo aisladores, con los tipos elegidos: el perfil ni se pide.
        GeneratePreventiveTasksResponse response = fixture.service.generatePreventiveTasks(order.getId(),
                new GeneratePreventiveTasksRequest(List.of("RG-03", "RG-04"), null, List.of(CatenaryAssetType.SECTION_INSULATOR), null, null));

        assertEquals(1, response.createdTasks());
        assertEquals(1, response.skippedAssets());
        assertEquals(0, response.skippedProfiles(), "Only profiles count there, as before");
        MaintenanceTask created = fixture.savedTask();
        assertEquals(fresh, created.getAsset());
        assertEquals(8, created.getSequence());
        assertEquals(Set.of("RG-03", "RG-04"), codesOf(created));
        verify(fixture.assetRepository, never()).findEnabledByTypeOnTrackBetween(any(), any(), any(), any());
        verify(fixture.lookups, never()).taskTypes(MaintenanceTaskServiceImpl.DEFAULT_INSULATOR_TASK_TYPES);
    }

    @Test
    void aTrackSectionIsNeverGeneratedOneByOne() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        when(fixture.lookups.order(order.getId())).thenReturn(order);

        ValidationException rejected = assertThrows(ValidationException.class, () -> fixture.service.generatePreventiveTasks(order.getId(),
                new GeneratePreventiveTasksRequest(null, null, List.of(CatenaryAssetType.PROFILE, CatenaryAssetType.TRACK_SECTION), null, null)));

        assertTrue(rejected.getMessage().startsWith("assetTypes"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("TRACK_SECTION"), rejected.getMessage());
        verify(fixture.repository, never()).save(any());
    }

    @Test
    void everyDisconnectorGetsRp07AndAMotorOneAlsoRp06AndASurgeArresterAlsoRg06() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        CatenaryAsset motor = disconnectorAt("HSA-NS5", 2L, "12900.000", DisconnectorDriveType.MOTOR);
        CatenaryAsset manual = disconnectorAt("HSA-NS6", 2L, "13100.000", DisconnectorDriveType.MANUAL);
        CatenaryAsset unknown = disconnectorAt("HSA-NS7", 2L, "13200.000", null);
        CatenaryAsset valve = disconnectorAt("HSA-SA8", 2L, "13300.000", DisconnectorDriveType.MANUAL);
        valve.setDisconnectorFunction("SurgeA");
        CatenaryAsset motorValve = disconnectorAt("HSA-SA9", 2L, "13400.000", DisconnectorDriveType.MOTOR);
        motorValve.setDisconnectorFunction("surgea");
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.assetRepository.findEnabledByTypeTouchingSection(eq(CatenaryAssetType.DISCONNECTOR), eq(2L), any(), any()))
                .thenReturn(List.of(motor, manual, unknown, valve, motorValve));
        when(fixture.lookups.taskTypes(any())).thenAnswer(call -> {
            Collection<String> codes = call.getArgument(0);
            return fullPossessionTypesOf(codes.toArray(String[]::new));
        });
        when(fixture.workloadEstimator.estimate(order)).thenReturn(WorkloadEstimate.ofMinutes(new BigDecimal("150")));

        GeneratePreventiveTasksResponse response = fixture.service.generatePreventiveTasks(order.getId(),
                new GeneratePreventiveTasksRequest(null, null, List.of(CatenaryAssetType.DISCONNECTOR), null, null));

        assertEquals(5, response.createdTasks());
        assertEquals(0, response.skippedWithoutTaskTypes(), "No disconnector is left without a type any more");
        assertEquals(Map.of(CatenaryAssetType.DISCONNECTOR, 5), response.createdByAssetType());
        ArgumentCaptor<MaintenanceTask> saved = ArgumentCaptor.forClass(MaintenanceTask.class);
        verify(fixture.repository, times(5)).save(saved.capture());
        List<MaintenanceTask> tasks = saved.getAllValues();
        assertEquals(List.of(motor, manual, unknown, valve, motorValve), tasks.stream().map(MaintenanceTask::getAsset).toList());
        assertEquals(Set.of("RP-06", "RP-07"), codesOf(tasks.get(0)), "With a motor, its remote drive test too");
        assertEquals(Set.of("RP-07"), codesOf(tasks.get(1)), "A manual one, the disconnector itself");
        assertEquals(Set.of("RP-07"), codesOf(tasks.get(2)), "Without the data, the same as a manual one");
        assertEquals(Set.of("RG-06", "RP-07"), codesOf(tasks.get(3)), "A surge arrester, also RG-06");
        assertEquals(Set.of("RG-06", "RP-06", "RP-07"), codesOf(tasks.get(4)), "Its code is compared ignoring case");
        verify(fixture.lookups).taskTypes(List.of("RG-06", "RP-06", "RP-07"));
        assertEquals("Preventive maintenance of disconnector HSA-NS5 (kp 12900.000)", tasks.get(0).getDescription());
        assertTrue(tasks.stream().allMatch(MaintenanceTask::requiresFullPossession), "RP-07 waits for a FULL shift");
    }

    @Test
    void anOrderMixesProfilesOfAnotherSectionInsulatorsOfAnotherTrackAndTheDisconnectorsOfItsPackage() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        CatenaryAsset otherSection = sectionOn(3L, 6L, TrackKind.MAIN);
        CatenaryAsset farProfile = profileOn("3-1.02", 3L, "13500.000");
        CatenaryAsset nearProfile = profileOn("3-1.01", 3L, "13000.000");
        CatenaryAsset onTrack13 = insulator("SI-W51", 13L, null, "20100.000", "20150.000");
        CatenaryAsset connectingTo13 = insulator("SI-W52", 12L, 13L, "20000.000", "20040.000");
        CatenaryAsset motor = disconnectorAt("HSA-NS5", 2L, "12900.000", DisconnectorDriveType.MOTOR);
        CatenaryAsset manual = disconnectorAt("HSA-NS6", 2L, "13100.000", DisconnectorDriveType.MANUAL);
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.assetRepository.findById(otherSection.getId())).thenReturn(Optional.of(otherSection));
        when(fixture.assetRepository.findEnabledByTypeOnTrackBetween(CatenaryAssetType.PROFILE, 3L, otherSection.getStartKp(), otherSection.getEndKp()))
                .thenReturn(List.of(nearProfile, farProfile));
        when(fixture.assetRepository.findEnabledForGeneration(CatenaryAssetType.SECTION_INSULATOR, 13L, null, null, null, null))
                .thenReturn(List.of(connectingTo13, onTrack13));
        when(fixture.assetRepository.findEnabledForGeneration(CatenaryAssetType.DISCONNECTOR, null, null, null, null, 6L))
                .thenReturn(List.of(motor, manual));
        when(fixture.repository.findMaxSequence(order.getId())).thenReturn(0);
        when(fixture.lookups.taskTypes(MaintenanceTaskServiceImpl.DEFAULT_PREVENTIVE_TASK_TYPES)).thenReturn(taskTypes("RG-01", "RG-12"));
        when(fixture.lookups.taskTypes(MaintenanceTaskServiceImpl.DEFAULT_INSULATOR_TASK_TYPES)).thenReturn(fullPossessionTypesOf("RG-03", "RP-02"));
        when(fixture.lookups.taskTypes(MaintenanceTaskServiceImpl.DEFAULT_MOTOR_DISCONNECTOR_TASK_TYPES)).thenReturn(fullPossessionTypes("RP-06"));
        when(fixture.lookups.taskTypes(MaintenanceTaskServiceImpl.DEFAULT_DISCONNECTOR_TASK_TYPES)).thenReturn(fullPossessionTypes("RP-07"));
        when(fixture.lookups.activeTemplate(any())).thenReturn(Optional.of(template("RUNNERS", "BODY")));
        when(fixture.workloadEstimator.estimate(order)).thenReturn(WorkloadEstimate.ofMinutes(new BigDecimal("600")));

        GeneratePreventiveTasksResponse response = fixture.service.generatePreventiveTasks(order.getId(), new GeneratePreventiveTasksRequest(
                null, true, null, List.of(
                        selection(CatenaryAssetType.PROFILE).sectionId(otherSection.getId()).build(),
                        selection(CatenaryAssetType.SECTION_INSULATOR).trackId(13L).build(),
                        selection(CatenaryAssetType.DISCONNECTOR).build()), null));

        assertEquals(6, response.createdTasks());
        assertEquals(Map.of(CatenaryAssetType.PROFILE, 2, CatenaryAssetType.SECTION_INSULATOR, 2, CatenaryAssetType.DISCONNECTOR, 2),
                response.createdByAssetType());
        assertEquals(0, response.skippedWithoutTaskTypes(), "The manual disconnector gets RP-07");
        assertFalse(response.dryRun());
        assertEquals(List.of(), response.assets(), "The lines come only with a preview");
        ArgumentCaptor<MaintenanceTask> saved = ArgumentCaptor.forClass(MaintenanceTask.class);
        verify(fixture.repository, times(6)).save(saved.capture());
        List<MaintenanceTask> tasks = saved.getAllValues();
        assertEquals(List.of(nearProfile, farProfile, connectingTo13, onTrack13, motor, manual), tasks.stream().map(MaintenanceTask::getAsset).toList(),
                "Track by track in the order asked for, by kp inside: the insulator of track 12 that connects goes with track 13");
        assertEquals(List.of(1, 2, 3, 4, 5, 6), tasks.stream().map(MaintenanceTask::getSequence).toList());
        assertEquals(Set.of("RG-01", "RG-12"), codesOf(tasks.get(0)));
        assertEquals(Set.of("RG-03", "RP-02"), codesOf(tasks.get(2)));
        assertEquals(Set.of("RP-06"), codesOf(tasks.get(4)));
        assertEquals(Set.of("RP-07"), codesOf(tasks.get(5)));
        assertTrue(tasks.stream().allMatch(task -> task.getCheckItems().size() == 2), "Each with the checklist of its type");
        verify(fixture.lookups).activeTemplate(motor);
        assertTrue(tasks.stream().allMatch(task -> task.getOrder() == order));
    }

    @Test
    void anOrderOnAnInsulatorGeneratesOnlyInsulatorsWithSelectionsAndSelectionsNeedAPackage() {
        TaskFixture fixture = new TaskFixture();
        CatenaryAsset anchor = insulator("SI-W31", 2L, 3L, "12850.000", "12890.000");
        MaintenanceOrder order = orderOn(anchor);
        CatenaryAsset another = insulator("SI-W41", 3L, null, "12920.000", "12960.000");
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.assetRepository.findEnabledForGeneration(CatenaryAssetType.SECTION_INSULATOR, null, null, null, null, 6L))
                .thenReturn(List.of(anchor, another));
        when(fixture.lookups.taskTypes(MaintenanceTaskServiceImpl.DEFAULT_INSULATOR_TASK_TYPES)).thenReturn(fullPossessionTypesOf("RG-03", "RP-02"));
        when(fixture.workloadEstimator.estimate(order)).thenReturn(WorkloadEstimate.ofMinutes(new BigDecimal("60")));

        GeneratePreventiveTasksResponse response = fixture.service.generatePreventiveTasks(order.getId(), new GeneratePreventiveTasksRequest(
                null, null, null, List.of(selection(CatenaryAssetType.SECTION_INSULATOR).build()), null));

        assertEquals(2, response.createdTasks());
        assertEquals(Map.of(CatenaryAssetType.SECTION_INSULATOR, 2), response.createdByAssetType());
        assertThrows(InvalidTransitionException.class, () -> fixture.service.generatePreventiveTasks(order.getId(),
                new GeneratePreventiveTasksRequest(null, null, null, null, null)), "Without selections it is still the order's section");

        MaintenanceOrder withoutPackage = orderOn(insulator("SI-W99", 9L, null, "100.000", "120.000"));
        withoutPackage.setExecutionPackageId(null);
        when(fixture.lookups.order(withoutPackage.getId())).thenReturn(withoutPackage);
        ValidationException rejected = assertThrows(ValidationException.class, () -> fixture.service.generatePreventiveTasks(withoutPackage.getId(),
                new GeneratePreventiveTasksRequest(null, null, null, List.of(selection(CatenaryAssetType.SECTION_INSULATOR).build()), null)));
        assertTrue(rejected.getMessage().startsWith("selections:"), rejected.getMessage());
    }

    @Test
    void whatIsAlreadyInTheOrderInTwoSelectionsInAnotherOpenOrderOrInAnotherPackageIsNeverGeneratedTwice() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        CatenaryAsset covered = profileOn("2-1.01", 2L, "12900.000");
        CatenaryAsset twice = profileOn("2-1.02", 2L, "13000.000");
        CatenaryAsset busy = profileOn("2-1.03", 2L, "13100.000");
        CatenaryAsset foreign = profileOn("2-1.04", 2L, "13200.000");
        foreign.setExecutionPackageId(7L);
        CatenaryAsset unknownPackage = profileOn("2-1.05", 2L, "13300.000");
        unknownPackage.setExecutionPackageId(null);
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.assetRepository.findEnabledForGeneration(CatenaryAssetType.PROFILE, 2L, null, null, null, null))
                .thenReturn(List.of(covered, twice, busy, foreign, unknownPackage));
        when(fixture.assetRepository.findAllById(List.of(twice.getId()))).thenReturn(List.of(twice));
        when(fixture.repository.findAssetIdsByOrderId(order.getId())).thenReturn(List.of(covered.getId()));
        when(fixture.repository.findOpenPreventiveTasksElsewhere(List.of(twice.getId(), busy.getId(), unknownPackage.getId()), order.getId()))
                .thenReturn(List.of(inOtherOrder(busy.getId(), "MO-000042")));
        when(fixture.lookups.taskTypes(List.of("RG-11"))).thenReturn(taskTypes("RG-11"));
        when(fixture.lookups.taskTypes(List.of("RG-01"))).thenReturn(taskTypes("RG-01"));
        when(fixture.workloadEstimator.estimate(order)).thenReturn(WorkloadEstimate.ofMinutes(new BigDecimal("30")));

        GeneratePreventiveTasksResponse response = fixture.service.generatePreventiveTasks(order.getId(), new GeneratePreventiveTasksRequest(
                List.of("RG-01"), null, null, List.of(
                        selection(CatenaryAssetType.PROFILE).assetIds(List.of(twice.getId())).taskTypeCodes(List.of("RG-11")).build(),
                        selection(CatenaryAssetType.PROFILE).trackId(2L).taskTypeCodes(List.of()).build()), null));

        assertEquals(2, response.createdTasks(), "The one in two selections once, and the one without a package");
        assertEquals(1, response.skippedAssets());
        assertEquals(1, response.skippedProfiles());
        assertEquals(1, response.skippedInOtherOrders());
        assertEquals(List.of("MO-000042"), response.otherOrderCodes());
        assertEquals(1, response.skippedOtherPackage());
        ArgumentCaptor<MaintenanceTask> saved = ArgumentCaptor.forClass(MaintenanceTask.class);
        verify(fixture.repository, times(2)).save(saved.capture());
        assertEquals(List.of(twice, unknownPackage), saved.getAllValues().stream().map(MaintenanceTask::getAsset).toList());
        assertEquals(Set.of("RG-11"), codesOf(saved.getAllValues().get(0)), "The first selection that finds it decides, with its own types");
        assertEquals(Set.of("RG-01"), codesOf(saved.getAllValues().get(1)), "An empty list in a selection leaves the request's");
    }

    @Test
    void theSelectionsAreCheckedBeforeAnythingIsGenerated() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        CatenaryAsset foreignSection = sectionOn(4L, 7L, TrackKind.MAIN);
        CatenaryAsset diverted = sectionOn(5L, 6L, TrackKind.DIVERTED);
        CatenaryAsset notASection = profileOn("2-1.01", 2L, "12900.000");
        CatenaryAsset foreign = profileOn("2-1.02", 2L, "13000.000");
        foreign.setExecutionPackageId(7L);
        CatenaryAsset off = profileOn("2-1.03", 2L, "13100.000");
        off.disableLocally();
        UUID missing = UUID.randomUUID();
        when(fixture.assetRepository.findById(foreignSection.getId())).thenReturn(Optional.of(foreignSection));
        when(fixture.assetRepository.findById(diverted.getId())).thenReturn(Optional.of(diverted));
        when(fixture.assetRepository.findById(notASection.getId())).thenReturn(Optional.of(notASection));
        when(fixture.assetRepository.findAllById(any())).thenAnswer(invocation -> {
            List<UUID> ids = invocation.getArgument(0);
            return Stream.of(notASection, foreign, off).filter(asset -> ids.contains(asset.getId())).toList();
        });

        Map<String, GenerationSelection> rejected = new LinkedHashMap<>();
        rejected.put("selections[0].assetType", selection(CatenaryAssetType.TRACK_SECTION).build());
        rejected.put("selections[0]: one of", selection(CatenaryAssetType.PROFILE).trackId(2L).sectionId(foreignSection.getId()).build());
        rejected.put("selections[0].fromKp: a kp range", selection(CatenaryAssetType.PROFILE).fromKp(BigDecimal.ONE).build());
        rejected.put("selections[0].fromKp: 2", selection(CatenaryAssetType.PROFILE).trackId(2L).fromKp(BigDecimal.TWO).toKp(BigDecimal.ONE).build());
        rejected.put("selections[0].stationId", selection(CatenaryAssetType.PROFILE).assetIds(List.of(notASection.getId())).stationId(4L).build());
        rejected.put("selections[0].sectionId: " + foreignSection.getCode() + " is not in the execution package",
                selection(CatenaryAssetType.PROFILE).sectionId(foreignSection.getId()).build());
        rejected.put("selections[0].sectionId: " + diverted.getCode() + " is a DIVERTED track",
                selection(CatenaryAssetType.PROFILE).sectionId(diverted.getId()).build());
        rejected.put("selections[0].sectionId: " + notASection.getCode() + " is a PROFILE",
                selection(CatenaryAssetType.PROFILE).sectionId(notASection.getId()).build());
        rejected.put("selections[0].sectionId: no asset", selection(CatenaryAssetType.PROFILE).sectionId(missing).build());
        rejected.put("selections[0].assetIds: no assets", selection(CatenaryAssetType.PROFILE).assetIds(List.of(missing)).build());
        rejected.put("selections[0].assetIds: " + notASection.getCode() + " are not DISCONNECTOR",
                selection(CatenaryAssetType.DISCONNECTOR).assetIds(List.of(notASection.getId())).build());
        rejected.put("selections[0].assetIds: " + off.getCode() + " are disabled", selection(CatenaryAssetType.PROFILE).assetIds(List.of(off.getId())).build());
        rejected.put("selections[0].assetIds: " + foreign.getCode() + " are not in the execution package",
                selection(CatenaryAssetType.PROFILE).assetIds(List.of(foreign.getId())).build());

        rejected.forEach((message, selection) -> {
            ValidationException exception = assertThrows(ValidationException.class, () -> fixture.service.generatePreventiveTasks(order.getId(),
                    new GeneratePreventiveTasksRequest(null, null, null, List.of(selection), null)), message);
            assertTrue(exception.getMessage().startsWith(message), exception.getMessage());
        });
        ValidationException together = assertThrows(ValidationException.class, () -> fixture.service.generatePreventiveTasks(order.getId(),
                new GeneratePreventiveTasksRequest(null, null, List.of(CatenaryAssetType.PROFILE),
                        List.of(selection(CatenaryAssetType.PROFILE).build()), null)));
        assertTrue(together.getMessage().startsWith("assetTypes:"), together.getMessage());
        verify(fixture.repository, never()).save(any());
    }

    @Test
    void aPreviewCreatesNothingTakesNoLockAndSaysWhatWouldHappenToEachAsset() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        order.getTasks().add(task(order, MaintenanceTaskStatus.PENDING));
        CatenaryAsset fresh = profileOn("2-1.01", 2L, "12900.000");
        CatenaryAsset busy = profileOn("2-1.02", 2L, "13000.000");
        CatenaryAsset manual = disconnectorAt("HSA-NS6", 2L, "13100.000", DisconnectorDriveType.MANUAL);
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.assetRepository.findEnabledForGeneration(CatenaryAssetType.PROFILE, 2L, null, null, null, null)).thenReturn(List.of(fresh, busy));
        when(fixture.assetRepository.findEnabledForGeneration(CatenaryAssetType.DISCONNECTOR, 2L, null, null, null, null)).thenReturn(List.of(manual));
        when(fixture.repository.findOpenPreventiveTasksElsewhere(any(), eq(order.getId()))).thenReturn(List.of(inOtherOrder(busy.getId(), "MO-000042")));
        when(fixture.lookups.taskTypes(MaintenanceTaskServiceImpl.DEFAULT_PREVENTIVE_TASK_TYPES)).thenReturn(taskTypes("RG-12", "RG-01"));
        when(fixture.lookups.taskTypes(MaintenanceTaskServiceImpl.DEFAULT_DISCONNECTOR_TASK_TYPES)).thenReturn(fullPossessionTypes("RP-07"));
        when(fixture.workloadEstimator.estimate(eq(order), any())).thenReturn(WorkloadEstimate.ofMinutes(new BigDecimal("25")));

        GeneratePreventiveTasksResponse response = fixture.service.generatePreventiveTasks(order.getId(), new GeneratePreventiveTasksRequest(
                null, null, null, List.of(selection(CatenaryAssetType.PROFILE).trackId(2L).build(),
                selection(CatenaryAssetType.DISCONNECTOR).trackId(2L).build()), true));

        assertTrue(response.dryRun());
        assertEquals(2, response.createdTasks(), "What would be created");
        assertEquals(3, response.totalTasks(), "The one there plus the two that would be created");
        assertEquals(List.of(GenerationOutcome.CREATE, GenerationOutcome.IN_OTHER_ORDER, GenerationOutcome.CREATE),
                response.assets().stream().map(GenerationPreviewLine::outcome).toList());
        GenerationPreviewLine first = response.assets().getFirst();
        assertEquals(List.of("RG-01", "RG-12"), first.taskTypeCodes());
        assertEquals(new BigDecimal("12900.000"), first.kp());
        assertEquals("MO-000042", response.assets().get(1).otherOrderCode());
        assertEquals(List.of("RP-07"), response.assets().get(2).taskTypeCodes(), "A manual disconnector is no longer skipped");
        ArgumentCaptor<Collection<MaintenanceTask>> planned = ArgumentCaptor.captor();
        verify(fixture.workloadEstimator).estimate(eq(order), planned.capture());
        assertEquals(List.of(fresh, manual), planned.getValue().stream().map(MaintenanceTask::getAsset).toList());
        verify(fixture.repository, never()).save(any());
        verify(fixture.repository, never()).advisoryTransactionLock(anyLong());
        verify(fixture.generationLocks, never()).withLock(anyString(), any());
        assertEquals(1, order.getTasks().size(), "The order is not touched");
    }

    @Test
    void generatingAndAddingATaskByHandQueueBehindTheSameLockOfThePackage() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.workloadEstimator.estimate(order)).thenReturn(WorkloadEstimate.ofMinutes(BigDecimal.ZERO));
        long packageLock = "mto-maintenance:generate-tasks:EP-6".hashCode();

        fixture.service.generatePreventiveTasks(order.getId(), new GeneratePreventiveTasksRequest(null, null, null, null, null));
        InOrder inOrder = inOrder(fixture.repository);
        inOrder.verify(fixture.repository).advisoryTransactionLock(packageLock);
        inOrder.verify(fixture.repository).findAssetIdsByOrderId(order.getId());

        // A mano es corto: espera al cerrojo del EP dentro de su transaccion, el mismo que la generacion.
        fixture.service.create(order.getId(), new MaintenanceTaskRequest("By hand", null, null, null, null));
        verify(fixture.repository, times(2)).advisoryTransactionLock(packageLock);

        MaintenanceOrder withoutPackage = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        withoutPackage.setExecutionPackageId(null);
        when(fixture.lookups.order(withoutPackage.getId())).thenReturn(withoutPackage);
        when(fixture.workloadEstimator.estimate(withoutPackage)).thenReturn(WorkloadEstimate.ofMinutes(BigDecimal.ZERO));
        fixture.service.generatePreventiveTasks(withoutPackage.getId(), new GeneratePreventiveTasksRequest(null, null, null, null, null));
        verify(fixture.generationLocks).withLock(eq("order-" + withoutPackage.getId()), any());
        verify(fixture.repository).advisoryTransactionLock(("mto-maintenance:generate-tasks:order-" + withoutPackage.getId()).hashCode());
    }

    @Test
    void aGeneratedTaskOnAnotherTrackOfThePackageIsWorkedFromAShiftOfItsOwnTrack() {
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceTask task = task(order, MaintenanceTaskStatus.PENDING);
        task.setAsset(disconnectorAt("HSA-NS5", 5L, "12900.000", DisconnectorDriveType.MOTOR));

        assertDoesNotThrow(() -> ShiftRules.requireSameTrack(shift(5L, PossessionType.FULL, ShiftStatus.IN_PROGRESS), task));
        assertThrows(ShiftException.class, () -> ShiftRules.requireSameTrack(shift(2L, PossessionType.FULL, ShiftStatus.IN_PROGRESS), task),
                "Not from the track of the order's section");
    }

    @Test
    void generatingTasksNeedsAPreventiveOrderOnATrackSection() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder corrective = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.DRAFT);
        when(fixture.lookups.order(corrective.getId())).thenReturn(corrective);
        assertThrows(InvalidTransitionException.class, () -> fixture.service.generatePreventiveTasks(corrective.getId(), new GeneratePreventiveTasksRequest(null, null, null, null, null)));

        MaintenanceOrder inProgress = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        when(fixture.lookups.order(inProgress.getId())).thenReturn(inProgress);
        assertThrows(InvalidTransitionException.class, () -> fixture.service.generatePreventiveTasks(inProgress.getId(), new GeneratePreventiveTasksRequest(null, null, null, null, null)));
    }

    @Test
    void tasksOnlyRunInsideAShiftInProgressOnTheSameTrackWithACompatiblePossession() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceTask task = task(order, MaintenanceTaskStatus.PENDING);
        task.setAsset(profile("12-2.27", "12847.990"));
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.repository.findByIdAndOrderId(task.getId(), order.getId())).thenReturn(Optional.of(task));

        MaintenanceShift planned = shift(2L, PossessionType.PARTIAL, ShiftStatus.PLANNED);
        when(fixture.lookups.shift(planned.getId())).thenReturn(planned);
        assertThrows(ShiftException.class, () -> fixture.service.complete(order.getId(), task.getId(), complete(planned.getId(), List.of("RG-01"))));

        MaintenanceShift otherTrack = shift(1L, PossessionType.FULL, ShiftStatus.IN_PROGRESS);
        when(fixture.lookups.shift(otherTrack.getId())).thenReturn(otherTrack);
        assertThrows(ShiftException.class, () -> fixture.service.complete(order.getId(), task.getId(), complete(otherTrack.getId(), List.of("RG-01"))));

        // Un turno que recorre las vias 1 y 2 si admite el perfil de la via 2.
        MaintenanceShift twoTracks = shift(Set.of(1L, 2L), PossessionType.FULL, ShiftStatus.IN_PROGRESS);
        when(fixture.lookups.shift(twoTracks.getId())).thenReturn(twoTracks);
        when(fixture.lookups.taskTypes(List.of("RG-01"))).thenReturn(taskTypes("RG-01"));
        MaintenanceTask onTwoTracks = task(order, MaintenanceTaskStatus.PENDING);
        onTwoTracks.setAsset(profile("12-2.28", "12900.000"));
        when(fixture.repository.findByIdAndOrderId(onTwoTracks.getId(), order.getId())).thenReturn(Optional.of(onTwoTracks));
        fixture.service.complete(order.getId(), onTwoTracks.getId(), complete(twoTracks.getId(), List.of("RG-01")));
        assertEquals(MaintenanceTaskStatus.COMPLETED, onTwoTracks.getStatus());

        MaintenanceShift partial = shift(2L, PossessionType.PARTIAL, ShiftStatus.IN_PROGRESS);
        when(fixture.lookups.shift(partial.getId())).thenReturn(partial);
        when(fixture.lookups.taskTypes(List.of("RG-03"))).thenReturn(fullPossessionTypes());
        assertThrows(ShiftException.class, () -> fixture.service.complete(order.getId(), task.getId(), complete(partial.getId(), List.of("RG-03"))));
        assertEquals(MaintenanceTaskStatus.PENDING, task.getStatus());
    }

    @Test
    void tasksCannotBeCompletedWhileTheOrderIsNotInProgress() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        MaintenanceTask task = task(order, MaintenanceTaskStatus.PENDING);
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.repository.findByIdAndOrderId(task.getId(), order.getId())).thenReturn(Optional.of(task));

        assertThrows(InvalidTransitionException.class, () -> fixture.service.complete(order.getId(), task.getId(), complete(UUID.randomUUID(), List.of())));
    }

    @Test
    void completingATaskRecordsInlineDefectsResolvedOrPendingWithARepairDate() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceTask task = task(order, MaintenanceTaskStatus.PENDING);
        task.setAsset(profile("13-2.01", "13007.290"));
        MaintenanceShift shift = shift(2L, PossessionType.PARTIAL, ShiftStatus.IN_PROGRESS);
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.repository.findByIdAndOrderId(task.getId(), order.getId())).thenReturn(Optional.of(task));
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        when(fixture.codeGenerator.nextDefectCode()).thenReturn("DEF-000007");
        when(fixture.defectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.complete(order.getId(), task.getId(), new CompleteTaskRequest(shift.getId(), List.of(), "Reattach dropper loop",
                "Damaged dropper", true, null, List.of(new InlineDefectRequest(DefectSeverity.MEDIUM, "Damaged dropper", null, "Dropper replaced", "1 dropper")),
                null, List.of("13-2.01 dropper.jpg")));

        assertEquals(MaintenanceTaskStatus.COMPLETED, task.getStatus());
        assertEquals(shift, task.getShift());
        assertNotNull(task.getCompletedAt());
        ArgumentCaptor<CatenaryDefect> captor = ArgumentCaptor.forClass(CatenaryDefect.class);
        verify(fixture.defectRepository).save(captor.capture());
        CatenaryDefect resolved = captor.getValue();
        assertEquals(DefectStatus.RESOLVED, resolved.getStatus());
        assertEquals(shift, resolved.getResolvedInShift());
        assertEquals(task, resolved.getFoundInTask());
        assertEquals("Dropper replaced", resolved.getCorrectionType());
        assertEquals(List.of("13-2.01 dropper.jpg"), resolved.getPhotoRefs());

        MaintenanceTask pendingRepair = task(order, MaintenanceTaskStatus.PENDING);
        when(fixture.repository.findByIdAndOrderId(pendingRepair.getId(), order.getId())).thenReturn(Optional.of(pendingRepair));
        fixture.service.complete(order.getId(), pendingRepair.getId(), new CompleteTaskRequest(shift.getId(), List.of(), null, "Bird nest", false,
                LocalDate.of(2026, 2, 3), List.of(new InlineDefectRequest(DefectSeverity.LOW, "Bird nest", null, null, null)), null, null));

        verify(fixture.defectRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        CatenaryDefect open = captor.getValue();
        assertEquals(DefectStatus.OPEN, open.getStatus());
        assertEquals(LocalDate.of(2026, 2, 3), open.getRepairPlannedDate());
    }

    // ----------------------------------------------------------- inspections

    @Test
    void aMajorDefectInspectionGeneratesADefectOnceAndAnOkOneGeneratesNothing() {
        InspectionFixture fixture = new InspectionFixture();
        MaintenanceInspection inspection = inspection(InspectionResult.MAJOR_DEFECT);
        when(fixture.repository.findById(inspection.getId())).thenReturn(Optional.of(inspection));
        when(fixture.codeGenerator.nextDefectCode()).thenReturn("DEF-000001");
        when(fixture.defectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.createDefect(inspection.getId(), new CreateDefectFromInspectionRequest(null, null, null, null));
        fixture.service.createDefect(inspection.getId(), new CreateDefectFromInspectionRequest(null, null, null, null));

        verify(fixture.defectRepository, org.mockito.Mockito.times(1)).save(any());
        CatenaryDefect defect = inspection.getGeneratedDefect();
        assertEquals(DefectSeverity.HIGH, defect.getSeverity());
        assertEquals("Cracked insulator", defect.getDescription());
        assertEquals(inspection.getAsset(), defect.getAsset());
        verify(fixture.history).recordDefectChange(eq(defect), isNull(), eq("OPEN"), anyString());

        MaintenanceInspection ok = inspection(InspectionResult.OK);
        when(fixture.repository.findById(ok.getId())).thenReturn(Optional.of(ok));
        assertThrows(InspectionException.class, () -> fixture.service.createDefect(ok.getId(), new CreateDefectFromInspectionRequest(null, null, null, null)));

        MaintenanceInspection minor = inspection(InspectionResult.MINOR_DEFECT);
        when(fixture.repository.findById(minor.getId())).thenReturn(Optional.of(minor));
        assertThrows(InspectionException.class, () -> fixture.service.createDefect(minor.getId(), new CreateDefectFromInspectionRequest(null, null, null, false)));
    }

    @Test
    void anUnsafeInspectionCreatesAnUrgentCorrectiveOrderLinkedToItsDefect() {
        InspectionFixture fixture = new InspectionFixture();
        MaintenanceInspection inspection = inspection(InspectionResult.UNSAFE);
        CatenaryDefect defect = CatenaryDefect.builder().code("DEF-000002").asset(inspection.getAsset()).severity(DefectSeverity.CRITICAL)
                .description("kink").detectedAt(Instant.now()).build();
        inspection.setGeneratedDefect(defect);
        MaintenanceOrder created = order(MaintenanceOrderType.URGENT, MaintenanceOrderStatus.DRAFT);
        MaintenanceOrderResponse response = mock(MaintenanceOrderResponse.class);
        when(response.id()).thenReturn(created.getId());
        when(fixture.repository.findById(inspection.getId())).thenReturn(Optional.of(inspection));
        when(fixture.orderService.create(any())).thenReturn(response);
        when(fixture.orderRepository.findById(created.getId())).thenReturn(Optional.of(created));

        fixture.service.createCorrectiveOrder(inspection.getId(), new CreateCorrectiveOrderRequest(null, null, null, null, null));

        ArgumentCaptor<MaintenanceOrderRequest> captor = ArgumentCaptor.forClass(MaintenanceOrderRequest.class);
        verify(fixture.orderService).create(captor.capture());
        assertEquals(MaintenanceOrderType.URGENT, captor.getValue().type());
        assertEquals(MaintenancePriority.CRITICAL, captor.getValue().priority());
        assertEquals(inspection.getAsset().getId(), captor.getValue().assetId());
        assertEquals(inspection, created.getOriginInspection());
        assertEquals(defect, created.getOriginDefect());
        assertEquals(created, defect.getOrder());
        assertEquals(DefectStatus.IN_PROGRESS, defect.getStatus());
        assertEquals(created, inspection.getGeneratedOrder());

        // Segunda llamada: idempotente, no crea otra orden.
        fixture.service.createCorrectiveOrder(inspection.getId(), new CreateCorrectiveOrderRequest(null, null, null, null, null));
        verify(fixture.orderService, org.mockito.Mockito.times(1)).create(any());
    }

    // ----------------------------------------------------------- estimation

    @Test
    void workloadEstimateAddsPerUnitTypesPerTaskAndPerKilometreTypesOncePerOrder() {
        WorkloadEstimatorImpl estimator = new WorkloadEstimatorImpl();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        order.setStartKp(new BigDecimal("10000.000"));
        order.setEndKp(new BigDecimal("12000.000"));
        MaintenanceTaskType messenger = MaintenanceTaskType.builder().code("RG-08").description("Messenger wire").unit(TaskUnit.KM)
                .fixedMinutes(new BigDecimal("60")).standardMinutesPerUnit(new BigDecimal("3")).orderIndex(8).build();
        MaintenanceTaskType insulators = MaintenanceTaskType.builder().code("RG-01").description("Insulators").unit(TaskUnit.UNIT)
                .standardMinutesPerUnit(new BigDecimal("5")).orderIndex(1).build();
        for (int index = 0; index < 3; index++) {
            MaintenanceTask task = task(order, MaintenanceTaskStatus.PENDING);
            task.getTaskTypes().add(insulators);
            task.getTaskTypes().add(messenger);
            order.getTasks().add(task);
        }

        WorkloadEstimate estimate = estimator.estimate(order);

        // 3 perfiles x 5 min + (60 + 3 x 2 km) una sola vez.
        assertEquals(0, new BigDecimal("81.00").compareTo(estimate.estimatedMinutes()));
        assertEquals(1, estimate.estimatedShifts());
    }

    @Test
    void perKilometreTypesCountTheLengthWalkedOnEachTrackOfTheOrder() {
        WorkloadEstimatorImpl estimator = new WorkloadEstimatorImpl();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        order.setStartKp(new BigDecimal("10000.000"));
        order.setEndKp(new BigDecimal("12000.000"));
        MaintenanceTaskType messenger = MaintenanceTaskType.builder().code("RG-08").description("Messenger wire").unit(TaskUnit.KM)
                .fixedMinutes(new BigDecimal("60")).standardMinutesPerUnit(new BigDecimal("3")).orderIndex(8).build();
        MaintenanceTask onTheSection = task(order, MaintenanceTaskStatus.PENDING);
        onTheSection.setAsset(profileOn("2-1.01", 2L, "10500.000"));
        onTheSection.getTaskTypes().add(messenger);
        order.getTasks().add(onTheSection);
        MaintenanceTask planned = MaintenanceTask.builder().sequence(9).description("Track 3, km 1").asset(profileOn("3-1.01", 3L, "1000.000")).build();
        planned.getTaskTypes().add(messenger);
        MaintenanceTask plannedToo = MaintenanceTask.builder().sequence(10).description("Track 3, km 4").asset(profileOn("3-1.02", 3L, "4000.000")).build();
        plannedToo.getTaskTypes().add(messenger);

        // Via 2: (60 + 3 x 2 km, el tramo de la orden); via 3: (60 + 3 x 3 km, del primero al ultimo).
        assertEquals(0, new BigDecimal("135.00").compareTo(estimator.estimate(order, List.of(planned, plannedToo)).estimatedMinutes()));
        assertEquals(0, new BigDecimal("66.00").compareTo(estimator.estimate(order).estimatedMinutes()), "Only the section, as always");
        assertEquals(1, order.getTasks().size(), "What is planned is not added to the order");
    }

    // ----------------------------------------------------------------- shifts

    @Test
    void closingAShiftComputesTheNetMinutesFromTheVoltageCutOffAndReleasesUnfinishedTasks() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = shift(Set.of(1L, 2L), PossessionType.PARTIAL, ShiftStatus.IN_PROGRESS);
        shift.setActualStart(Instant.parse("2026-01-27T21:10:00Z"));
        shift.setVoltageCutoffAt(Instant.parse("2026-01-27T23:40:00Z"));
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceTask unfinished = task(order, MaintenanceTaskStatus.IN_PROGRESS);
        unfinished.setShift(shift);
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        when(fixture.taskRepository.findByShiftIdAndStatusIn(eq(shift.getId()), any())).thenReturn(List.of(unfinished));

        assertThrows(ValidationException.class, () -> fixture.service.close(shift.getId(), new CloseShiftRequest(Instant.parse("2026-01-27T20:00:00Z"), null, null, null)));
        fixture.service.close(shift.getId(), new CloseShiftRequest(Instant.parse("2026-01-28T04:30:00Z"), null, null, "late cut-off"));

        assertEquals(ShiftStatus.CLOSED, shift.getStatus());
        assertEquals(290, shift.getNetWorkMinutes(), "De 23:40 a 04:30");
        assertEquals(MaintenanceTaskStatus.PENDING, unfinished.getStatus());
        assertNull(unfinished.getShift(), "La tarea vuelve a la cola de la orden sin turno");
    }

    @Test
    void theProfilesReviewedInAShiftAreTheCompletedTasksAssetsByKpWithoutDuplicates() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = shift(2L, PossessionType.PARTIAL, ShiftStatus.CLOSED);
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        CatenaryAsset far = profile("13-2.02", "13060.290");
        CatenaryAsset near = profile("12-2.27", "12847.990");
        CatenaryAsset pendingProfile = profile("14-2.02", "14078.090");
        MaintenanceTask farTask = task(order, MaintenanceTaskStatus.COMPLETED);
        farTask.setAsset(far);
        MaintenanceTask nearTask = task(order, MaintenanceTaskStatus.COMPLETED);
        nearTask.setAsset(near);
        MaintenanceTask nearAgain = task(order, MaintenanceTaskStatus.COMPLETED);
        nearAgain.setAsset(near);
        MaintenanceTask pendingTask = task(order, MaintenanceTaskStatus.PENDING);
        pendingTask.setAsset(pendingProfile);
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        when(fixture.taskRepository.findByShiftIdOrderBySequenceAsc(shift.getId())).thenReturn(List.of(farTask, nearTask, nearAgain, pendingTask));

        List<CatenaryAssetSummaryResponse> reviewed = fixture.service.profiles(shift.getId(), null);
        List<CatenaryAssetSummaryResponse> pending = fixture.service.profiles(shift.getId(), MaintenanceTaskStatus.PENDING);

        assertEquals(List.of("12-2.27", "13-2.02"), reviewed.stream().map(CatenaryAssetSummaryResponse::name).toList());
        assertEquals(List.of("14-2.02"), pending.stream().map(CatenaryAssetSummaryResponse::name).toList());
        assertEquals(2, fixture.service.report(shift.getId()).profilesReviewed());
    }

    // ---------------------------------------------------------- orders (2)

    @Test
    void updatingAfterPlanningOnlyAllowsDescriptionPriorityAndClosingNotes() {
        OrderFixture fixture = new OrderFixture();
        MaintenanceOrder assigned = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.ASSIGNED);
        when(fixture.lookups.order(assigned.getId())).thenReturn(assigned);

        assertThrows(InvalidTransitionException.class, () -> fixture.service.update(assigned.getId(),
                new MaintenanceOrderUpdateRequest("New title", null, null, null, null, null, null, null, null, null, null, null, null, null)));
        fixture.service.update(assigned.getId(), new MaintenanceOrderUpdateRequest(null, "Re-tension the wire", MaintenancePriority.HIGH,
                null, null, null, "half done", null, null, null, null, null, null, null));

        assertEquals("Re-tension the wire", assigned.getDescription());
        assertEquals(MaintenancePriority.HIGH, assigned.getPriority());
        assertEquals("half done", assigned.getClosingNotes());
        assertEquals("Order", assigned.getTitle());

        // En DRAFT se edita todo, pero el rango kp resultante sigue teniendo que ser valido.
        MaintenanceOrder draft = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        when(fixture.lookups.order(draft.getId())).thenReturn(draft);
        fixture.service.update(draft.getId(), new MaintenanceOrderUpdateRequest(" Preventive T2 ", null, null, LocalDate.of(2026, 2, 1),
                null, "yossi", null, 7L, null, null, null, null, null, null));
        assertEquals("Preventive T2", draft.getTitle());
        assertEquals(LocalDate.of(2026, 2, 1), draft.getPlannedDate());
        assertEquals(7L, draft.getExecutionPackageId());
        assertThrows(ValidationException.class, () -> fixture.service.update(draft.getId(),
                new MaintenanceOrderUpdateRequest(null, null, null, null, null, null, null, null, null, null, new BigDecimal("15000.000"), null, null, null)));

        // URGENT no baja nunca de CRITICAL aunque la peticion lo pida.
        MaintenanceOrder urgent = order(MaintenanceOrderType.URGENT, MaintenanceOrderStatus.IN_PROGRESS);
        when(fixture.lookups.order(urgent.getId())).thenReturn(urgent);
        fixture.service.update(urgent.getId(), new MaintenanceOrderUpdateRequest(null, null, MaintenancePriority.LOW, null, null, null, null, null, null, null, null, null, null, null));
        assertEquals(MaintenancePriority.CRITICAL, urgent.getPriority());

        MaintenanceOrder completed = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.COMPLETED);
        when(fixture.lookups.order(completed.getId())).thenReturn(completed);
        assertThrows(InvalidTransitionException.class, () -> fixture.service.update(completed.getId(),
                new MaintenanceOrderUpdateRequest(null, "late", null, null, null, null, null, null, null, null, null, null, null, null)));
    }

    @Test
    void startingAnUrgentDraftReservesItsMaterialsOnceItIsInProgress() {
        OrderFixture fixture = new OrderFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.URGENT, MaintenanceOrderStatus.DRAFT);
        line(order);
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        doAnswer(invocation -> {
            assertEquals(MaintenanceOrderStatus.IN_PROGRESS, order.getStatus(), "Reserved after the transition, as when planning");
            return null;
        }).when(fixture.stock).reserveAll(order);

        fixture.service.start(order.getId(), null);

        verify(fixture.stock).reserveAll(order);
        verify(fixture.history).recordOrderChange(order, "DRAFT", "IN_PROGRESS", null);
    }

    @Test
    void anInspectionOrderNeedsARecordedInspectionBeforeItCompletes() {
        OrderFixture fixture = new OrderFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.INSPECTION, MaintenanceOrderStatus.IN_PROGRESS);
        order.setActualStartDate(Instant.now());
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.inspectionRepository.existsByOriginOrderId(order.getId())).thenReturn(false);

        assertThrows(InvalidTransitionException.class, () -> fixture.service.complete(order.getId(), new CompleteOrderRequest("checked", null, null)));
        assertEquals(MaintenanceOrderStatus.IN_PROGRESS, order.getStatus());

        when(fixture.inspectionRepository.existsByOriginOrderId(order.getId())).thenReturn(true);
        fixture.service.complete(order.getId(), new CompleteOrderRequest("checked", null, null));

        assertEquals(MaintenanceOrderStatus.COMPLETED, order.getStatus());
        assertEquals("checked", order.getClosingNotes());
        verify(fixture.assetRepository, never()).save(any());
    }

    @Test
    void anOrderIsAssignedToATeamFromPlannedAndCannotBeAssignedFromDraft() {
        OrderFixture fixture = new OrderFixture();
        MaintenanceOrder planned = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        MaintenanceTeam team = team("A");
        when(fixture.lookups.order(planned.getId())).thenReturn(planned);
        when(fixture.lookups.team(team.getId())).thenReturn(team);

        fixture.service.assign(planned.getId(), new AssignOrderRequest(team.getId(), null, "night crew"));

        assertEquals(MaintenanceOrderStatus.ASSIGNED, planned.getStatus());
        assertEquals(team, planned.getTeam());
        verify(fixture.history).recordOrderChange(planned, "PLANNED", "ASSIGNED", "night crew");

        MaintenanceOrder draft = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        when(fixture.lookups.order(draft.getId())).thenReturn(draft);
        assertThrows(InvalidTransitionException.class, () -> fixture.service.assign(draft.getId(), new AssignOrderRequest(team.getId(), null, null)));
    }

    @Test
    void anOrderOfATeamOfMtoResourcesWithoutAUserGoesToItsLeader() {
        OrderFixture fixture = new OrderFixture();
        CrewPerson leaderA = crewPerson("TEAM_LEADER");
        ReflectionTestUtils.setField(leaderA, "username", "equipo.jefe");
        CrewPerson leaderB = crewPerson("TEAM_LEADER");
        ReflectionTestUtils.setField(leaderB, "username", "otro.jefe");
        MaintenanceTeam teamA = syncedTeam(leaderA, null, fleetVehicle("ROAD_RAIL", true, false, 5));
        MaintenanceTeam teamB = syncedTeam(leaderB, null, fleetVehicle("ROAD_RAIL", true, false, 5));
        MaintenanceTeam local = team("L");
        CatenaryAsset section = trackSection();
        when(fixture.lookups.enabledAsset(section.getId())).thenReturn(section);
        when(fixture.codeGenerator.nextOrderCode()).thenReturn("MO-000001");
        when(fixture.lookups.team(teamA.getId())).thenReturn(teamA);
        when(fixture.lookups.team(teamB.getId())).thenReturn(teamB);
        when(fixture.lookups.team(local.getId())).thenReturn(local);

        // El alta sin usuario va al jefe; con un usuario dado, ese gana.
        fixture.service.create(new MaintenanceOrderRequest("Preventive T2", null, MaintenanceOrderType.PREVENTIVE, null,
                section.getId(), null, teamA.getId(), null, null));
        assertEquals("equipo.jefe", fixture.savedOrder().getAssignedUser());
        OrderFixture explicit = new OrderFixture();
        when(explicit.lookups.enabledAsset(section.getId())).thenReturn(section);
        when(explicit.lookups.team(teamA.getId())).thenReturn(teamA);
        explicit.service.create(new MaintenanceOrderRequest("Preventive T2", null, MaintenanceOrderType.PREVENTIVE, null,
                section.getId(), null, teamA.getId(), "yossi", null));
        assertEquals("yossi", explicit.savedOrder().getAssignedUser());

        // Cambiar de equipo pasa la orden del jefe anterior al nuevo; un usuario dado a mano se queda.
        MaintenanceOrder draft = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        draft.setTeam(teamA);
        draft.setAssignedUser("equipo.jefe");
        when(fixture.lookups.order(draft.getId())).thenReturn(draft);
        fixture.service.update(draft.getId(), new MaintenanceOrderUpdateRequest(null, null, null, null, teamB.getId(),
                null, null, null, null, null, null, null, null, null));
        assertEquals("otro.jefe", draft.getAssignedUser());
        draft.setAssignedUser("yossi");
        fixture.service.update(draft.getId(), new MaintenanceOrderUpdateRequest(null, null, null, null, teamA.getId(),
                null, null, null, null, null, null, null, null, null));
        assertEquals("yossi", draft.getAssignedUser(), "un usuario que no era el jefe anterior se queda");
        fixture.service.update(draft.getId(), new MaintenanceOrderUpdateRequest(null, null, null, null, local.getId(),
                null, null, null, null, null, null, null, null, null));
        assertEquals("yossi", draft.getAssignedUser(), "un equipo local no tiene jefe que poner");

        // Asignar a un equipo de mto-resources sin usuario: su jefe, y con el va el aviso de la orden asignada.
        MaintenanceOrder planned = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        when(fixture.lookups.order(planned.getId())).thenReturn(planned);
        fixture.service.assign(planned.getId(), new AssignOrderRequest(teamB.getId(), null, "night crew"));
        assertEquals("otro.jefe", planned.getAssignedUser());
        assertEquals(MaintenanceOrderStatus.ASSIGNED, planned.getStatus());
        // El dialogo manda el usuario que ya tenia: con el jefe anterior, la orden pasa al del equipo nuevo.
        fixture.service.assign(planned.getId(), new AssignOrderRequest(teamA.getId(), "otro.jefe", null));
        assertEquals("equipo.jefe", planned.getAssignedUser());
        fixture.service.assign(planned.getId(), new AssignOrderRequest(teamB.getId(), "yossi", null));
        assertEquals("yossi", planned.getAssignedUser(), "una persona elegida en el dialogo gana");

        // Un jefe sin usuario de Keycloak no pone nada.
        ReflectionTestUtils.setField(leaderA, "username", null);
        MaintenanceOrder other = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        when(fixture.lookups.order(other.getId())).thenReturn(other);
        fixture.service.assign(other.getId(), new AssignOrderRequest(teamA.getId(), null, null));
        assertNull(other.getAssignedUser());
    }

    // ----------------------------------------------------------------- assets

    @Test
    void aTrackSectionNeedsAUniqueCodeAndAValidKilometricRange() {
        AssetFixture fixture = new AssetFixture();
        when(fixture.repository.existsByCode("SEC-T2")).thenReturn(true);

        assertThrows(DuplicateCodeException.class, () -> fixture.service.createTrackSection(sectionRequest("  SEC-T2 ", "12847.990", "14078.090")));
        assertThrows(ValidationException.class, () -> fixture.service.createTrackSection(sectionRequest("SEC-T3", "14078.090", "12847.990")));
        verify(fixture.repository, never()).save(any());

        fixture.service.createTrackSection(sectionRequest("SEC-T3", "12847.990", "14078.090"));

        ArgumentCaptor<CatenaryAsset> captor = ArgumentCaptor.forClass(CatenaryAsset.class);
        verify(fixture.repository).save(captor.capture());
        CatenaryAsset saved = captor.getValue();
        assertEquals("SEC-T3", saved.getCode());
        assertEquals(CatenaryAssetType.TRACK_SECTION, saved.getType());
        assertEquals(TrackKind.MAIN, saved.getTrackKind());
        assertEquals(365, saved.getPreventiveIntervalDays());
        assertTrue(saved.isEnabled());
        assertFalse(saved.isFromMasterData());
    }

    @Test
    void aFormThatSendsASynchronizedAssetBackWholeIsAcceptedUnlessItChangesWhatTheSourceDecides() {
        AssetFixture fixture = new AssetFixture();
        CatenaryAsset synced = profile("12-2.27", "12847.990");
        synced.setSourceService("mto-configuration");
        synced.setSourceEntityId("prf-1");
        when(fixture.lookups.asset(synced.getId())).thenReturn(synced);

        // Lo que manda un formulario: el activo entero, con la descripcion nueva y el kp con otra escala.
        fixture.service.update(synced.getId(), new CatenaryAssetUpdateRequest(" 12-2.27 ", "Next to the bridge", null, null, 6L, 2L, null,
                new BigDecimal("12847.99"), new BigDecimal("12847.990"), null, null, null, null));
        assertEquals("Next to the bridge", synced.getDescription());
        assertEquals("12-2.27", synced.getName());

        assertThrows(AssetDisabledException.class, () -> fixture.service.update(synced.getId(),
                new CatenaryAssetUpdateRequest("12-2.27", null, null, null, 6L, 3L, null, null, null, null, null, null, null)), "another track");
        assertThrows(AssetDisabledException.class, () -> fixture.service.update(synced.getId(),
                new CatenaryAssetUpdateRequest(null, null, null, null, null, null, null, new BigDecimal("12848"), null, null, null, null, null)), "another kp");
        assertThrows(AssetDisabledException.class, () -> fixture.service.update(synced.getId(),
                new CatenaryAssetUpdateRequest(null, null, null, null, null, null, 9L, null, null, null, null, null, null)), "a station it does not have");
        assertThrows(AssetDisabledException.class, () -> fixture.service.update(synced.getId(),
                new CatenaryAssetUpdateRequest(null, null, null, null, null, null, null, null, null, TrackKind.MAIN, null, null, null)),
                "a track kind, which only own sections have");
        assertEquals(2L, synced.getTrackId());
    }

    @Test
    void assetsFromMasterDataOnlyAcceptDescriptionEnabledAndPreventiveInterval() {
        AssetFixture fixture = new AssetFixture();
        CatenaryAsset synced = profile("12-2.27", "12847.990");
        synced.setSourceService("mto-configuration");
        synced.setSourceEntityId("prf-1");
        when(fixture.lookups.asset(synced.getId())).thenReturn(synced);

        assertThrows(AssetDisabledException.class, () -> fixture.service.update(synced.getId(),
                new CatenaryAssetUpdateRequest("renamed", null, null, null, null, null, null, null, null, null, null, null, null)));
        fixture.service.update(synced.getId(), new CatenaryAssetUpdateRequest(null, "Next to the bridge", false, 180, null, null, null, null, null, null, null, null, null));

        assertEquals("12-2.27", synced.getName());
        assertEquals("Next to the bridge", synced.getDescription());
        assertFalse(synced.isEnabled());
        assertTrue(synced.getDisabledLocally(), "Disabled here, which no master data event undoes");
        assertEquals(180, synced.getPreventiveIntervalDays());

        fixture.service.update(synced.getId(), new CatenaryAssetUpdateRequest(null, null, true, null, null, null, null, null, null, null, null, null, null));
        assertTrue(synced.isEnabled());
        assertFalse(synced.getDisabledLocally());

        // Desactivado en mto-configuration: reactivarlo aqui no serviria, el siguiente evento diria que no.
        CatenaryAsset goneAtSource = CatenaryAsset.builder().code("PRF-gone").name("13-2.09").type(CatenaryAssetType.PROFILE)
                .sourceService("mto-configuration").sourceEntityId("prf-2").enabledAtSource(false).enabled(false).build();
        ReflectionTestUtils.setField(goneAtSource, "id", UUID.randomUUID());
        when(fixture.lookups.asset(goneAtSource.getId())).thenReturn(goneAtSource);
        assertThrows(AssetDisabledException.class, () -> fixture.service.update(goneAtSource.getId(),
                new CatenaryAssetUpdateRequest(null, null, true, null, null, null, null, null, null, null, null, null, null)));
        assertFalse(goneAtSource.isEnabled());

        // Un activo local admite cambiar la localizacion, siempre con un rango kp coherente y trackKind solo en tramos.
        CatenaryAsset local = profile("13-2.01", "13007.290");
        when(fixture.lookups.asset(local.getId())).thenReturn(local);
        assertThrows(ValidationException.class, () -> fixture.service.update(local.getId(),
                new CatenaryAssetUpdateRequest(null, null, null, null, null, null, null, null, null, TrackKind.MAIN, null, null, null)));
        assertThrows(ValidationException.class, () -> fixture.service.update(local.getId(),
                new CatenaryAssetUpdateRequest(null, null, null, null, null, null, null, null, new BigDecimal("13000.000"), null, null, null, null)));

        CatenaryAsset section = trackSection();
        when(fixture.lookups.asset(section.getId())).thenReturn(section);
        fixture.service.update(section.getId(), new CatenaryAssetUpdateRequest(" T2 renamed ", null, null, null, 7L, 3L, 9L, null, null, TrackKind.DIVERTED, null, null, null));
        assertEquals("T2 renamed", section.getName());
        assertEquals(7L, section.getExecutionPackageId());
        assertEquals(3L, section.getTrackId());
        assertEquals(9L, section.getStationId());
        assertEquals(TrackKind.DIVERTED, section.getTrackKind());
    }

    @Test
    void disablingAnAssetIsIdempotentAndIsRecordedEvenWhenTheSourceHasItDisabledAlready() {
        AssetFixture fixture = new AssetFixture();
        CatenaryAsset asset = profile("12-2.27", "12847.990");
        when(fixture.lookups.asset(asset.getId())).thenReturn(asset);

        fixture.service.disable(asset.getId());
        fixture.service.disable(asset.getId());

        assertFalse(asset.isEnabled());
        verify(fixture.repository, times(1)).save(asset);

        // Ya desactivado en el origen: la decision de aqui se guarda igual, para cuando el origen lo reactive.
        CatenaryAsset goneAtSource = CatenaryAsset.builder().code("PRF-gone").name("13-2.09").type(CatenaryAssetType.PROFILE)
                .sourceService("mto-configuration").sourceEntityId("prf-2").enabledAtSource(false).enabled(false).build();
        ReflectionTestUtils.setField(goneAtSource, "id", UUID.randomUUID());
        when(fixture.lookups.asset(goneAtSource.getId())).thenReturn(goneAtSource);
        fixture.service.disable(goneAtSource.getId());
        assertTrue(goneAtSource.getDisabledLocally());
        verify(fixture.repository).save(goneAtSource);
    }

    // ----------------------------------------------------------- merge patch

    @Test
    void aPatchEmptiesWhatEachResourceAdmitsAndRefusesTheRequiredFieldsWithA400() {
        OrderFixture orders = new OrderFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.DRAFT);
        order.setPlannedDate(LocalDate.now().plusDays(2));
        order.setTeam(team("T1"));
        when(orders.lookups.order(order.getId())).thenReturn(order);
        orders.service.patch(order.getId(), new MergePatch<>(orderUpdate(null), Set.of("plannedDate", "teamId", "stationId")));
        assertNull(order.getPlannedDate());
        assertNull(order.getTeam());
        assertEquals(2L, order.getTrackId(), "What the patch leaves out is not touched");
        ValidationException required = assertThrows(ValidationException.class,
                () -> orders.service.patch(order.getId(), new MergePatch<>(orderUpdate(null), Set.of("title", "priority"))));
        assertEquals("These fields cannot be emptied: priority, title", required.getMessage());
        order.setStatus(MaintenanceOrderStatus.IN_PROGRESS);
        order.setPlannedDate(LocalDate.now());
        assertThrows(InvalidTransitionException.class,
                () -> orders.service.patch(order.getId(), new MergePatch<>(orderUpdate(null), Set.of("plannedDate"))),
                "Emptying is changing: after PLANNED only description and closing notes");
        orders.service.patch(order.getId(), new MergePatch<>(orderUpdate(null), Set.of("closingNotes")));
        assertEquals(LocalDate.now(), order.getPlannedDate());

        ShiftFixture shifts = new ShiftFixture();
        MaintenanceShift shift = shift(2L, PossessionType.PARTIAL, ShiftStatus.PLANNED);
        shift.setTeam(team("T2"));
        shift.setPlannedStart(Instant.parse("2026-02-01T22:00:00Z"));
        when(shifts.lookups.shift(shift.getId())).thenReturn(shift);
        shifts.service.patch(shift.getId(), new MergePatch<>(shiftUpdate(null), Set.of("teamId", "plannedStart", "blockingDisconnectorIds")));
        assertNull(shift.getTeam());
        assertNull(shift.getPlannedStart());
        assertThrows(ValidationException.class, () -> shifts.service.patch(shift.getId(), new MergePatch<>(shiftUpdate(null), Set.of("trackIds"))));

        DefectFixture defects = new DefectFixture();
        CatenaryDefect defect = defect(DefectStatus.OPEN);
        defect.setRepairPlannedDate(LocalDate.now().plusDays(7));
        when(defects.repository.findById(defect.getId())).thenReturn(Optional.of(defect));
        defects.service.patch(defect.getId(), new MergePatch<>(new CatenaryDefectUpdateRequest(null, null, null, null, null, null, null, null),
                Set.of("repairPlannedDate", "photoRefs")));
        assertNull(defect.getRepairPlannedDate());
        assertTrue(defect.getPhotoRefs().isEmpty());
        assertThrows(ValidationException.class, () -> defects.service.patch(defect.getId(),
                new MergePatch<>(new CatenaryDefectUpdateRequest(null, " ", null, null, null, null, null, null), Set.of())),
                "A blank description is a 400 here, not a 500 against @NotBlank at commit");

        InspectionFixture inspections = new InspectionFixture();
        MaintenanceInspection inspection = inspection(InspectionResult.OK);
        inspection.setKp(new BigDecimal("13499.290"));
        MaintenanceInspectionItem height = MaintenanceInspectionItem.fromTemplate(inspection, templateItem("CW_HEIGHT", 1, true));
        ReflectionTestUtils.setField(height, "id", UUID.randomUUID());
        height.setMeasuredValue(new BigDecimal("5200"));
        inspection.getItems().add(height);
        when(inspections.repository.findById(inspection.getId())).thenReturn(Optional.of(inspection));
        inspections.service.patch(inspection.getId(), new MergePatch<>(new MaintenanceInspectionUpdateRequest(null, null, null, null, null, null, null, null, null),
                Set.of("kp")));
        assertNull(inspection.getKp());
        inspections.service.patchItem(inspection.getId(), height.getId(),
                new MergePatch<>(new CheckItemUpdateRequest(null, null, null, null, null, null), Set.of("measuredValue")));
        assertNull(height.getMeasuredValue());
        assertThrows(ValidationException.class, () -> inspections.service.patchItem(inspection.getId(), height.getId(),
                new MergePatch<>(new CheckItemUpdateRequest(null, null, null, null, null, null), Set.of("adjusted"))),
                "Adjusted is yes or no, never nothing");

        MaterialFixture materials = new MaterialFixture();
        MaintenanceOrder planned = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        when(materials.lookups.order(planned.getId())).thenReturn(planned);
        MaintenanceMaterialUsage line = storedLine(materials, planned, line(planned));
        assertThrows(ValidationException.class, () -> materials.service.patch(planned.getId(), line.getId(),
                new MergePatch<>(new MaterialUsageUpdateRequest(null, null, null, null), Set.of("consumedQuantity"))));

        AssetFixture assets = new AssetFixture();
        CatenaryAsset synced = profile("12-2.27", "12847.990");
        synced.setSourceService("mto-configuration");
        synced.setSourceEntityId("prf-1");
        synced.setPreventiveIntervalDays(180);
        when(assets.lookups.asset(synced.getId())).thenReturn(synced);
        assets.service.patch(synced.getId(), new MergePatch<>(assetUpdate(null), Set.of("preventiveIntervalDays", "description")));
        assertNull(synced.getPreventiveIntervalDays());
        assertThrows(AssetDisabledException.class, () -> assets.service.patch(synced.getId(), new MergePatch<>(assetUpdate(null), Set.of("executionPackageId"))),
                "Emptying the package of a synchronized asset is changing what mto-configuration decides");
        assets.service.patch(synced.getId(), new MergePatch<>(assetUpdate(null), Set.of("stationId")));
        assertNull(synced.getStationId(), "A profile has no station: emptying it changes nothing, so it is not refused");
        CatenaryAsset section = trackSection();
        section.setStationId(9L);
        when(assets.lookups.asset(section.getId())).thenReturn(section);
        assets.service.patch(section.getId(), new MergePatch<>(assetUpdate(null), Set.of("stationId")));
        assertNull(section.getStationId());
    }

    @Test
    void aStaleVersionIsRejectedBeforeAnythingChangesAndWithoutVersionEverythingIsAsBefore() {
        OrderFixture fixture = new OrderFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.DRAFT);
        ReflectionTestUtils.setField(order, "version", 3L);
        when(fixture.lookups.order(order.getId())).thenReturn(order);

        StaleVersionException stale = assertThrows(StaleVersionException.class, () -> fixture.service.update(order.getId(),
                new MaintenanceOrderUpdateRequest(null, "Someone else's view", null, null, null, null, null, null, null, null, null, null, null, 2L)));
        assertTrue(stale.getMessage().contains("version 3"));
        assertNull(order.getDescription());
        verify(fixture.repository, never()).save(any());

        fixture.service.update(order.getId(), new MaintenanceOrderUpdateRequest(null, "Read just now", null, null, null, null, null, null, null, null, null, null, null, 3L));
        assertEquals("Read just now", order.getDescription());
        fixture.service.update(order.getId(), new MaintenanceOrderUpdateRequest(null, "No version", null, null, null, null, null, null, null, null, null, null, null, null));
        assertEquals("No version", order.getDescription());

        // Un punto de checklist lleva su propia version.
        TaskFixture tasks = new TaskFixture();
        MaintenanceOrder running = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceTask task = task(running, MaintenanceTaskStatus.PENDING);
        MaintenanceTaskCheckItem item = MaintenanceTaskCheckItem.fromTemplate(task, templateItem("CW_HEIGHT", 1, true));
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(item, "version", 1L);
        task.getCheckItems().add(item);
        when(tasks.lookups.order(running.getId())).thenReturn(running);
        when(tasks.repository.findByIdAndOrderId(task.getId(), running.getId())).thenReturn(Optional.of(task));
        assertThrows(StaleVersionException.class, () -> tasks.service.updateCheckItem(running.getId(), task.getId(), item.getId(),
                new CheckItemUpdateRequest(new BigDecimal("5200"), null, null, null, null, 0L)));
        assertNull(item.getMeasuredValue());
    }

    @Test
    void aBlankTitleIsA400AndLoweringThePlannedQuantityBelowWhatWasConsumedIsTheOverConsumptionRule() {
        OrderFixture orders = new OrderFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.DRAFT);
        when(orders.lookups.order(order.getId())).thenReturn(order);
        assertThrows(ValidationException.class, () -> orders.service.update(order.getId(),
                new MaintenanceOrderUpdateRequest("  ", null, null, null, null, null, null, null, null, null, null, null, null, null)));
        assertEquals("Order", order.getTitle());

        MaterialFixture materials = new MaterialFixture();
        MaintenanceOrder planned = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        when(materials.lookups.order(planned.getId())).thenReturn(planned);
        MaintenanceMaterialUsage line = storedLine(materials, planned, line(planned));
        line.setConsumedQuantity(new BigDecimal("2"));
        MaterialUsageException below = assertThrows(MaterialUsageException.class, () -> materials.service.update(planned.getId(), line.getId(),
                new MaterialUsageUpdateRequest(new BigDecimal("1"), null, null, null)));
        assertTrue(below.getMessage().contains("below"), "It used to break a CHECK of the database and answer 500");
        line.setAllowOverConsumption(true);
        materials.service.update(planned.getId(), line.getId(), new MaterialUsageUpdateRequest(new BigDecimal("1"), null, null, null));
        assertEquals(0, BigDecimal.ONE.compareTo(line.getPlannedQuantity()));
    }

    private static MaintenanceOrderUpdateRequest orderUpdate(Long version) {
        return new MaintenanceOrderUpdateRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, version);
    }

    private static MaintenanceShiftUpdateRequest shiftUpdate(Long version) {
        return new MaintenanceShiftUpdateRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, version);
    }

    private static CatenaryAssetUpdateRequest assetUpdate(Long version) {
        return new CatenaryAssetUpdateRequest(null, null, null, null, null, null, null, null, null, null, null, null, version);
    }

    // ---------------------------------------------------------------- defects

    @Test
    void aDefectLinkedToAnOrderAtCreationStartsInProgressWithItsOwnKpRange() {
        DefectFixture fixture = new DefectFixture();
        CatenaryAsset section = trackSection();
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.PLANNED);
        when(fixture.lookups.enabledAsset(section.getId())).thenReturn(section);
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.codeGenerator.nextDefectCode()).thenReturn("DEF-000010");

        fixture.service.create(new CatenaryDefectRequest(section.getId(), DefectSeverity.HIGH, "  Worn contact wire ", null, null, null,
                order.getId(), new BigDecimal("13000.000"), null, null, null, null, List.of("wire.jpg")));

        CatenaryDefect saved = fixture.savedDefect();
        assertEquals("DEF-000010", saved.getCode());
        assertEquals(DefectStatus.IN_PROGRESS, saved.getStatus());
        assertEquals("Worn contact wire", saved.getDescription());
        assertEquals(order, saved.getOrder());
        assertEquals(2L, saved.getTrackId());
        assertEquals(0, new BigDecimal("13000.000").compareTo(saved.getStartKp()));
        assertEquals(0, new BigDecimal("13000.000").compareTo(saved.getEndKp()), "A single kp closes the range on itself");
        assertNotNull(saved.getDetectedAt());
        assertEquals(List.of("wire.jpg"), saved.getPhotoRefs());
        verify(fixture.history).recordDefectChange(saved, null, "IN_PROGRESS", "Defect recorded and linked to order " + order.getCode());

        MaintenanceOrder cancelled = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.CANCELLED);
        when(fixture.lookups.order(cancelled.getId())).thenReturn(cancelled);
        assertThrows(InvalidTransitionException.class, () -> fixture.service.create(new CatenaryDefectRequest(section.getId(), DefectSeverity.LOW,
                "late", null, null, null, cancelled.getId(), null, null, null, null, null, null)));
        assertThrows(ValidationException.class, () -> fixture.service.create(new CatenaryDefectRequest(section.getId(), DefectSeverity.LOW,
                "inverted", null, null, null, null, new BigDecimal("14000.000"), new BigDecimal("13000.000"), null, null, null, null)));
        verify(fixture.repository, times(1)).save(any());
    }

    @Test
    void aDefectWithALinkedOrderIsOnlyResolvedWhenTheOrderCompletesOrAShiftIsDeclared() {
        DefectFixture fixture = new DefectFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        CatenaryDefect defect = defect(DefectStatus.IN_PROGRESS);
        defect.setOrder(order);
        when(fixture.repository.findById(defect.getId())).thenReturn(Optional.of(defect));

        assertThrows(InvalidTransitionException.class, () -> fixture.service.resolve(defect.getId(), new ResolveDefectRequest("fixed", null, null, null)));
        assertEquals(DefectStatus.IN_PROGRESS, defect.getStatus());

        MaintenanceShift shift = shift(2L, PossessionType.PARTIAL, ShiftStatus.IN_PROGRESS);
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        fixture.service.resolve(defect.getId(), new ResolveDefectRequest(" Dropper replaced ", shift.getId(), "Replacement", "1 dropper"));

        assertEquals(DefectStatus.RESOLVED, defect.getStatus());
        assertEquals(shift, defect.getResolvedInShift());
        assertEquals("Dropper replaced", defect.getResolutionNotes());
        assertEquals("Replacement", defect.getCorrectionType());
        assertNotNull(defect.getResolvedAt());
        verify(fixture.history).recordDefectChange(defect, "IN_PROGRESS", "RESOLVED", " Dropper replaced ");

        fixture.service.close(defect.getId(), new DefectCommentRequest("verified on site"));
        assertEquals(DefectStatus.CLOSED, defect.getStatus());
        verify(fixture.history).recordDefectChange(defect, "RESOLVED", "CLOSED", "verified on site");
        assertThrows(InvalidTransitionException.class, () -> fixture.service.close(defect.getId(), new DefectCommentRequest("again")));
        assertThrows(InvalidTransitionException.class, () -> fixture.service.resolve(defect.getId(), new ResolveDefectRequest("again", null, null, null)));
        assertThrows(InvalidTransitionException.class, () -> fixture.service.update(defect.getId(),
                new CatenaryDefectUpdateRequest(DefectSeverity.LOW, null, null, null, null, null, null, null)));

        // Con la orden completada la resolucion no necesita turno.
        MaintenanceOrder done = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.COMPLETED);
        CatenaryDefect afterOrder = defect(DefectStatus.IN_PROGRESS);
        afterOrder.setOrder(done);
        when(fixture.repository.findById(afterOrder.getId())).thenReturn(Optional.of(afterOrder));
        fixture.service.resolve(afterOrder.getId(), new ResolveDefectRequest("done with the order", null, null, null));
        assertEquals(DefectStatus.RESOLVED, afterOrder.getStatus());
        assertNull(afterOrder.getResolvedInShift());
    }

    @Test
    void aDefectIsNeverDetectedAfterNowSoItCanBeResolvedInTheSameShift() {
        // Una inspeccion fechada hoy en las primeras horas de una noche de Madrid: en UTC su dia aun no ha
        // empezado. El defecto que genera se detecta ahora, no a una hora que todavia no ha llegado.
        InspectionFixture fixture = new InspectionFixture();
        MaintenanceInspection tonight = inspection(InspectionResult.MAJOR_DEFECT);
        tonight.setInspectionDate(LocalDate.now(ZoneOffset.UTC).plusDays(1));
        MaintenanceInspection past = inspection(InspectionResult.MAJOR_DEFECT);
        when(fixture.repository.findById(tonight.getId())).thenReturn(Optional.of(tonight));
        when(fixture.repository.findById(past.getId())).thenReturn(Optional.of(past));
        when(fixture.codeGenerator.nextDefectCode()).thenReturn("DEF-000001", "DEF-000002");
        when(fixture.defectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Instant before = Instant.now();
        fixture.service.createDefect(tonight.getId(), new CreateDefectFromInspectionRequest(null, null, null, null));
        fixture.service.createDefect(past.getId(), new CreateDefectFromInspectionRequest(null, null, null, null));

        Instant detected = tonight.getGeneratedDefect().getDetectedAt();
        assertFalse(detected.isBefore(before));
        assertFalse(detected.isAfter(Instant.now()));
        assertEquals(Instant.parse("2026-01-28T00:00:00Z"), past.getGeneratedDefect().getDetectedAt(), "A past day still starts at midnight UTC");

        // Uno que ya estaba fechado en el futuro no se resuelve antes de detectarse: el CHECK de la base
        // lo rechazaba con un 409 generico.
        DefectFixture defects = new DefectFixture();
        CatenaryDefect future = defect(DefectStatus.OPEN);
        future.setDetectedAt(Instant.now().plus(Duration.ofHours(2)));
        when(defects.repository.findById(future.getId())).thenReturn(Optional.of(future));
        assertThrows(ValidationException.class, () -> defects.service.resolve(future.getId(), new ResolveDefectRequest("fixed", null, null, null)));
        assertEquals(DefectStatus.OPEN, future.getStatus());
        assertNull(future.getResolvedAt());
    }

    @Test
    void onlyOpenDefectsAreDiscardedAndLinkingAnOrderMovesThemInProgress() {
        DefectFixture fixture = new DefectFixture();
        CatenaryDefect open = defect(DefectStatus.OPEN);
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.PLANNED);
        when(fixture.repository.findById(open.getId())).thenReturn(Optional.of(open));
        when(fixture.lookups.order(order.getId())).thenReturn(order);

        fixture.service.linkOrder(open.getId(), order.getId());

        assertEquals(DefectStatus.IN_PROGRESS, open.getStatus());
        assertEquals(order, open.getOrder());
        verify(fixture.history).recordDefectChange(open, "OPEN", "IN_PROGRESS", "Linked to order " + order.getCode());

        // Reenlazar a otra orden no cambia el estado pero queda en el historial.
        MaintenanceOrder other = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.DRAFT);
        when(fixture.lookups.order(other.getId())).thenReturn(other);
        fixture.service.linkOrder(open.getId(), other.getId());
        assertEquals(DefectStatus.IN_PROGRESS, open.getStatus());
        assertEquals(other, open.getOrder());
        verify(fixture.history).recordDefectChange(open, "IN_PROGRESS", "IN_PROGRESS", "Re-linked to order " + other.getCode());

        MaintenanceOrder done = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.COMPLETED);
        when(fixture.lookups.order(done.getId())).thenReturn(done);
        assertThrows(InvalidTransitionException.class, () -> fixture.service.linkOrder(open.getId(), done.getId()));
        assertThrows(InvalidTransitionException.class, () -> fixture.service.discard(open.getId(), new DefectCommentRequest("duplicate")));

        CatenaryDefect fresh = defect(DefectStatus.OPEN);
        when(fixture.repository.findById(fresh.getId())).thenReturn(Optional.of(fresh));
        fixture.service.update(fresh.getId(), new CatenaryDefectUpdateRequest(DefectSeverity.CRITICAL, " Kink in the wire ", "measured 3 mm", null, null,
                LocalDate.of(2026, 2, 3), List.of("kink.jpg"), null));
        assertEquals(DefectSeverity.CRITICAL, fresh.getSeverity());
        assertEquals("Kink in the wire", fresh.getDescription());
        assertEquals(LocalDate.of(2026, 2, 3), fresh.getRepairPlannedDate());
        assertEquals(List.of("kink.jpg"), fresh.getPhotoRefs());

        fixture.service.discard(fresh.getId(), new DefectCommentRequest(" duplicate of DEF-000001 "));
        assertEquals(DefectStatus.DISCARDED, fresh.getStatus());
        assertEquals("duplicate of DEF-000001", fresh.getDiscardReason());
        assertThrows(InvalidTransitionException.class, () -> fixture.service.linkOrder(fresh.getId(), order.getId()));
        assertThrows(NotFoundException.class, () -> fixture.service.findById(UUID.randomUUID()));
    }

    // ------------------------------------------------------------- shifts (2)

    @Test
    void aShiftOpensAsManyBlockingDisconnectorsAsTheCutNeedsAndRejectsAnythingElse() {
        ShiftFixture fixture = new ShiftFixture();
        CatenaryAsset first = disconnector("HSA-NS5");
        CatenaryAsset second = disconnector("HSA-NS7");
        CatenaryAsset third = disconnector("HSA-NS9");
        CatenaryAsset profile = profile("12-2.27", "12847.990");
        for (CatenaryAsset asset : List.of(first, second, third, profile)) {
            when(fixture.lookups.asset(asset.getId())).thenReturn(asset);
        }
        when(fixture.codeGenerator.nextShiftCode()).thenReturn("SH-000002");
        Instant start = Instant.parse("2026-01-27T21:00:00Z");
        Instant end = Instant.parse("2026-01-28T05:00:00Z");

        fixture.service.create(shiftRequestWith(null, Set.of(first.getId(), second.getId(), third.getId()), start, end, null, null));
        MaintenanceShift saved = fixture.savedShift();
        assertEquals(Set.of(first, second, third), saved.getBlockingDisconnectors());

        assertThrows(ValidationException.class,
                () -> fixture.service.create(shiftRequestWith(null, Set.of(first.getId(), profile.getId()), start, end, null, null)),
                "Un perfil no es un seccionador");

        // Un update con conjunto vacio los quita; con null no los toca.
        when(fixture.lookups.shift(saved.getId())).thenReturn(saved);
        fixture.service.update(saved.getId(), new MaintenanceShiftUpdateRequest(null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null));
        assertEquals(3, saved.getBlockingDisconnectors().size());
        fixture.service.update(saved.getId(), new MaintenanceShiftUpdateRequest(null, null, null, null, null, null, null, Set.of(), null, null, null, null,
                null, null, null, null, null, null));
        assertTrue(saved.getBlockingDisconnectors().isEmpty());
    }

    @Test
    void creatingAShiftInheritsTheTeamBaseAndVehicleAndValidatesWindowRangeAndDisconnectors() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceTeam team = team("A");
        CatenaryAsset disconnector = disconnector("HSA-NS5");
        CatenaryAsset profile = profile("12-2.27", "12847.990");
        when(fixture.lookups.team(team.getId())).thenReturn(team);
        when(fixture.lookups.asset(disconnector.getId())).thenReturn(disconnector);
        when(fixture.lookups.asset(profile.getId())).thenReturn(profile);
        when(fixture.codeGenerator.nextShiftCode()).thenReturn("SH-000001");
        Instant start = Instant.parse("2026-01-27T21:00:00Z");
        Instant end = Instant.parse("2026-01-28T05:00:00Z");

        fixture.service.create(shiftRequest(team.getId(), disconnector.getId(), start, end, "12847.990", "14078.090"));

        MaintenanceShift saved = fixture.savedShift();
        assertEquals("SH-000001", saved.getCode());
        assertEquals("Rishpon", saved.getBaseName(), "Base and vehicle default to the team's");
        assertEquals("Vehicle A", saved.getVehicle());
        assertEquals(Set.of(disconnector), saved.getBlockingDisconnectors());
        assertEquals(ShiftStatus.PLANNED, saved.getStatus());
        assertEquals(Set.of(2L), saved.getTrackIds());

        assertThrows(ValidationException.class, () -> fixture.service.create(shiftRequest(team.getId(), profile.getId(), start, end, null, null)),
                "Only a DISCONNECTOR asset can open the block");
        assertThrows(ValidationException.class, () -> fixture.service.create(shiftRequest(null, null, end, start, null, null)));
        assertThrows(ValidationException.class, () -> fixture.service.create(shiftRequest(null, null, null, null, "14078.090", "12847.990")));
        verify(fixture.repository, times(1)).save(any());
    }

    @Test
    void aShiftStartsOnlyFromPlannedAndCancellingReturnsItsOpenTasksToTheOrderQueue() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = shift(2L, PossessionType.PARTIAL, ShiftStatus.PLANNED);
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        Instant actualStart = Instant.parse("2026-01-27T21:10:00Z");
        Instant cutoff = Instant.parse("2026-01-27T23:40:00Z");

        fixture.service.start(shift.getId(), new StartShiftRequest(actualStart, cutoff));

        assertEquals(ShiftStatus.IN_PROGRESS, shift.getStatus());
        assertEquals(actualStart, shift.getActualStart());
        assertEquals(cutoff, shift.getVoltageCutoffAt());
        assertThrows(InvalidTransitionException.class, () -> fixture.service.start(shift.getId(), new StartShiftRequest(null, null)));

        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceTask open = task(order, MaintenanceTaskStatus.IN_PROGRESS);
        open.setShift(shift);
        when(fixture.taskRepository.findByShiftIdAndStatusIn(eq(shift.getId()), any())).thenReturn(List.of(open));
        shift.setObservations("Rain expected");

        fixture.service.cancel(shift.getId(), new CancelShiftRequest("possession refused"));

        assertEquals(ShiftStatus.CANCELLED, shift.getStatus());
        assertEquals("Rain expected\nCancelled: possession refused", shift.getObservations());
        assertEquals(MaintenanceTaskStatus.PENDING, open.getStatus());
        assertNull(open.getShift());
        assertThrows(InvalidTransitionException.class, () -> fixture.service.cancel(shift.getId(), new CancelShiftRequest("again")));
        assertThrows(InvalidTransitionException.class, () -> fixture.service.close(shift.getId(), new CloseShiftRequest(null, null, null, null)));
        assertThrows(InvalidTransitionException.class, () -> fixture.service.update(shift.getId(), shiftUpdate(Set.of(1L), null)));
    }

    @Test
    void updatingAShiftKeepsAtLeastOneTrackAndRewritesTheTracksItCovers() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = shift(2L, PossessionType.PARTIAL, ShiftStatus.PLANNED);
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);

        assertThrows(ValidationException.class, () -> fixture.service.update(shift.getId(), shiftUpdate(Set.of(), null)));
        fixture.service.update(shift.getId(), shiftUpdate(Set.of(1L, 3L), PossessionType.FULL));

        assertEquals(Set.of(1L, 3L), shift.getTrackIds());
        assertEquals(PossessionType.FULL, shift.getPossessionType());
        assertTrue(shift.worksOn(3L));
        assertFalse(shift.worksOn(2L));
    }

    @Test
    void changingTheTracksOrThePossessionOfAShiftKeepsItsAssignedTasksWorkable() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = shift(Set.of(1L, 2L), PossessionType.FULL, ShiftStatus.PLANNED);
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        MaintenanceTask onTrack2 = task(order, MaintenanceTaskStatus.PENDING);
        onTrack2.setAsset(profile("13-2.01", "13007.290"));
        onTrack2.getTaskTypes().add(MaintenanceTaskType.builder().code("RG-03").description("Section insulators")
                .requiresFullPossession(true).orderIndex(3).build());
        when(fixture.taskRepository.findByShiftIdAndStatusIn(eq(shift.getId()), any())).thenReturn(List.of(onTrack2));

        ShiftException withoutItsTrack = assertThrows(ShiftException.class,
                () -> fixture.service.update(shift.getId(), shiftUpdate(Set.of(1L), null)));
        assertTrue(withoutItsTrack.getMessage().contains("Task " + onTrack2.getSequence()), withoutItsTrack.getMessage());
        assertTrue(withoutItsTrack.getMessage().endsWith("(POST /shifts/{id}/tasks/{taskId}/unassign)"), "It says how to take it out");
        shift.getTrackIds().addAll(Set.of(1L, 2L));
        assertThrows(ShiftException.class, () -> fixture.service.update(shift.getId(), shiftUpdate(null, PossessionType.PARTIAL)),
                "RG-03 needs full possession");
        shift.setPossessionType(PossessionType.FULL);
        verify(fixture.repository, never()).save(any());

        // Quitar la via que no usa ninguna tarea si se puede.
        fixture.service.update(shift.getId(), shiftUpdate(Set.of(2L), null));
        assertEquals(Set.of(2L), shift.getTrackIds());
    }

    @Test
    void closingAShiftWithoutAVoltageCutOffCountsFromTheActualStartAndHonoursAnExplicitNetTime() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift computed = shift(2L, PossessionType.PARTIAL, ShiftStatus.IN_PROGRESS);
        computed.setActualStart(Instant.parse("2026-01-27T21:10:00Z"));
        MaintenanceShift declared = shift(1L, PossessionType.FULL, ShiftStatus.IN_PROGRESS);
        declared.setActualStart(Instant.parse("2026-01-27T21:10:00Z"));
        when(fixture.lookups.shift(computed.getId())).thenReturn(computed);
        when(fixture.lookups.shift(declared.getId())).thenReturn(declared);

        fixture.service.close(computed.getId(), new CloseShiftRequest(Instant.parse("2026-01-28T01:10:00Z"), null, null, null));
        fixture.service.close(declared.getId(), new CloseShiftRequest(Instant.parse("2026-01-28T01:10:00Z"), null, 200, "one hour lost"));

        assertEquals(240, computed.getNetWorkMinutes());
        assertEquals(200, declared.getNetWorkMinutes());
        assertEquals("one hour lost", declared.getObservations());
        assertEquals(ShiftStatus.CLOSED, computed.getStatus());
        assertThrows(InvalidTransitionException.class, () -> fixture.service.close(computed.getId(), new CloseShiftRequest(null, null, null, null)));
    }

    @Test
    void aVoltageCutOffAfterTheEndOfTheShiftIsRejectedAndTheShiftStaysInProgress() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = shift(2L, PossessionType.PARTIAL, ShiftStatus.IN_PROGRESS);
        shift.setActualStart(Instant.parse("2026-01-27T21:10:00Z"));
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);

        // El del cierre y el que ya traia del inicio: el tiempo neto salia negativo, y @PositiveOrZero lo
        // rechazaba al confirmar la transaccion, con un 500.
        assertThrows(ValidationException.class, () -> fixture.service.close(shift.getId(),
                new CloseShiftRequest(Instant.parse("2026-01-28T01:10:00Z"), Instant.parse("2026-01-28T01:20:00Z"), null, null)));
        shift.setVoltageCutoffAt(Instant.parse("2026-01-28T02:00:00Z"));
        assertThrows(ValidationException.class, () -> fixture.service.close(shift.getId(),
                new CloseShiftRequest(Instant.parse("2026-01-28T01:10:00Z"), null, null, null)));

        assertEquals(ShiftStatus.IN_PROGRESS, shift.getStatus());
        assertNull(shift.getNetWorkMinutes());
        verify(fixture.repository, never()).save(any());

        // Un corte justo al final es un tiempo neto de cero, no un error.
        fixture.service.close(shift.getId(), new CloseShiftRequest(Instant.parse("2026-01-28T02:00:00Z"), null, null, null));
        assertEquals(0, shift.getNetWorkMinutes());
    }

    @Test
    void theShiftReportListsOneRowPerTaskByKpWithDefectsMaterialsAndRepairDates() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = shift(2L, PossessionType.PARTIAL, ShiftStatus.CLOSED);
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceTask far = task(order, MaintenanceTaskStatus.COMPLETED);
        far.setAsset(profile("13-2.02", "13060.290"));
        List<MaintenanceTaskType> types = new ArrayList<>(taskTypes("RG-01", "RG-12"));
        far.getTaskTypes().add(types.get(1));
        far.getTaskTypes().add(types.get(0));
        MaintenanceTask near = task(order, MaintenanceTaskStatus.COMPLETED);
        near.setAsset(profile("12-2.27", "12847.990"));
        near.setNotes("Insulators cleaned");
        MaintenanceTask pending = task(order, MaintenanceTaskStatus.PENDING);
        pending.setAsset(profile("14-2.02", "14078.090"));
        CatenaryDefect openDefect = defect(DefectStatus.OPEN);
        openDefect.setFoundInTask(far);
        openDefect.setRepairPlannedDate(LocalDate.of(2026, 2, 3));
        CatenaryDefect resolvedDefect = defect(DefectStatus.RESOLVED);
        resolvedDefect.setFoundInTask(near);
        MaintenanceMaterialUsage usage = line(order);
        usage.setTask(near);
        usage.setConsumedQuantity(new BigDecimal("2"));
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        when(fixture.taskRepository.findByShiftIdOrderBySequenceAsc(shift.getId())).thenReturn(List.of(far, near, pending));
        when(fixture.defectRepository.findByFoundInTaskIdIn(any())).thenReturn(List.of(openDefect, resolvedDefect));
        when(fixture.defectRepository.countByResolvedInShiftId(shift.getId())).thenReturn(1L);
        when(fixture.materialRepository.findByTaskIdIn(any())).thenReturn(List.of(usage));

        ShiftReportResponse report = fixture.service.report(shift.getId());

        assertEquals(List.of("12-2.27", "13-2.02", "14-2.02"), report.rows().stream().map(ShiftReportRowResponse::profileName).toList());
        assertEquals(List.of(1, 2, 3), report.rows().stream().map(ShiftReportRowResponse::number).toList());
        ShiftReportRowResponse nearRow = report.rows().get(0);
        assertTrue(nearRow.workComplete());
        assertEquals(List.of("2 ud GA70"), nearRow.materials());
        assertEquals("Insulators cleaned", nearRow.worksPerformed());
        ShiftReportRowResponse farRow = report.rows().get(1);
        assertFalse(farRow.workComplete(), "A pending defect leaves the profile unfinished");
        assertEquals(LocalDate.of(2026, 2, 3), farRow.repairPlannedDate());
        assertEquals(List.of("RG-01", "RG-12"), farRow.taskTypeCodes(), "Catalogue order, not insertion order");
        assertFalse(report.rows().get(2).workComplete());
        assertEquals(2, report.tasksCompleted());
        assertEquals(1, report.tasksPending());
        assertEquals(2, report.profilesReviewed());
        assertEquals(2, report.defectsFound());
        assertEquals(1, report.defectsResolved());
        assertEquals(0, report.inspectionsRecorded());
        assertEquals(List.of(), report.inspections());
    }

    @Test
    void theShiftReportListsTheInspectionsRecordedInTheShiftWithWhatTheyOpened() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = shift(Set.of(2L, 3L), PossessionType.FULL, ShiftStatus.CLOSED);
        MaintenanceInspection unsafe = inspection(InspectionResult.UNSAFE);
        unsafe.setAsset(insulator("SI-W41", 3L, 2L, "12920.000", "12960.000"));
        unsafe.setTrackId(3L);
        unsafe.setKp(new BigDecimal("12921.500"));
        unsafe.setInspector("dana");
        unsafe.setGeneratedDefect(CatenaryDefect.builder().code("DEF-000007").build());
        unsafe.setGeneratedOrder(MaintenanceOrder.builder().code("MO-000031").build());
        MaintenanceInspection ok = inspection(InspectionResult.OK);
        ok.setDetectedDefects(null);
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        when(fixture.taskRepository.findByShiftIdOrderBySequenceAsc(shift.getId())).thenReturn(List.of());
        when(fixture.inspectionRepository.findByShiftIdOrderByInspectionDateAscCodeAsc(shift.getId())).thenReturn(List.of(unsafe, ok));

        ShiftReportResponse report = fixture.service.report(shift.getId());

        assertEquals(2, report.inspectionsRecorded(), "The service counts them, the clients do not add");
        ShiftReportInspectionResponse first = report.inspections().getFirst();
        assertEquals("INS-UNSAFE", first.code());
        assertEquals("SIN-SI-W41", first.assetCode());
        assertEquals(CatenaryAssetType.SECTION_INSULATOR, first.assetType());
        assertEquals(3L, first.trackId());
        assertEquals(new BigDecimal("12921.500"), first.kp(), "The kp written in the inspection, not the asset's");
        assertEquals(InspectionResult.UNSAFE, first.result());
        assertEquals("Cracked insulator", first.detectedDefects());
        assertEquals("DEF-000007", first.generatedDefectCode());
        assertEquals("MO-000031", first.generatedOrderCode());
        ShiftReportInspectionResponse second = report.inspections().get(1);
        assertEquals(2L, second.trackId(), "Without its own track it is the asset's");
        assertEquals(new BigDecimal("13499.290"), second.kp());
        assertNull(second.generatedDefectCode());
        assertEquals(List.of(), report.rows());
    }

    // ------------------------------------------------------------------ teams

    @Test
    void teamCodesAreNormalisedAndMustBeUnique() {
        TeamFixture fixture = new TeamFixture();
        when(fixture.repository.existsByCode("C")).thenReturn(true);

        assertThrows(DuplicateCodeException.class, () -> fixture.service.create(new MaintenanceTeamRequest(" c ", "Team C", null, null, null, null)));
        fixture.service.create(new MaintenanceTeamRequest(" d ", " Team D ", "Herzliya", "Vehicle D", null, Set.of(6L)));

        ArgumentCaptor<MaintenanceTeam> captor = ArgumentCaptor.forClass(MaintenanceTeam.class);
        verify(fixture.repository).save(captor.capture());
        MaintenanceTeam saved = captor.getValue();
        assertEquals("D", saved.getCode());
        assertEquals("Team D", saved.getName());
        assertTrue(saved.getActive(), "Active unless told otherwise");
        assertEquals(Set.of(6L), saved.getExecutionPackageIds());

        ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
        when(fixture.lookups.team(saved.getId())).thenReturn(saved);
        assertThrows(DuplicateCodeException.class, () -> fixture.service.update(saved.getId(), new MaintenanceTeamRequest("C", "Team C", null, null, null, null)));
        fixture.service.update(saved.getId(), new MaintenanceTeamRequest("d", "Team D bis", null, null, false, Set.of(7L, 8L)));

        assertEquals("D", saved.getCode(), "Keeping its own code is not a duplicate");
        assertEquals("Team D bis", saved.getName());
        assertFalse(saved.getActive());
        assertEquals(Set.of(7L, 8L), saved.getExecutionPackageIds());
    }

    @Test
    void aTeamOfMtoResourcesIsNotEditedHere() {
        TeamFixture fixture = new TeamFixture();
        MaintenanceTeam synced = team("A");
        ReflectionTestUtils.setField(synced, "sourceService", "mto-resources");
        ReflectionTestUtils.setField(synced, "sourceEntityId", UUID.randomUUID());
        when(fixture.lookups.team(synced.getId())).thenReturn(synced);

        assertThrows(SynchronizedTeamException.class, () -> fixture.service.update(synced.getId(),
                new MaintenanceTeamRequest("A", "Renamed", null, null, null, null)));
        assertEquals("Team A", synced.getName());
    }

    // ------------------------------------------------------------ shift crew

    @Test
    void aShiftOfASynchronizedTeamCopiesItsLeaderSupervisorLinemenAndDefaultVehicle() {
        ShiftFixture fixture = new ShiftFixture();
        CrewPerson leader = crewPerson("TEAM_LEADER");
        CrewPerson supervisor = crewPerson("SUPERVISOR");
        FleetVehicle towerCar = fleetVehicle("RAIL_ONLY", true, false, 8);
        MaintenanceTeam team = syncedTeam(leader, supervisor, towerCar, crewPerson("WORKER"), crewPerson("WORKER"), crewPerson("WORKER"));
        when(fixture.lookups.team(team.getId())).thenReturn(team);
        when(fixture.vehicles.existsById(towerCar.getId())).thenReturn(true);
        when(fixture.vehicles.findById(towerCar.getId())).thenReturn(Optional.of(towerCar));
        when(fixture.codeGenerator.nextShiftCode()).thenReturn("SH-000100");

        fixture.service.create(new MaintenanceShiftRequest(LocalDate.now(), team.getId(), null, null, PossessionType.FULL, null, null,
                null, null, null, null, Set.of(2L), null, null, null, null, null));

        MaintenanceShift saved = fixture.savedShift();
        assertEquals(leader, saved.getTeamLeader());
        assertEquals(supervisor, saved.getSupervisor());
        assertEquals(3, saved.getLinemen().size());
        assertEquals(List.of(towerCar.getId()), saved.getVehicles().stream().map(vehicle -> vehicle.getVehicle().getId()).toList());
        assertNull(saved.getBaseName(), "A synchronized team has no base");
    }

    @Test
    void aShiftWithACrewStartsOnlyWithItsLeaderAndTwoToSixLinemen() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift withoutLeader = crewedShift(null, 3);
        MaintenanceShift withOne = crewedShift(crewPerson("TEAM_LEADER"), 1);
        MaintenanceShift complete = crewedShift(crewPerson("TEAM_LEADER"), 2);
        for (MaintenanceShift shift : List.of(withoutLeader, withOne, complete)) {
            when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        }

        assertThrows(ShiftException.class, () -> fixture.service.start(withoutLeader.getId(), new StartShiftRequest(null, null)));
        assertThrows(ShiftException.class, () -> fixture.service.start(withOne.getId(), new StartShiftRequest(null, null)));
        assertEquals(ShiftStatus.PLANNED, withOne.getStatus(), "Planned, it may still be incomplete");
        fixture.service.start(complete.getId(), new StartShiftRequest(null, null));
        assertEquals(ShiftStatus.IN_PROGRESS, complete.getStatus());
        assertEquals(List.of("shift.started"), fixture.events.names());
    }

    @Test
    void sevenLinemenTheLeaderAsLinemanOrAWorkerAsLeaderAreRefusedEvenWhilePlanned() {
        ShiftFixture fixture = new ShiftFixture();
        CrewPerson leader = crewPerson("TEAM_LEADER");
        MaintenanceShift seven = crewedShift(leader, 7);
        MaintenanceShift leaderAsLineman = crewedShift(leader, 2);
        leaderAsLineman.getLinemen().add(leader);
        MaintenanceShift workerLeads = crewedShift(crewPerson("WORKER"), 2);
        for (MaintenanceShift shift : List.of(seven, leaderAsLineman, workerLeads)) {
            when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
            assertThrows(ShiftException.class, () -> fixture.service.update(shift.getId(), observations("checked")), shift.getCode());
        }
    }

    @Test
    void aShiftOfALocalTeamWithoutCrewStartsAsBefore() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = shift(2L, PossessionType.FULL, ShiftStatus.PLANNED);
        shift.setTeam(team("B"));
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);

        fixture.service.start(shift.getId(), new StartShiftRequest(null, null));

        assertEquals(ShiftStatus.IN_PROGRESS, shift.getStatus());
    }

    @Test
    void aVehicleOrAPersonInAnotherOpenShiftOfTheSameNightIsRefused() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = crewedShift(crewPerson("TEAM_LEADER"), 2);
        shift.getVehicles().add(MaintenanceShiftVehicle.of(shift, fleetVehicle("RAIL_ONLY", true, false, 8)));
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        when(fixture.repository.findOpenShiftCodesWithVehicles(any(), eq(shift.getShiftDate()), any(), eq(shift.getId())))
                .thenReturn(List.of("SH-000099"));

        ShiftException refused = assertThrows(ShiftException.class, () -> fixture.service.start(shift.getId(), new StartShiftRequest(null, null)));
        assertTrue(refused.getMessage().contains("SH-000099"), refused.getMessage());
        verify(fixture.repository, org.mockito.Mockito.atLeastOnce()).lockResource(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void anInstrumentInAnotherOpenShiftOfTheSameNightIsRefused() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = shift(2L, PossessionType.FULL, ShiftStatus.PLANNED);
        MaintenanceInstrument detector = instrument("VD-02", true, LocalDate.now().plusMonths(3));
        shift.getInstruments().add(detector);
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        when(fixture.repository.findOpenShiftCodesWithInstruments(eq(Set.of(detector.getId())), eq(shift.getShiftDate()), any(),
                eq(shift.getId()))).thenReturn(List.of("SH-000099"));

        ShiftException refused = assertThrows(ShiftException.class, () -> fixture.service.start(shift.getId(), new StartShiftRequest(null, null)));
        assertTrue(refused.getMessage().contains("SH-000099") && refused.getMessage().contains("an instrument works in one shift a night"),
                refused.getMessage());
        verify(fixture.repository).lockResource(("mto-maintenance:shift-resource:instrument:" + detector.getId()).hashCode());
    }

    @Test
    void whoOrWhatIsNotAvailableOnTheShiftDateDoesNotGetIntoTheShift() {
        ShiftFixture fixture = new ShiftFixture();
        CrewPerson leader = crewPerson("TEAM_LEADER");
        MaintenanceShift shift = crewedShift(leader, 2);
        LocalDate night = shift.getShiftDate();
        FleetVehicle towerCar = fleetVehicle("RAIL_ONLY", true, false, 8);
        shift.getVehicles().add(MaintenanceShiftVehicle.of(shift, towerCar));
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        ReflectionTestUtils.setField(leader, "unavailable", List.of(new UnavailablePeriod(night.minusDays(2), night)));

        ShiftException absent = assertThrows(ShiftException.class, () -> fixture.service.start(shift.getId(), new StartShiftRequest(null, null)));
        assertTrue(absent.getMessage().contains(leader.getFullName() + " (" + leader.getCode() + ") (unavailable " + night.minusDays(2) + " to "
                + night + ")"), absent.getMessage());

        ReflectionTestUtils.setField(leader, "unavailable", List.of(new UnavailablePeriod(night.minusDays(5), night.minusDays(1))));
        ReflectionTestUtils.setField(towerCar, "unavailable", List.of(new UnavailablePeriod(night.minusDays(1), null)));
        ShiftException inRepair = assertThrows(ShiftException.class, () -> fixture.service.start(shift.getId(), new StartShiftRequest(null, null)));
        assertTrue(inRepair.getMessage().contains("vehicle " + towerCar.getCode() + " (unavailable from " + night.minusDays(1) + ")"),
                inRepair.getMessage());

        ReflectionTestUtils.setField(towerCar, "unavailable", List.of(new UnavailablePeriod(night.plusDays(1), night.plusDays(3))));
        fixture.service.start(shift.getId(), new StartShiftRequest(null, null));
        assertEquals(ShiftStatus.IN_PROGRESS, shift.getStatus(), "A period that ended the night before, or starts the night after, does not count");
    }

    @Test
    void twoNightsInARowOfTheSamePersonNeedTheMinimumRestBetweenThem() {
        ShiftFixture fixture = new ShiftFixture();
        CrewPerson leader = crewPerson("TEAM_LEADER");
        MaintenanceShift tonight = crewedShift(leader, 2);
        LocalDate date = tonight.getShiftDate();
        Instant start = date.atTime(21, 0).toInstant(ZoneOffset.UTC);
        tonight.setPlannedStart(start);
        tonight.setPlannedEnd(start.plus(Duration.ofHours(8)));
        MaintenanceShift lastNight = shift(2L, PossessionType.FULL, ShiftStatus.CLOSED);
        ReflectionTestUtils.setField(lastNight, "code", "SH-LAST");
        lastNight.setShiftDate(date.minusDays(1));
        lastNight.setTeamLeader(crewPerson("TEAM_LEADER"));
        lastNight.getLinemen().add(leader);
        lastNight.setActualStart(date.minusDays(1).atTime(21, 0).toInstant(ZoneOffset.UTC));
        lastNight.setActualEnd(date.atTime(13, 0).toInstant(ZoneOffset.UTC));
        when(fixture.lookups.shift(tonight.getId())).thenReturn(tonight);
        when(fixture.repository.findShiftsOfCrewOn(any(), eq(List.of(date.minusDays(1), date.plusDays(1))), any(), eq(tonight.getId())))
                .thenReturn(List.of(lastNight));

        ShiftException tired = assertThrows(ShiftException.class, () -> fixture.service.start(tonight.getId(), new StartShiftRequest(start, null)));
        assertTrue(tired.getMessage().contains(leader.getFullName() + " (" + leader.getCode() + ") would rest 8h between shift SH-LAST")
                && tired.getMessage().contains("at least 12h between two nights"), tired.getMessage());

        lastNight.setActualEnd(date.atTime(8, 0).toInstant(ZoneOffset.UTC));
        fixture.service.start(tonight.getId(), new StartShiftRequest(start, null));
        assertEquals(ShiftStatus.IN_PROGRESS, tonight.getStatus(), "Thirteen hours are enough");
    }

    @Test
    void theScopeOfWhoWritesComesFromTheTeamsOfHisRecordAndChiefsAndServiceAccountsHaveNone() {
        ScopeMocks mocks = new ScopeMocks();
        MaintenanceScope scope = mocks.scope();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        order.setExecutionPackageId(14L);

        scope.requireOrder(order);

        CrewPerson supervisor = crewPerson("SUPERVISOR");
        mocks.actAs(supervisor, List.of(14L, 15L), List.of());
        scope.requireOrder(order);
        order.setExecutionPackageId(9L);
        ScopeException outside = assertThrows(ScopeException.class, () -> scope.requireOrder(order));
        assertEquals("MO-DRAFT is in execution package 9, outside the scope of " + supervisor.getFullName() + " (" + supervisor.getCode()
                + ") (14, 15)", outside.getMessage());
        order.setExecutionPackageId(null);
        assertThrows(ScopeException.class, () -> scope.requireOrder(order), "Without an execution package only who has no limit writes");

        MaintenanceTeam team = team("A");
        MaintenanceShift shift = shift(2L, PossessionType.FULL, ShiftStatus.PLANNED);
        shift.setTeam(team);
        assertThrows(ScopeException.class, () -> scope.requireShift(shift));
        mocks.actAs(supervisor, List.of(14L), List.of(team.getId()));
        scope.requireShift(shift);
        shift.setExecutionPackageId(9L);
        assertThrows(ScopeException.class, () -> scope.requireShift(shift), "With its package, the package decides");

        mocks.actAs(crewPerson("CHIEF_MAINTENANCE"), List.of(), List.of());
        scope.requireOrder(order);
        CrewPerson loner = crewPerson("TEAM_LEADER");
        mocks.actAs(loner, List.of(), List.of());
        order.setExecutionPackageId(14L);
        assertTrue(assertThrows(ScopeException.class, () -> scope.requireOrder(order)).getMessage()
                .endsWith("(none: you are in no team of mto-resources)"));
        new MaintenanceScope(mocks.currentUser, mocks.people, mocks.teams, new ScopeProperties(false)).requireOrder(order);
    }

    @Test
    void outsideItsScopeATeamLeaderChangesNothing() {
        CrewPerson leader = crewPerson("TEAM_LEADER");
        List<Long> mine = List.of(4L);

        OrderFixture orders = new OrderFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        order.setExecutionPackageId(14L);
        when(orders.lookups.order(order.getId())).thenReturn(order);
        orders.scopeMocks.actAs(leader, mine, List.of());
        assertThrows(ScopeException.class, () -> orders.service.cancel(order.getId(), new CancelOrderRequest("out of scope")));
        assertEquals(MaintenanceOrderStatus.DRAFT, order.getStatus());

        TaskFixture tasks = new TaskFixture();
        MaintenanceTask task = task(order, MaintenanceTaskStatus.PENDING);
        when(tasks.lookups.order(order.getId())).thenReturn(order);
        when(tasks.repository.findByIdAndOrderId(task.getId(), order.getId())).thenReturn(Optional.of(task));
        tasks.scopeMocks.actAs(leader, mine, List.of());
        assertThrows(ScopeException.class, () -> tasks.service.cancel(order.getId(), task.getId(), new CancelTaskRequest("out of scope")));
        assertEquals(MaintenanceTaskStatus.PENDING, task.getStatus());

        ShiftFixture shifts = new ShiftFixture();
        MaintenanceShift shift = crewedShift(crewPerson("TEAM_LEADER"), 2);
        shift.setExecutionPackageId(14L);
        when(shifts.lookups.shift(shift.getId())).thenReturn(shift);
        shifts.scopeMocks.actAs(leader, mine, List.of());
        assertThrows(ScopeException.class, () -> shifts.service.start(shift.getId(), new StartShiftRequest(null, null)));
        assertEquals(ShiftStatus.PLANNED, shift.getStatus());

        AssetFixture assets = new AssetFixture();
        CatenaryAsset section = trackSection();
        when(assets.lookups.asset(section.getId())).thenReturn(section);
        assets.scopeMocks.actAs(leader, mine, List.of());
        assertThrows(ScopeException.class, () -> assets.service.disable(section.getId()), "The section is of package 6");

        DefectFixture defects = new DefectFixture();
        CatenaryDefect defect = CatenaryDefect.builder().code("DEF-9").asset(section).severity(DefectSeverity.LOW).build();
        defect.locateAt(section);
        ReflectionTestUtils.setField(defect, "id", UUID.randomUUID());
        when(defects.repository.findById(defect.getId())).thenReturn(Optional.of(defect));
        defects.scopeMocks.actAs(leader, mine, List.of());
        assertThrows(ScopeException.class, () -> defects.service.close(defect.getId(), new DefectCommentRequest("out of scope")));

        MaterialFixture materials = new MaterialFixture();
        when(materials.lookups.order(order.getId())).thenReturn(order);
        materials.scopeMocks.actAs(leader, mine, List.of());
        assertThrows(ScopeException.class, () -> materials.service.sync(order.getId(), UUID.randomUUID()));
    }

    @Test
    void aTaskTypeThatRequiresEquipmentIsOnlyWorkedInAShiftThatCarriesIt() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceTask task = task(order, MaintenanceTaskStatus.PENDING);
        task.setAsset(profile("12-2.27", "12847.990"));
        Set<MaintenanceTaskType> types = taskTypes("RP-09");
        types.iterator().next().getRequiredEquipment().addAll(EnumSet.of(TaskEquipment.EARTHING, TaskEquipment.INSULATED_PLATFORM));
        task.getTaskTypes().addAll(types);
        MaintenanceShift shift = shift(2L, PossessionType.FULL, ShiftStatus.PLANNED);
        when(fixture.repository.findById(task.getId())).thenReturn(Optional.of(task));
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);

        ShiftException bare = assertThrows(ShiftException.class, () -> fixture.service.assignToShift(shift.getId(), task.getId()));
        assertTrue(bare.getMessage().contains("needs an insulated platform") && bare.getMessage().contains("and earthing"), bare.getMessage());

        FleetVehicle platform = fleetVehicle("ROAD_RAIL", true, false, 3);
        ReflectionTestUtils.setField(platform, "capabilities", List.of("INSULATED_PLATFORM"));
        shift.getVehicles().add(MaintenanceShiftVehicle.of(shift, platform));
        ShiftException noEarthing = assertThrows(ShiftException.class, () -> fixture.service.assignToShift(shift.getId(), task.getId()));
        assertFalse(noEarthing.getMessage().contains("insulated platform"), noEarthing.getMessage());

        MaintenanceInstrument retired = instrument("EP-01", false, null);
        ReflectionTestUtils.setField(retired, "kind", ShiftRules.EARTHING_POLE);
        shift.getInstruments().add(retired);
        assertThrows(ShiftException.class, () -> fixture.service.assignToShift(shift.getId(), task.getId()), "A retired pole does not earth");
        MaintenanceInstrument pole = instrument("EP-02", true, LocalDate.now().plusMonths(3));
        ReflectionTestUtils.setField(pole, "kind", ShiftRules.EARTHING_POLE);
        shift.getInstruments().add(pole);

        fixture.service.assignToShift(shift.getId(), task.getId());
        assertEquals(shift, task.getShift());
        ReflectionTestUtils.setField(platform, "capabilities", List.of("EARTHING", "INSULATED_PLATFORM"));
        shift.getInstruments().clear();
        assertTrue(ShiftRules.carries(shift, TaskEquipment.EARTHING), "A vehicle that earths is enough too");
    }

    @Test
    void theEquipmentATaskTypeRequiresIsReplacedWholeWithTheVersionReadAndNullEmptiesIt() {
        MaintenanceTaskTypeRepository repository = mock(MaintenanceTaskTypeRepository.class);
        MaintenanceTaskTypeMapper mapper = mock(MaintenanceTaskTypeMapper.class);
        MaintenanceTaskTypeServiceImpl service = new MaintenanceTaskTypeServiceImpl(repository, mapper);
        MaintenanceTaskType type = taskTypes("RP-09").iterator().next();
        ReflectionTestUtils.setField(type, "version", 3L);
        when(repository.findByCode("RP-09")).thenReturn(Optional.of(type));
        when(repository.saveAndFlush(type)).thenReturn(type);

        service.patch(" rp-09 ", MergePatch.of(new MaintenanceTaskTypeUpdateRequest(Set.of(TaskEquipment.EARTHING), 3L)));
        assertEquals(EnumSet.of(TaskEquipment.EARTHING), type.getRequiredEquipment());
        service.patch("RP-09", MergePatch.of(new MaintenanceTaskTypeUpdateRequest(Set.of(TaskEquipment.INSULATED_PLATFORM), null)));
        assertEquals(EnumSet.of(TaskEquipment.INSULATED_PLATFORM), type.getRequiredEquipment(), "The set replaces the previous one");
        service.patch("RP-09", new MergePatch<>(new MaintenanceTaskTypeUpdateRequest(null, null), Set.of("requiredEquipment")));
        assertTrue(type.getRequiredEquipment().isEmpty());

        assertThrows(StaleVersionException.class, () -> service.patch("RP-09",
                MergePatch.of(new MaintenanceTaskTypeUpdateRequest(Set.of(TaskEquipment.EARTHING), 2L))));
    }

    @Test
    void theCrewFitsInTheSeatsAndAWagonGoesWithSomethingThatTowsIt() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift crowded = crewedShift(crewPerson("TEAM_LEADER"), 4);
        crowded.getVehicles().add(MaintenanceShiftVehicle.of(crowded, fleetVehicle("ROAD_RAIL", true, false, 3)));
        MaintenanceShift wagonAlone = crewedShift(crewPerson("TEAM_LEADER"), 2);
        wagonAlone.getVehicles().add(MaintenanceShiftVehicle.of(wagonAlone, fleetVehicle("RAIL_ONLY", false, false, null)));
        MaintenanceShift towed = crewedShift(crewPerson("TEAM_LEADER"), 2);
        towed.getVehicles().add(MaintenanceShiftVehicle.of(towed, fleetVehicle("RAIL_ONLY", false, false, null)));
        towed.getVehicles().add(MaintenanceShiftVehicle.of(towed, fleetVehicle("RAIL_ONLY", true, true, 8)));
        for (MaintenanceShift shift : List.of(crowded, wagonAlone, towed)) {
            when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        }

        assertThrows(ShiftException.class, () -> fixture.service.start(crowded.getId(), new StartShiftRequest(null, null)), "5 people, 3 seats");
        assertThrows(ShiftException.class, () -> fixture.service.start(wagonAlone.getId(), new StartShiftRequest(null, null)));
        fixture.service.start(towed.getId(), new StartShiftRequest(null, null));
        assertEquals(ShiftStatus.IN_PROGRESS, towed.getStatus());
    }

    @Test
    void closingSaysWhereEachVehicleWasLeftAndARailOnlyOneStaysOnATrack() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = crewedShift(crewPerson("TEAM_LEADER"), 2);
        shift.setStatus(ShiftStatus.IN_PROGRESS);
        shift.setActualStart(Instant.parse("2026-01-27T21:10:00Z"));
        FleetVehicle towerCar = fleetVehicle("RAIL_ONLY", true, false, 8);
        shift.getVehicles().add(MaintenanceShiftVehicle.of(shift, towerCar));
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        Instant end = Instant.parse("2026-01-28T04:30:00Z");

        assertThrows(ShiftException.class, () -> fixture.service.close(shift.getId(), new CloseShiftRequest(end, null, null, null,
                List.of(new VehicleParkingRequest(towerCar.getId(), null, null, null, "Car park by the level crossing")))));
        assertThrows(ValidationException.class, () -> fixture.service.close(shift.getId(), new CloseShiftRequest(end, null, null, null,
                List.of(new VehicleParkingRequest(UUID.randomUUID(), null, 31L, null, null)))), "Not a vehicle of the shift");
        fixture.service.close(shift.getId(), new CloseShiftRequest(end, null, null, null,
                List.of(new VehicleParkingRequest(towerCar.getId(), null, 31L, new BigDecimal("4.250"), null))));

        DomainEvent closed = fixture.events.published.getLast();
        assertEquals("closed", closed.eventName());
        Map<?, ?> vehicle = (Map<?, ?>) ((List<?>) closed.values().get("vehicles")).getFirst();
        assertEquals(31L, ((Map<?, ?>) vehicle.get("parking")).get("trackId"));
        assertEquals(List.of(towerCar.getId().toString()), closed.values().get("vehicleIds"));
    }

    @Test
    void cancellingAShiftTellsMtoResources() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = shift(2L, PossessionType.FULL, ShiftStatus.IN_PROGRESS);
        shift.setActualStart(Instant.parse("2026-01-27T21:10:00Z"));
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);

        fixture.service.cancel(shift.getId(), new CancelShiftRequest("possession withdrawn"));

        assertEquals(List.of("shift.cancelled"), fixture.events.names());
        assertEquals("2026-01-27T21:10:00Z", String.valueOf(fixture.events.published.getFirst().values().get("actualStart")));
    }

    // ------------------------------------------------- qualifications and expiries

    @Test
    void somethingExpiredOnTheShiftDateDoesNotGetIntoAShiftOnCreateNorOnChange() {
        ShiftFixture fixture = new ShiftFixture();
        LocalDate night = LocalDate.now().plusDays(7);
        CrewPerson leader = withQualification(crewPerson("TEAM_LEADER"), "RAIL-DRIVER", night.minusDays(1));
        when(fixture.people.findById(leader.getId())).thenReturn(Optional.of(leader));
        when(fixture.codeGenerator.nextShiftCode()).thenReturn("SH-000200");

        ShiftException person = assertThrows(ShiftException.class, () -> fixture.service.create(shiftWith(night, leader.getId(), null)),
                "Valid today, expired on the night of the shift");
        assertTrue(person.getMessage().contains("qualification RAIL-DRIVER expired on " + night.minusDays(1)), person.getMessage());
        verify(fixture.repository, never()).save(any());

        CrewPerson renewed = withQualification(crewPerson("TEAM_LEADER"), "RAIL-DRIVER", night);
        MaintenanceInstrument expiredCalibration = instrument("VD-02", true, night.minusDays(1));
        when(fixture.people.findById(renewed.getId())).thenReturn(Optional.of(renewed));
        when(fixture.instruments.findById(expiredCalibration.getId())).thenReturn(Optional.of(expiredCalibration));
        ShiftException calibration = assertThrows(ShiftException.class,
                () -> fixture.service.create(shiftWith(night, renewed.getId(), Set.of(expiredCalibration.getId()))));
        assertTrue(calibration.getMessage().contains("instrument VD-02: calibration expired"), calibration.getMessage());

        // Al cambiarlo, un vehiculo con un documento caducado tampoco entra, aunque el turno siga planificado.
        MaintenanceShift planned = crewedShift(crewPerson("TEAM_LEADER"), 2);
        planned.setShiftDate(night);
        FleetVehicle uninsured = withDocument(fleetVehicle("ROAD_RAIL", true, false, 5), "INSURANCE", night.minusDays(2));
        when(fixture.lookups.shift(planned.getId())).thenReturn(planned);
        when(fixture.vehicles.findById(uninsured.getId())).thenReturn(Optional.of(uninsured));
        ShiftException document = assertThrows(ShiftException.class, () -> fixture.service.update(planned.getId(),
                new MaintenanceShiftUpdateRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, Set.of(uninsured.getId()), null, null, null, null)));
        assertTrue(document.getMessage().contains("vehicle " + uninsured.getCode() + ": INSURANCE"), document.getMessage());

        // Un instrumento retirado en mto-resources tampoco.
        MaintenanceShift another = crewedShift(crewPerson("TEAM_LEADER"), 2);
        MaintenanceInstrument retired = instrument("EP-09", false, null);
        when(fixture.lookups.shift(another.getId())).thenReturn(another);
        when(fixture.instruments.findById(retired.getId())).thenReturn(Optional.of(retired));
        ShiftException instrument = assertThrows(ShiftException.class,
                () -> fixture.service.update(another.getId(), instruments(Set.of(retired.getId()))));
        assertTrue(instrument.getMessage().contains("instrument EP-09: retired in mto-resources"), instrument.getMessage());
    }

    @Test
    void theInstrumentsOfAShiftAreASetReplacedWholeAndAnUnknownOneIsMsi404() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = shift(2L, PossessionType.FULL, ShiftStatus.PLANNED);
        MaintenanceInstrument pole = instrument("EP-07", true, LocalDate.now().plusYears(1));
        MaintenanceInstrument detector = instrument("VD-02", true, null);
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        when(fixture.instruments.findById(pole.getId())).thenReturn(Optional.of(pole));
        when(fixture.instruments.findById(detector.getId())).thenReturn(Optional.of(detector));

        fixture.service.update(shift.getId(), instruments(Set.of(detector.getId(), pole.getId())));
        assertEquals(List.of("EP-07", "VD-02"), shift.instrumentsByCode().stream().map(MaintenanceInstrument::getCode).toList());

        UUID unknown = UUID.randomUUID();
        NotFoundException notArrived = assertThrows(NotFoundException.class,
                () -> fixture.service.update(shift.getId(), instruments(Set.of(pole.getId(), unknown))));
        assertEquals("Maintenance instrument", notArrived.getAggregate(), "Answered 404 MSI-404");
        assertEquals(2, shift.getInstruments().size(), "Nothing is replaced before every id is found");

        fixture.service.update(shift.getId(), instruments(Set.of(pole.getId())));
        assertEquals(List.of("EP-07"), shift.instrumentsByCode().stream().map(MaintenanceInstrument::getCode).toList());
        fixture.service.update(shift.getId(), instruments(null));
        assertEquals(1, shift.getInstruments().size(), "A PUT without the set leaves it as it is");
        fixture.service.patch(shift.getId(), new MergePatch<>(instruments(null), Set.of("instrumentIds")));
        assertTrue(shift.getInstruments().isEmpty(), "A merge-patch null takes them all out");
    }

    @Test
    void aShiftStartsOnlyWithADifferentQualifiedDriverForEachSelfPropelledVehicleThatRequiresOne() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = crewedShift(crewPerson("TEAM_LEADER"), 2);
        FleetVehicle towerCar = withDriverQualification(fleetVehicle("RAIL_ONLY", true, true, 8), "RAIL-DRIVER");
        FleetVehicle roadRail = withDriverQualification(fleetVehicle("ROAD_RAIL", true, false, 5), "RR-OPERATOR");
        shift.getVehicles().add(MaintenanceShiftVehicle.of(shift, towerCar));
        shift.getVehicles().add(MaintenanceShiftVehicle.of(shift, roadRail));
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        // El jefe sabe llevar los dos, pero solo conduce uno.
        withQualification(shift.getTeamLeader(), "RAIL-DRIVER", null);
        withQualification(shift.getTeamLeader(), "RR-OPERATOR", LocalDate.now().plusYears(1));

        fixture.service.update(shift.getId(), observations("planned without a second driver"));
        ShiftException refused = assertThrows(ShiftException.class, () -> fixture.service.start(shift.getId(), new StartShiftRequest(null, null)));
        assertTrue(refused.getMessage().contains("no qualified driver for"), refused.getMessage());
        assertEquals(ShiftStatus.PLANNED, shift.getStatus(), "While planned the drivers may still be missing");
        assertTrue(fixture.events.published.isEmpty());

        // Un lineman con la habilitacion de la dresina basta: el jefe se queda el bivial.
        withQualification(shift.getLinemen().iterator().next(), "RAIL-DRIVER", LocalDate.now());
        fixture.service.start(shift.getId(), new StartShiftRequest(null, null));
        assertEquals(ShiftStatus.IN_PROGRESS, shift.getStatus());
        assertEquals(List.of("shift.started"), fixture.events.names());
    }

    @Test
    void theShiftEventsCarryTheInstrumentsByCodeAfterEverythingElse() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = shift(2L, PossessionType.FULL, ShiftStatus.PLANNED);
        MaintenanceInstrument detector = instrument("VD-02", true, null);
        MaintenanceInstrument pole = instrument("EP-07", true, LocalDate.now().plusMonths(3));
        shift.getInstruments().add(detector);
        shift.getInstruments().add(pole);
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);

        fixture.service.start(shift.getId(), new StartShiftRequest(null, null));

        Map<String, Object> values = fixture.events.published.getFirst().values();
        assertEquals(List.of(pole.getId().toString(), detector.getId().toString()), values.get("instrumentIds"));
        assertEquals(List.of("EP-07", "VD-02"), values.get("instrumentCodes"));
        List<String> keys = List.copyOf(values.keySet());
        assertEquals(List.of("linemanPersonIds", "instrumentIds", "instrumentCodes"), keys.subList(keys.size() - 3, keys.size()),
                "Keys are only added, at the end");
    }

    // ------------------------------------------------------- inspections (2)

    @Test
    void creatingAnInspectionCopiesTheActiveChecklistOfTheAssetTypeAndRejectsAClosedOriginOrder() {
        InspectionFixture fixture = new InspectionFixture();
        CatenaryAsset asset = profile("13-2.10", "13499.290");
        InspectionTemplate template = template("CW_HEIGHT", "DROPPERS");
        when(fixture.lookups.enabledAsset(asset.getId())).thenReturn(asset);
        when(fixture.lookups.activeTemplate(asset)).thenReturn(Optional.of(template));
        when(fixture.codeGenerator.nextInspectionCode()).thenReturn("INS-000001");

        fixture.service.create(new MaintenanceInspectionRequest(asset.getId(), LocalDate.of(2026, 1, 28), "yossi", null, InspectionResult.OK,
                null, null, null, null, null, null));

        ArgumentCaptor<MaintenanceInspection> captor = ArgumentCaptor.forClass(MaintenanceInspection.class);
        verify(fixture.repository).save(captor.capture());
        MaintenanceInspection saved = captor.getValue();
        assertEquals("INS-000001", saved.getCode());
        assertEquals(template, saved.getTemplate());
        assertEquals(List.of("CW_HEIGHT", "DROPPERS"), saved.getItems().stream().map(MaintenanceInspectionItem::getCode).toList());
        assertSame(saved, saved.getItems().getFirst().getInspection());
        assertEquals(InspectionKind.VISUAL, saved.getInspectionKind());
        assertEquals(asset.getStartKp(), saved.getKp(), "Without a kp the inspection sits where the asset is");
        assertEquals(2L, saved.getTrackId());

        MaintenanceOrder cancelled = order(MaintenanceOrderType.INSPECTION, MaintenanceOrderStatus.CANCELLED);
        when(fixture.lookups.order(cancelled.getId())).thenReturn(cancelled);
        assertThrows(InvalidTransitionException.class, () -> fixture.service.create(new MaintenanceInspectionRequest(asset.getId(),
                LocalDate.of(2026, 1, 28), null, null, InspectionResult.OK, null, null, null, null, cancelled.getId(), null)));
    }

    @Test
    void anInspectionIsRecordedInAShiftThatWorkedOnItsTrackOrTheOneItConnectsWith() {
        // Sale en el parte de ese turno: tiene que ser de esa noche (en curso o ya cerrado) y de sus vias.
        InspectionFixture fixture = new InspectionFixture();
        CatenaryAsset insulator = insulator("SI-W41", 3L, 2L, "12920.000", "12960.000");
        when(fixture.lookups.enabledAsset(insulator.getId())).thenReturn(insulator);
        when(fixture.codeGenerator.nextInspectionCode()).thenReturn("INS-000040");
        MaintenanceShift onConnected = shift(2L, PossessionType.FULL, ShiftStatus.CLOSED);
        MaintenanceShift onOther = shift(5L, PossessionType.FULL, ShiftStatus.IN_PROGRESS);
        MaintenanceShift planned = shift(3L, PossessionType.FULL, ShiftStatus.PLANNED);
        MaintenanceShift cancelled = shift(3L, PossessionType.FULL, ShiftStatus.CANCELLED);
        Stream.of(onConnected, onOther, planned, cancelled).forEach(shift -> when(fixture.lookups.shift(shift.getId())).thenReturn(shift));

        fixture.service.create(inspectionIn(insulator, onConnected));

        ArgumentCaptor<MaintenanceInspection> captor = ArgumentCaptor.forClass(MaintenanceInspection.class);
        verify(fixture.repository).save(captor.capture());
        assertSame(onConnected, captor.getValue().getShift(), "A closed shift on the track it connects with records it");

        ShiftException otherTrack = assertThrows(ShiftException.class, () -> fixture.service.create(inspectionIn(insulator, onOther)));
        assertTrue(otherTrack.getMessage().contains("is on track 3 and connects with track 2"), otherTrack.getMessage());
        ShiftException notWorked = assertThrows(ShiftException.class, () -> fixture.service.create(inspectionIn(insulator, planned)));
        assertTrue(notWorked.getMessage().contains("is PLANNED"), notWorked.getMessage());
        assertThrows(ShiftException.class, () -> fixture.service.create(inspectionIn(insulator, cancelled)));
        verify(fixture.repository, times(1)).save(any());
    }

    private static MaintenanceInspectionRequest inspectionIn(CatenaryAsset asset, MaintenanceShift shift) {
        return new MaintenanceInspectionRequest(asset.getId(), LocalDate.of(2026, 1, 28), "dana", null, InspectionResult.OK,
                null, null, null, null, null, shift.getId());
    }

    @Test
    void anInspectionCannotBeOkWithADefectiveItemAndAMeasureOutOfRangeIsOnlyOkOnceAdjusted() {
        InspectionFixture fixture = new InspectionFixture();
        MaintenanceInspection inspection = inspection(InspectionResult.MINOR_DEFECT);
        MaintenanceInspectionItem height = MaintenanceInspectionItem.fromTemplate(inspection, templateItem("CW_HEIGHT", 1, true));
        ReflectionTestUtils.setField(height, "id", UUID.randomUUID());
        inspection.getItems().add(height);
        when(fixture.repository.findById(inspection.getId())).thenReturn(Optional.of(inspection));

        assertThrows(InspectionException.class, () -> fixture.service.updateItem(inspection.getId(), height.getId(),
                new CheckItemUpdateRequest(new BigDecimal("5620"), null, null, CheckItemResult.OK, null, null)));
        fixture.service.updateItem(inspection.getId(), height.getId(),
                new CheckItemUpdateRequest(null, null, new BigDecimal("5300"), CheckItemResult.OK, "brought back to 5300", null));

        assertTrue(height.getAdjusted(), "A value after adjustment marks the item as adjusted");
        assertEquals(CheckItemResult.OK, height.getItemResult());
        assertEquals(new BigDecimal("5300"), height.effectiveValue());
        assertFalse(height.isOutOfRange());

        fixture.service.updateItem(inspection.getId(), height.getId(), new CheckItemUpdateRequest(null, null, null, CheckItemResult.DEFECT, null, null));
        assertThrows(InspectionException.class, () -> fixture.service.update(inspection.getId(),
                new MaintenanceInspectionUpdateRequest(null, null, null, InspectionResult.OK, null, null, null, null, null)));
        assertEquals(InspectionResult.OK, inspection.getResult(), "The result is applied before the check so the caller sees why it failed");
        assertThrows(NotFoundException.class, () -> fixture.service.updateItem(inspection.getId(), UUID.randomUUID(),
                new CheckItemUpdateRequest(null, null, null, CheckItemResult.OK, null, null)));

        fixture.service.update(inspection.getId(), new MaintenanceInspectionUpdateRequest(LocalDate.of(2026, 1, 29), "dana", InspectionKind.TECHNICAL,
                InspectionResult.MAJOR_DEFECT, "measured", "Height out of range", "Adjust", new BigDecimal("13500.000"), null));
        assertEquals(InspectionResult.MAJOR_DEFECT, inspection.getResult());
        assertEquals("dana", inspection.getInspector());
        assertEquals(InspectionKind.TECHNICAL, inspection.getInspectionKind());
        assertEquals(new BigDecimal("13500.000"), inspection.getKp());
    }

    @Test
    void aMajorDefectInspectionCreatesAHighPriorityCorrectiveOrderAndAMinorOneNeedsForceForItsDefect() {
        InspectionFixture fixture = new InspectionFixture();
        MaintenanceInspection inspection = inspection(InspectionResult.MAJOR_DEFECT);
        inspection.setRecommendedActions("Replace the insulator");
        MaintenanceOrder created = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.DRAFT);
        MaintenanceOrderResponse response = mock(MaintenanceOrderResponse.class);
        when(response.id()).thenReturn(created.getId());
        when(fixture.repository.findById(inspection.getId())).thenReturn(Optional.of(inspection));
        when(fixture.orderService.create(any())).thenReturn(response);
        when(fixture.orderRepository.findById(created.getId())).thenReturn(Optional.of(created));

        fixture.service.createCorrectiveOrder(inspection.getId(), new CreateCorrectiveOrderRequest(null, null, null, LocalDate.of(2026, 2, 1), null));

        ArgumentCaptor<MaintenanceOrderRequest> captor = ArgumentCaptor.forClass(MaintenanceOrderRequest.class);
        verify(fixture.orderService).create(captor.capture());
        MaintenanceOrderRequest request = captor.getValue();
        assertEquals(MaintenanceOrderType.CORRECTIVE, request.type());
        assertEquals(MaintenancePriority.HIGH, request.priority());
        assertEquals("Corrective work after inspection INS-MAJOR_DEFECT on PRF-13-2.10", request.title());
        assertEquals("Cracked insulator\n\nRecommended actions: Replace the insulator", request.description());
        assertEquals(LocalDate.of(2026, 2, 1), request.plannedDate());
        assertEquals(inspection, created.getOriginInspection());
        assertNull(created.getOriginDefect(), "No defect was generated before the order");
        assertEquals(created, inspection.getGeneratedOrder());

        MaintenanceInspection ok = inspection(InspectionResult.OK);
        when(fixture.repository.findById(ok.getId())).thenReturn(Optional.of(ok));
        assertThrows(InspectionException.class, () -> fixture.service.createCorrectiveOrder(ok.getId(), new CreateCorrectiveOrderRequest(null, null, null, null, null)));

        MaintenanceInspection minor = inspection(InspectionResult.MINOR_DEFECT);
        minor.setKp(new BigDecimal("13499.290"));
        when(fixture.repository.findById(minor.getId())).thenReturn(Optional.of(minor));
        when(fixture.codeGenerator.nextDefectCode()).thenReturn("DEF-000009");
        when(fixture.defectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.service.createDefect(minor.getId(), new CreateDefectFromInspectionRequest(null, " Chipped insulator ", null, true));

        CatenaryDefect defect = minor.getGeneratedDefect();
        assertEquals("DEF-000009", defect.getCode());
        assertEquals(DefectSeverity.MEDIUM, defect.getSeverity(), "MINOR_DEFECT maps to MEDIUM when no severity is given");
        assertEquals("Chipped insulator", defect.getDescription());
        assertEquals(minor, defect.getInspection());
        assertEquals(minor.getKp(), defect.getStartKp());
        assertEquals(Instant.parse("2026-01-28T00:00:00Z"), defect.getDetectedAt());
        verify(fixture.history).recordDefectChange(defect, null, "OPEN", "Created from inspection INS-MINOR_DEFECT");
    }

    @Test
    void inspectionTemplatesAreListedNewestVersionFirstAndAMissingOneIsNotFound() {
        InspectionTemplateRepository repository = mock(InspectionTemplateRepository.class);
        InspectionTemplateMapper mapper = mock(InspectionTemplateMapper.class);
        InspectionTemplateServiceImpl service = new InspectionTemplateServiceImpl(repository, mapper);
        InspectionTemplate template = template("CW_HEIGHT");
        InspectionTemplateResponse response = mock(InspectionTemplateResponse.class);
        when(repository.findAllByOrderByAssetTypeAscVersionDesc()).thenReturn(List.of(template));
        when(mapper.toResponse(template)).thenReturn(response);

        assertEquals(List.of(response), service.findAll());
        assertThrows(NotFoundException.class, () -> service.findById(UUID.randomUUID()));
    }

    // -------------------------------------------------------------- tasks (2)

    @Test
    void manualTasksAreAppendedToTheOrderWithTheirChecklistAndOnlyWhileTheOrderIsOpen() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        CatenaryAsset profile = profile("12-2.27", "12847.990");
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.lookups.enabledAsset(profile.getId())).thenReturn(profile);
        when(fixture.repository.findMaxSequence(order.getId())).thenReturn(2);
        when(fixture.lookups.taskTypes(List.of("RP-09"))).thenReturn(diagnosticTypes());
        when(fixture.lookups.activeTemplate(profile)).thenReturn(Optional.of(template("CW_HEIGHT", "CW_STAGGER")));

        fixture.service.create(order.getId(), new MaintenanceTaskRequest("  Static auscultation ", profile.getId(), "yossi", List.of("RP-09"), null));

        MaintenanceTask saved = fixture.savedTask();
        assertEquals(3, saved.getSequence());
        assertEquals("Static auscultation", saved.getDescription());
        assertEquals(profile, saved.getAsset());
        assertEquals(List.of("CW_HEIGHT", "CW_STAGGER"), saved.getCheckItems().stream().map(MaintenanceTaskCheckItem::getCode).toList(),
                "A diagnostic task type brings the checklist even without asking for it");
        assertTrue(order.getTasks().contains(saved));

        MaintenanceOrder completed = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.COMPLETED);
        when(fixture.lookups.order(completed.getId())).thenReturn(completed);
        assertThrows(InvalidTransitionException.class, () -> fixture.service.create(completed.getId(), new MaintenanceTaskRequest("late", null, null, null, null)));
        assertThrows(NotFoundException.class, () -> fixture.service.findById(order.getId(), UUID.randomUUID()));
    }

    @Test
    void aTaskWithUnansweredMeasurementsCannotBeCompletedAndTheMaterialsUsedAreReservedAtOnce() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceTask task = task(order, MaintenanceTaskStatus.PENDING);
        task.setAsset(profile("12-2.27", "12847.990"));
        MaintenanceTaskCheckItem height = MaintenanceTaskCheckItem.fromTemplate(task, templateItem("CW_HEIGHT", 1, true));
        task.getCheckItems().add(height);
        MaintenanceShift shift = shift(2L, PossessionType.PARTIAL, ShiftStatus.IN_PROGRESS);
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.repository.findByIdAndOrderId(task.getId(), order.getId())).thenReturn(Optional.of(task));
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);

        assertThrows(ValidationException.class, () -> fixture.service.complete(order.getId(), task.getId(), complete(shift.getId(), List.of())));
        assertEquals(MaintenanceTaskStatus.PENDING, task.getStatus());

        height.setMeasuredValue(new BigDecimal("5200"));
        height.setItemResult(CheckItemResult.OK);
        UUID material = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        MaintenanceMaterialUsage line = MaintenanceMaterialUsage.builder().order(order).task(task).materialId(material).materialCode("GA70")
                .warehouseId(warehouse).plannedQuantity(new BigDecimal("3")).unit("ud").allowOverConsumption(true).build();
        when(fixture.lineFactory.buildLine(order, task, material, "GA70", warehouse, new BigDecimal("3"), "ud", true)).thenReturn(line);
        when(fixture.lineFactory.saveLine(line)).thenReturn(line);

        fixture.service.complete(order.getId(), task.getId(), new CompleteTaskRequest(shift.getId(), null, "droppers replaced", null, true, null, null,
                List.of(new TaskMaterialRequest(material, "GA70", warehouse, new BigDecimal("3"), "ud")), List.of("12-2.27.jpg")));

        assertEquals(MaintenanceTaskStatus.COMPLETED, task.getStatus());
        assertNotNull(task.getStartedAt(), "A task completed in one go gets its start stamped too");
        assertEquals(List.of("12-2.27.jpg"), task.getPhotoRefs());
        assertEquals(0, new BigDecimal("3").compareTo(line.getConsumedQuantity()), "What was used on the profile is already consumed");
        verify(fixture.stock).reserve(line);
    }

    @Test
    void completingATaskReservesItsMaterialsOnlyAfterEverythingElseWentThrough() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceShift shift = shift(2L, PossessionType.PARTIAL, ShiftStatus.IN_PROGRESS);
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        UUID dropper = UUID.randomUUID();
        UUID clamp = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        TaskMaterialRequest usedDropper = new TaskMaterialRequest(dropper, "DROPPER-STD", warehouse, BigDecimal.ONE, "ud");
        TaskMaterialRequest misspelt = new TaskMaterialRequest(null, "DROPER-STD", warehouse, BigDecimal.ONE, "ud");
        TaskMaterialRequest usedClamp = new TaskMaterialRequest(clamp, "CLAMP-12", warehouse, BigDecimal.ONE, "ud");
        MaintenanceMaterialUsage dropperLine = MaintenanceMaterialUsage.builder().order(order).materialId(dropper).materialCode("DROPPER-STD")
                .warehouseId(warehouse).plannedQuantity(BigDecimal.ONE).unit("ud").allowOverConsumption(true).build();
        MaintenanceMaterialUsage clampLine = MaintenanceMaterialUsage.builder().order(order).materialId(clamp).materialCode("CLAMP-12")
                .warehouseId(warehouse).plannedQuantity(BigDecimal.ONE).unit("ud").allowOverConsumption(true).build();
        when(fixture.lineFactory.buildLine(any(), any(), eq(dropper), any(), any(), any(), any(), eq(true))).thenReturn(dropperLine);
        when(fixture.lineFactory.buildLine(any(), any(), eq(clamp), any(), any(), any(), any(), eq(true))).thenReturn(clampLine);
        when(fixture.lineFactory.buildLine(any(), any(), isNull(), eq("DROPER-STD"), any(), any(), any(), eq(true)))
                .thenThrow(new ValidationException("Material 'DROPER-STD' is unknown to stock and no materialId was given"));
        when(fixture.lineFactory.saveLine(dropperLine)).thenReturn(dropperLine);

        // El segundo material no existe en stock: el primero ya estaba guardado, y antes ya estaba reservado.
        MaintenanceTask first = task(order, MaintenanceTaskStatus.PENDING);
        first.setAsset(profile("13-2.01", "13007.290"));
        when(fixture.repository.findByIdAndOrderId(first.getId(), order.getId())).thenReturn(Optional.of(first));
        assertThrows(ValidationException.class, () -> fixture.service.complete(order.getId(), first.getId(),
                new CompleteTaskRequest(shift.getId(), null, null, null, true, null, null, List.of(usedDropper, misspelt), null)));

        // El mismo material dos veces: la segunda linea choca con la primera al guardarse.
        MaintenanceTask second = task(order, MaintenanceTaskStatus.PENDING);
        second.setAsset(profile("13-2.02", "13060.290"));
        when(fixture.repository.findByIdAndOrderId(second.getId(), order.getId())).thenReturn(Optional.of(second));
        when(fixture.lineFactory.saveLine(clampLine)).thenReturn(clampLine)
                .thenThrow(new MaterialUsageException("Material CLAMP-12 is already registered on this task"));
        assertThrows(MaterialUsageException.class, () -> fixture.service.complete(order.getId(), second.getId(),
                new CompleteTaskRequest(shift.getId(), null, null, null, true, null, null, List.of(usedClamp, usedClamp), null)));

        verify(fixture.stock, never()).reserve(any());

        MaintenanceTask third = task(order, MaintenanceTaskStatus.PENDING);
        third.setAsset(profile("13-2.03", "13110.290"));
        when(fixture.repository.findByIdAndOrderId(third.getId(), order.getId())).thenReturn(Optional.of(third));
        fixture.service.complete(order.getId(), third.getId(),
                new CompleteTaskRequest(shift.getId(), null, null, null, true, null, null, List.of(usedDropper), null));

        InOrder inStock = inOrder(fixture.repository, fixture.stock);
        inStock.verify(fixture.repository).save(third);
        inStock.verify(fixture.repository).flush();
        inStock.verify(fixture.stock).reserve(dropperLine);
    }

    @Test
    void startingATaskBindsItToTheShiftAndOnlyPendingTasksStartOrGetAssigned() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        CatenaryAsset profile = profile("12-2.27", "12847.990");
        MaintenanceTask task = task(order, MaintenanceTaskStatus.PENDING);
        task.setAsset(profile);
        MaintenanceShift shift = shift(2L, PossessionType.FULL, ShiftStatus.IN_PROGRESS);
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.repository.findByIdAndOrderId(task.getId(), order.getId())).thenReturn(Optional.of(task));
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);

        fixture.service.start(order.getId(), task.getId(), new StartTaskRequest(shift.getId(), " yossi "));

        assertEquals(MaintenanceTaskStatus.IN_PROGRESS, task.getStatus());
        assertEquals(shift, task.getShift());
        assertNotNull(task.getStartedAt());
        assertEquals("yossi", task.getAssignedUser());
        assertThrows(InvalidTransitionException.class, () -> fixture.service.start(order.getId(), task.getId(), new StartTaskRequest(shift.getId(), null)));

        MaintenanceTask pending = task(order, MaintenanceTaskStatus.PENDING);
        pending.setAsset(profile);
        when(fixture.repository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(fixture.repository.findById(task.getId())).thenReturn(Optional.of(task));
        MaintenanceShift closed = shift(2L, PossessionType.FULL, ShiftStatus.CLOSED);
        MaintenanceShift planned = shift(2L, PossessionType.PARTIAL, ShiftStatus.PLANNED);
        when(fixture.lookups.shift(closed.getId())).thenReturn(closed);
        when(fixture.lookups.shift(planned.getId())).thenReturn(planned);

        assertThrows(InvalidTransitionException.class, () -> fixture.service.assignToShift(closed.getId(), pending.getId()));
        assertThrows(InvalidTransitionException.class, () -> fixture.service.assignToShift(planned.getId(), task.getId()), "Only pending tasks are assigned");
        order.getAsset().setTrackKind(TrackKind.DIVERTED);
        assertThrows(ShiftException.class, () -> fixture.service.assignToShift(planned.getId(), pending.getId()), "A diverted section waits for full possession");
        order.getAsset().setTrackKind(TrackKind.MAIN);

        fixture.service.assignToShift(planned.getId(), pending.getId());

        assertEquals(planned, pending.getShift());
        assertEquals(MaintenanceTaskStatus.PENDING, pending.getStatus(), "Assigning a task to a planned shift does not start it");
    }

    @Test
    void takingAnOpenTaskOutOfItsShiftSendsItBackToItsOrderAndLeavesWhatWasDone() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceShift shift = shift(2L, PossessionType.FULL, ShiftStatus.IN_PROGRESS);
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        MaintenanceTask pending = task(order, MaintenanceTaskStatus.PENDING);
        pending.setShift(shift);
        MaintenanceTask started = task(order, MaintenanceTaskStatus.IN_PROGRESS);
        started.setShift(shift);
        Instant startedAt = Instant.parse("2026-01-31T19:41:00Z");
        started.setStartedAt(startedAt);
        MaintenanceTask done = task(order, MaintenanceTaskStatus.COMPLETED);
        done.setShift(shift);
        for (MaintenanceTask each : List.of(pending, started, done)) {
            when(fixture.repository.findByIdAndShiftId(each.getId(), shift.getId())).thenReturn(Optional.of(each));
        }

        fixture.service.unassignFromShift(shift.getId(), pending.getId());
        fixture.service.unassignFromShift(shift.getId(), started.getId());

        assertNull(pending.getShift());
        assertEquals(MaintenanceTaskStatus.PENDING, pending.getStatus());
        assertNull(started.getShift());
        assertEquals(MaintenanceTaskStatus.PENDING, started.getStatus(), "A started task goes back to the queue, as on closing the shift");
        assertEquals(startedAt, started.getStartedAt(), "It was started: the time stays");
        InvalidTransitionException completed = assertThrows(InvalidTransitionException.class,
                () -> fixture.service.unassignFromShift(shift.getId(), done.getId()));
        assertTrue(completed.getMessage().contains("only an open task"), completed.getMessage());
        assertEquals(shift, done.getShift(), "What was done in the shift stays in it");

        UUID elsewhere = UUID.randomUUID();
        when(fixture.repository.findByIdAndShiftId(elsewhere, shift.getId())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> fixture.service.unassignFromShift(shift.getId(), elsewhere), "A task of another shift is not this one's");

        MaintenanceShift closed = shift(2L, PossessionType.FULL, ShiftStatus.CLOSED);
        when(fixture.lookups.shift(closed.getId())).thenReturn(closed);
        MaintenanceTask left = task(order, MaintenanceTaskStatus.PENDING);
        left.setShift(closed);
        when(fixture.repository.findByIdAndShiftId(left.getId(), closed.getId())).thenReturn(Optional.of(left));
        assertThrows(InvalidTransitionException.class, () -> fixture.service.unassignFromShift(closed.getId(), left.getId()));
        assertEquals(closed, left.getShift());
    }

    @Test
    void cancellingATaskKeepsTheReasonInItsNotesAndFreezesItsCheckItems() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        MaintenanceTask task = task(order, MaintenanceTaskStatus.PENDING);
        task.setNotes("Prepared");
        MaintenanceTaskCheckItem item = MaintenanceTaskCheckItem.fromTemplate(task, templateItem("CW_HEIGHT", 1, true));
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        task.getCheckItems().add(item);
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.repository.findByIdAndOrderId(task.getId(), order.getId())).thenReturn(Optional.of(task));
        when(fixture.lookups.taskTypes(List.of("RG-01"))).thenReturn(taskTypes("RG-01"));

        fixture.service.updateCheckItem(order.getId(), task.getId(), item.getId(), new CheckItemUpdateRequest(new BigDecimal("5200"), null, null, CheckItemResult.OK, null, null));
        fixture.service.update(order.getId(), task.getId(), new MaintenanceTaskUpdateRequest(" Pole 12-2.27 ", "dana", List.of("RG-01"), null, "bird nest", List.of("nest.jpg"), null));

        assertEquals(CheckItemResult.OK, item.getItemResult());
        assertEquals("Pole 12-2.27", task.getDescription());
        assertEquals("dana", task.getAssignedUser());
        assertEquals(1, task.getTaskTypes().size());
        assertEquals("bird nest", task.getDefectsFound());
        assertEquals(List.of("nest.jpg"), task.getPhotoRefs());

        fixture.service.cancel(order.getId(), task.getId(), new CancelTaskRequest("profile skipped this cycle"));

        assertEquals(MaintenanceTaskStatus.CANCELLED, task.getStatus());
        assertEquals("Prepared\nCancelled: profile skipped this cycle", task.getNotes());
        assertThrows(InvalidTransitionException.class, () -> fixture.service.updateCheckItem(order.getId(), task.getId(), item.getId(),
                new CheckItemUpdateRequest(null, null, null, CheckItemResult.DEFECT, null, null)));
        assertThrows(InvalidTransitionException.class, () -> fixture.service.update(order.getId(), task.getId(),
                new MaintenanceTaskUpdateRequest("x", null, null, null, null, null, null)));
        assertThrows(InvalidTransitionException.class, () -> fixture.service.cancel(order.getId(), task.getId(), new CancelTaskRequest("again")));
    }

    @Test
    void theTasksOfAShiftCanBeFilteredByStatus() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceShift shift = shift(2L, PossessionType.PARTIAL, ShiftStatus.IN_PROGRESS);
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceTask done = task(order, MaintenanceTaskStatus.COMPLETED);
        MaintenanceTask pending = task(order, MaintenanceTaskStatus.PENDING);
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        when(fixture.repository.findByShiftIdOrderBySequenceAsc(shift.getId())).thenReturn(List.of(done, pending));

        assertEquals(2, fixture.service.findByShift(shift.getId(), null).size());
        assertEquals(1, fixture.service.findByShift(shift.getId(), MaintenanceTaskStatus.COMPLETED).size());
        verify(fixture.mapper, times(2)).toResponse(done);
        verify(fixture.mapper, times(1)).toResponse(pending);
    }

    // ------------------------------------------------------- shared helpers

    @Test
    void shiftRulesFallBackToTheOrderTrackAndFullPossessionAcceptsAnyWork() {
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceTask sectionWork = task(order, MaintenanceTaskStatus.PENDING);
        sectionWork.getTaskTypes().addAll(fullPossessionTypes());
        MaintenanceShift full = shift(2L, PossessionType.FULL, ShiftStatus.IN_PROGRESS);
        MaintenanceShift partial = shift(2L, PossessionType.PARTIAL, ShiftStatus.IN_PROGRESS);
        MaintenanceShift otherTrack = shift(3L, PossessionType.FULL, ShiftStatus.IN_PROGRESS);

        assertDoesNotThrow(() -> {
            ShiftRules.requireInProgress(full);
            ShiftRules.requireSameTrack(full, sectionWork);
            ShiftRules.requireCompatiblePossession(full, sectionWork);
        });
        assertThrows(ShiftException.class, () -> ShiftRules.requireSameTrack(otherTrack, sectionWork), "Without an asset the order track decides");
        assertThrows(ShiftException.class, () -> ShiftRules.requireCompatiblePossession(partial, sectionWork));

        MaintenanceTask plain = task(order, MaintenanceTaskStatus.PENDING);
        assertDoesNotThrow(() -> ShiftRules.requireCompatiblePossession(partial, plain));
        order.getAsset().setTrackKind(TrackKind.DIVERTED);
        assertThrows(ShiftException.class, () -> ShiftRules.requireCompatiblePossession(partial, plain));
        assertThrows(ShiftException.class, () -> ShiftRules.requireInProgress(shift(2L, PossessionType.PARTIAL, ShiftStatus.PLANNED)));
    }

    @Test
    void anInsulatorThatJoinsTwoTracksIsWorkedFromAShiftOnEitherOfThem() {
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceTask onTheInsulator = task(order, MaintenanceTaskStatus.PENDING);
        CatenaryAsset insulator = CatenaryAsset.builder().code("SIN-41").name("SI-W31").type(CatenaryAssetType.SECTION_INSULATOR)
                .trackId(1L).connectedTrackId(2L).build();
        onTheInsulator.setAsset(insulator);

        assertDoesNotThrow(() -> ShiftRules.requireSameTrack(shift(1L, PossessionType.FULL, ShiftStatus.IN_PROGRESS), onTheInsulator));
        assertDoesNotThrow(() -> ShiftRules.requireSameTrack(shift(2L, PossessionType.FULL, ShiftStatus.IN_PROGRESS), onTheInsulator),
                "Its other track: the reports by track already count it there");
        ShiftException elsewhere = assertThrows(ShiftException.class,
                () -> ShiftRules.requireSameTrack(shift(3L, PossessionType.FULL, ShiftStatus.IN_PROGRESS), onTheInsulator));
        assertTrue(elsewhere.getMessage().contains("connects with track 2"), elsewhere.getMessage());
    }

    @Test
    void lookupsNormaliseTaskTypeCodesRejectUnknownOnesAndDisabledAssets() {
        MaintenanceTaskTypeRepository taskTypeRepository = mock(MaintenanceTaskTypeRepository.class);
        CatenaryAssetRepository assetRepository = mock(CatenaryAssetRepository.class);
        MaintenanceLookups lookups = new MaintenanceLookups(assetRepository, mock(MaintenanceOrderRepository.class), mock(MaintenanceShiftRepository.class),
                mock(MaintenanceTeamRepository.class), taskTypeRepository, mock(InspectionTemplateRepository.class));
        when(taskTypeRepository.findByCodeIn(List.of("RG-12", "RG-01"))).thenReturn(new ArrayList<>(taskTypes("RG-01", "RG-12")));
        when(taskTypeRepository.findByCodeIn(List.of("RG-01", "RG-99"))).thenReturn(new ArrayList<>(taskTypes("RG-01")));

        Set<MaintenanceTaskType> resolved = lookups.taskTypes(List.of(" rg-12 ", "RG-01", "rg-12"));

        assertEquals(List.of("RG-12", "RG-01"), resolved.stream().map(MaintenanceTaskType::getCode).toList(), "Trimmed, upper-cased, deduplicated, in request order");
        ValidationException unknown = assertThrows(ValidationException.class, () -> lookups.taskTypes(List.of("RG-01", "RG-99")));
        assertTrue(unknown.getMessage().contains("RG-99"));
        assertTrue(lookups.taskTypes(null).isEmpty());
        assertTrue(lookups.taskTypes(List.of()).isEmpty());

        CatenaryAsset disabled = profile("12-2.27", "12847.990");
        disabled.disableLocally();
        when(assetRepository.findById(disabled.getId())).thenReturn(Optional.of(disabled));
        assertSame(disabled, lookups.asset(disabled.getId()));
        assertThrows(AssetDisabledException.class, () -> lookups.enabledAsset(disabled.getId()));
        assertThrows(NotFoundException.class, () -> lookups.asset(UUID.randomUUID()));
        assertThrows(NotFoundException.class, () -> lookups.team(UUID.randomUUID()));
    }

    @Test
    void domainInvariantsBrokenByARequestBecomeValidationErrors() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> DomainGuard.domain(() -> new Quantity(new BigDecimal("-1"), "ud")));

        assertEquals("amount must not be negative", exception.getMessage());
        assertEquals(BigDecimal.ONE, DomainGuard.domain(() -> new Quantity(BigDecimal.ONE, "ud")).amount());
    }

    @Test
    void statusHistoryRowsCarryTheActorAndAreReadInChronologicalOrder() {
        MaintenanceStatusHistoryRepository repository = mock(MaintenanceStatusHistoryRepository.class);
        StatusHistoryMapper mapper = mock(StatusHistoryMapper.class);
        StatusHistoryServiceImpl service = new StatusHistoryServiceImpl(repository, mapper, new RecordingEventPublisher());
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        CatenaryDefect defect = defect(DefectStatus.OPEN);

        service.recordOrderChange(order, "DRAFT", "PLANNED", "week 4");
        service.recordDefectChange(defect, null, "OPEN", "found");

        ArgumentCaptor<MaintenanceStatusHistory> captor = ArgumentCaptor.forClass(MaintenanceStatusHistory.class);
        verify(repository, times(2)).save(captor.capture());
        MaintenanceStatusHistory orderRow = captor.getAllValues().get(0);
        assertEquals(order, orderRow.getOrder());
        assertNull(orderRow.getDefect());
        assertEquals("DRAFT", orderRow.getPreviousStatus());
        assertEquals("PLANNED", orderRow.getNewStatus());
        assertEquals("system", orderRow.getChangedBy(), "Outside a request and without a user the actor is the system");
        assertNotNull(orderRow.getChangedAt());
        MaintenanceStatusHistory defectRow = captor.getAllValues().get(1);
        assertEquals(defect, defectRow.getDefect());
        assertNull(defectRow.getOrder());
        assertNull(defectRow.getPreviousStatus());

        StatusHistoryResponse response = mock(StatusHistoryResponse.class);
        when(repository.findByOrderIdOrderByChangedAtAsc(order.getId())).thenReturn(List.of(orderRow));
        when(mapper.toResponse(orderRow)).thenReturn(response);
        assertEquals(List.of(response), service.findByOrder(order.getId()));
        assertTrue(service.findByDefect(UUID.randomUUID()).isEmpty());
    }

    @Test
    void taskTypesAreFilteredByGroupPossessionAndDiagnosticAndLookedUpByNormalisedCode() {
        MaintenanceTaskTypeRepository repository = mock(MaintenanceTaskTypeRepository.class);
        MaintenanceTaskTypeMapper mapper = mock(MaintenanceTaskTypeMapper.class);
        when(mapper.toResponse(any())).thenAnswer(invocation -> {
            MaintenanceTaskType type = invocation.getArgument(0);
            return new MaintenanceTaskTypeResponse(null, type.getCode(), type.getDescription(), type.getFunctionalGroup(), type.getStandardMinutesPerUnit(),
                    type.getUnit(), type.getFixedMinutes(), type.getRequiresFullPossession(), type.getDiagnostic(), type.getActive(), type.getOrderIndex(),
                    type.getRequiredEquipment(), type.getVersion());
        });
        MaintenanceTaskTypeServiceImpl service = new MaintenanceTaskTypeServiceImpl(repository, mapper);
        MaintenanceTaskType insulators = MaintenanceTaskType.builder().code("RG-01").description("Insulators").functionalGroup(FunctionalGroup.STRUCTURAL_SUPPORTS).orderIndex(1).build();
        MaintenanceTaskType sectionInsulators = MaintenanceTaskType.builder().code("RG-03").description("Section insulators")
                .functionalGroup(FunctionalGroup.TURNOUTS_AND_SWITCHES).requiresFullPossession(true).orderIndex(3).build();
        MaintenanceTaskType heightCorrection = MaintenanceTaskType.builder().code("RP-10").description("Height correction")
                .functionalGroup(FunctionalGroup.DIAGNOSTICS).diagnostic(true).orderIndex(30).build();
        when(repository.findAllByOrderByOrderIndexAsc()).thenReturn(List.of(insulators, sectionInsulators, heightCorrection));
        when(repository.findByCode("RG-01")).thenReturn(Optional.of(insulators));

        assertEquals(3, service.findAll(null, null, null).size());
        assertEquals(List.of("RG-03"), service.findAll(FunctionalGroup.TURNOUTS_AND_SWITCHES, null, null).stream().map(MaintenanceTaskTypeResponse::code).toList());
        assertEquals(List.of("RG-03"), service.findAll(null, true, null).stream().map(MaintenanceTaskTypeResponse::code).toList());
        assertEquals(List.of("RP-10"), service.findAll(null, false, true).stream().map(MaintenanceTaskTypeResponse::code).toList());
        assertEquals("RG-01", service.findByCode(" rg-01 ").code());
        assertThrows(NotFoundException.class, () -> service.findByCode("ZZ-99"));
    }

    // ---------------------------------------------------------- materials (2)

    @Test
    void materialLinesResolveTheMaterialAgainstStockAndFallBackToTheRequestWhenStockIsDown() {
        MaintenanceMaterialUsageRepository repository = mock(MaintenanceMaterialUsageRepository.class);
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialLineFactory factory = new MaterialLineFactory(repository, stockClient);
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        UUID material = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        when(stockClient.findMaterialByCode("GA70")).thenReturn(Optional.of(new StockMaterial(material, "GA70", "Ga70 dropper", "ud", true)));
        when(stockClient.findMaterialByCode("XX-1")).thenReturn(Optional.empty());
        when(stockClient.findMaterialById(material)).thenThrow(new StockUnavailableException("stock down"));

        MaintenanceMaterialUsage resolved = factory.buildLine(order, null, null, " GA70 ", warehouse, new BigDecimal("2"), null, false);

        assertEquals(material, resolved.getMaterialId());
        assertEquals("GA70", resolved.getMaterialCode());
        assertEquals("Ga70 dropper", resolved.getMaterialDescriptionSnapshot());
        assertEquals("ud", resolved.getUnit(), "The unit comes from the catalogue when the request has none");

        assertThrows(ValidationException.class, () -> factory.buildLine(order, null, null, "XX-1", warehouse, BigDecimal.ONE, "ud", false),
                "Unknown to stock and no materialId: nothing to register");
        assertThrows(ValidationException.class, () -> factory.buildLine(order, null, material, "GA70", warehouse, BigDecimal.ONE, null, false),
                "Stock down and no unit in the request");
        assertThrows(ValidationException.class, () -> factory.buildLine(order, null, material, "GA70", warehouse, new BigDecimal("-1"), "ud", false));

        MaintenanceMaterialUsage fallback = factory.buildLine(order, null, material, "GA70", warehouse, BigDecimal.ONE, " ud ", true);
        assertEquals(material, fallback.getMaterialId());
        assertNull(fallback.getMaterialDescriptionSnapshot());
        assertEquals("ud", fallback.getUnit());
        assertTrue(fallback.getAllowOverConsumption());

        when(repository.saveAndFlush(resolved)).thenReturn(resolved);
        when(repository.saveAndFlush(fallback)).thenThrow(new DataIntegrityViolationException("uq_maintenance_material_usage_line"));
        assertSame(resolved, factory.saveLine(resolved));
        assertTrue(order.getMaterials().contains(resolved));
        assertThrows(MaterialUsageException.class, () -> factory.saveLine(fallback));
    }

    @Test
    void consumingAReservedLineReleasesConsumesOrOutputsAccordingToTheQuantityUsed() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher());
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        UUID project = UUID.randomUUID();
        order.setStockProjectId(project);

        MaintenanceMaterialUsage unused = reservedLine(order, "0");
        MaintenanceMaterialUsage exact = reservedLine(order, "2");
        MaintenanceMaterialUsage partial = reservedLine(order, "0.5");
        MaintenanceMaterialUsage over = reservedLine(order, "3.5");
        List.of(unused, exact, partial, over).forEach(line -> stockSays(stockClient, line, "ACTIVE"));

        synchronizer.consume(unused);
        synchronizer.consume(exact);
        synchronizer.consume(partial);
        synchronizer.consume(over);

        verify(stockClient).release(unused.getStockReservationId());
        assertEquals(StockSyncStatus.RELEASED, unused.getStockSyncStatus());
        verify(stockClient).consume(exact.getStockReservationId());
        verify(stockClient).release(partial.getStockReservationId());
        verify(stockClient).output(eq(partial.getMaterialId()), eq(partial.getWarehouseId()), eq(project), eq(new BigDecimal("0.5")), eq(order.getCode()), anyString(), any());
        verify(stockClient).consume(over.getStockReservationId());
        verify(stockClient).output(eq(over.getMaterialId()), eq(over.getWarehouseId()), eq(project), eq(new BigDecimal("1.5")), eq(order.getCode()), anyString(), any());
        assertEquals(StockSyncStatus.CONSUMED, exact.getStockSyncStatus());
        assertEquals(StockSyncStatus.CONSUMED, partial.getStockSyncStatus());
        assertEquals(StockSyncStatus.CONSUMED, over.getStockSyncStatus());

        MaintenanceMaterialUsage reserved = reservedLine(order, "0");
        stockSays(stockClient, reserved, "ACTIVE");
        synchronizer.release(reserved);
        verify(stockClient).release(reserved.getStockReservationId());
        assertEquals(StockSyncStatus.RELEASED, reserved.getStockSyncStatus());

        MaintenanceMaterialUsage neverRequested = line(order);
        synchronizer.release(neverRequested);
        synchronizer.consume(neverRequested);
        assertEquals(StockSyncStatus.NOT_REQUESTED, neverRequested.getStockSyncStatus(), "Nothing reserved and nothing consumed: stock is not called");

        when(stockClient.isEnabled()).thenReturn(false);
        assertThrows(StockUnavailableException.class, () -> synchronizer.syncNow(neverRequested));
    }

    @Test
    void planningTalksToStockForEveryLineAtOnceSoFourSlowLinesTakeTheTimeOfOne() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        when(stockClient.reserve(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Thread.sleep(400);
            return new StockReservation(UUID.randomUUID(), invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                    invocation.getArgument(3), "ACTIVE");
        });
        RecordingEventPublisher events = new RecordingEventPublisher();
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, events, virtualThreads(), 4,
                Duration.ofSeconds(10), true, event -> fail("Nothing is deferred: " + event));
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.PLANNED);
        order.setStockProjectId(UUID.randomUUID());
        List<MaintenanceMaterialUsage> lines = List.of(line(order), line(order), line(order), line(order));

        long started = System.nanoTime();
        synchronizer.reserveAll(order);
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertTrue(elapsed < 1200, "Four lines of 400 ms each in parallel, not 1600 ms in a row: " + elapsed + " ms");
        lines.forEach(line -> {
            assertEquals(StockSyncStatus.RESERVED, line.getStockSyncStatus());
            assertNotNull(line.getStockReservationId());
            assertFalse(line.isInDoubt());
        });
        assertTrue(events.published.isEmpty());
    }

    @Test
    void aLineThatStockDoesNotAnswerWithinTheDeadlineIsLeftInDoubtAndTheRetryRepeatsItWithTheSameKey() throws Exception {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        CountDownLatch release = new CountDownLatch(1);
        List<String> keys = java.util.Collections.synchronizedList(new ArrayList<>());
        UUID created = UUID.randomUUID();
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.PLANNED);
        order.setStockProjectId(UUID.randomUUID());
        MaintenanceMaterialUsage quick = line(order);
        MaintenanceMaterialUsage slow = line(order);
        when(stockClient.reserve(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            keys.add(invocation.getArgument(4));
            if (invocation.getArgument(0).equals(slow.getMaterialId()) && release.getCount() > 0) {
                release.await(10, TimeUnit.SECONDS);
            }
            return new StockReservation(invocation.getArgument(0).equals(slow.getMaterialId()) ? created : UUID.randomUUID(),
                    invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3), "ACTIVE");
        });
        RecordingEventPublisher events = new RecordingEventPublisher();
        List<Object> deferred = new ArrayList<>();
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, events, virtualThreads(), 4,
                Duration.ofMillis(300), true, deferred::add);

        long started = System.nanoTime();
        synchronizer.reserveAll(order);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2000, "The transition does not wait for a slow stock");

        assertEquals(StockSyncStatus.RESERVED, quick.getStockSyncStatus());
        assertEquals(StockSyncStatus.FAILED, slow.getStockSyncStatus());
        assertEquals(StockRequestType.RESERVATION, slow.getStockRequestInDoubt(), "The reservation may have reached stock");
        assertTrue(slow.getStockSyncError().contains("deferred"), slow.getStockSyncError());
        assertNull(slow.getStockReservationId());
        assertEquals(List.of("material.in-doubt"), events.names(), "Said once, like any line left in doubt");
        StockLinesDeferred event = (StockLinesDeferred) deferred.getFirst();
        assertEquals(order.getId(), event.orderId());
        assertEquals(List.of(slow.getId()), event.lineIds());

        // La llamada en el aire termina despues; la entidad ya no cambia por ella.
        release.countDown();
        CompletableFuture.allOf(event.inFlight().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        assertEquals(StockSyncStatus.FAILED, slow.getStockSyncStatus());
        assertNull(slow.getStockReservationId());

        // El reintento repite la reserva en duda con la misma clave, y stock devuelve la que creo.
        synchronizer.syncNow(slow);
        assertEquals(StockSyncStatus.RESERVED, slow.getStockSyncStatus());
        assertEquals(created, slow.getStockReservationId());
        String slowKey = MaterialStockSynchronizer.idempotencyKey(slow, StockRequestType.RESERVATION, null);
        assertEquals(2, keys.stream().filter(slowKey::equals).count(), "Sent twice with one key: stock applies it once");
    }

    @Test
    void aConversationCutByItsDeadlineSendsNothingMoreToStock() throws Exception {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        CountDownLatch release = new CountDownLatch(1);
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.PLANNED);
        order.setStockProjectId(UUID.randomUUID());
        MaintenanceMaterialUsage first = line(order);
        MaintenanceMaterialUsage second = line(order);
        when(stockClient.reserve(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            release.await(10, TimeUnit.SECONDS);
            return new StockReservation(UUID.randomUUID(), invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                    invocation.getArgument(3), "ACTIVE");
        });
        List<Object> deferred = new ArrayList<>();
        // Una a la vez: la segunda espera su turno detras de la primera, que no contesta.
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher(), virtualThreads(), 1,
                Duration.ofMillis(200), true, deferred::add);

        synchronizer.reserveAll(order);

        // Cual de las dos llego primero al permiso no esta fijado: una tiene su reserva en el aire, la otra nada.
        MaintenanceMaterialUsage sent = first.isInDoubt() ? first : second;
        MaintenanceMaterialUsage waiting = sent == first ? second : first;
        assertEquals(StockRequestType.RESERVATION, sent.getStockRequestInDoubt(), "Its reservation was in the air");
        assertNull(waiting.getStockRequestInDoubt(), "It never got to send anything");
        assertEquals(StockSyncStatus.FAILED, sent.getStockSyncStatus());
        assertEquals(StockSyncStatus.FAILED, waiting.getStockSyncStatus());
        release.countDown();
        StockLinesDeferred event = (StockLinesDeferred) deferred.getFirst();
        assertEquals(java.util.Set.of(first.getId(), second.getId()), java.util.Set.copyOf(event.lineIds()));
        CompletableFuture.allOf(event.inFlight().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        verify(stockClient, times(1)).reserve(any(), any(), any(), any(), any());
        verify(stockClient, never()).reserve(eq(waiting.getMaterialId()), any(), any(), any(), any());
    }

    @Test
    void theDeferredLinesAreRetriedRightAfterTheCommitOnceTheirCallInTheAirIsOver() {
        StockSyncRetryService retryService = mock(StockSyncRetryService.class);
        DeferredStockSyncListener listener = new DeferredStockSyncListener(retryService, Runnable::run);
        UUID orderId = UUID.randomUUID();
        List<UUID> lines = List.of(UUID.randomUUID(), UUID.randomUUID());
        CompletableFuture<RuntimeException> inTheAir = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(150, TimeUnit.MILLISECONDS).execute(() -> inTheAir.complete(null));

        long started = System.nanoTime();
        listener.retryAfterCommit(new StockLinesDeferred(orderId, lines, List.of(inTheAir)));

        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= 140, "It waits for the call in the air");
        verify(retryService).retryLines(orderId, lines);
    }

    @Test
    void settlingALineAsksStockFirstSoThatRetryingNeverConsumesTwice() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher());
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.COMPLETED);
        UUID project = UUID.randomUUID();
        order.setStockProjectId(project);

        // Consumida y la salida del exceso caida: el reintento no vuelve a consumir, solo saca el exceso.
        MaintenanceMaterialUsage over = reservedLine(order, "3.5");
        stockSays(stockClient, over, "ACTIVE");
        doThrow(new StockUnavailableException("stock down")).when(stockClient).output(eq(over.getMaterialId()), any(), any(), any(), any(), any(), any());
        synchronizer.consume(over);
        assertEquals(StockSyncStatus.FAILED, over.getStockSyncStatus());

        doNothing().when(stockClient).output(eq(over.getMaterialId()), any(), any(), any(), any(), any(), any());
        stockSays(stockClient, over, "CONSUMED");
        synchronizer.syncNow(over);

        assertEquals(StockSyncStatus.CONSUMED, over.getStockSyncStatus());
        verify(stockClient, times(1)).consume(over.getStockReservationId());
        verify(stockClient, times(2)).output(eq(over.getMaterialId()), eq(over.getWarehouseId()), eq(project), eq(new BigDecimal("1.5")),
                eq(order.getCode()), anyString(), any());

        // Liberada y la salida caida: el reintento no libera otra vez, saca lo usado.
        MaintenanceMaterialUsage partial = reservedLine(order, "0.5");
        partial.markFailed("consume: stock down");
        stockSays(stockClient, partial, "RELEASED");
        synchronizer.syncNow(partial);
        assertEquals(StockSyncStatus.CONSUMED, partial.getStockSyncStatus());
        verify(stockClient, never()).release(partial.getStockReservationId());
        verify(stockClient).output(eq(partial.getMaterialId()), eq(partial.getWarehouseId()), eq(project), eq(new BigDecimal("0.5")),
                eq(order.getCode()), anyString(), any());

        // Cambiada desde Almacen a 3: se liquida contra lo que stock tiene reservado, no contra los 2 previstos.
        MaintenanceMaterialUsage resized = reservedLine(order, "2.5");
        when(stockClient.findReservation(resized.getStockReservationId())).thenReturn(Optional.of(new StockReservation(
                resized.getStockReservationId(), resized.getMaterialId(), resized.getWarehouseId(), project, new BigDecimal("3"), "ACTIVE")));
        synchronizer.consume(resized);
        verify(stockClient).release(resized.getStockReservationId());
        verify(stockClient).output(eq(resized.getMaterialId()), eq(resized.getWarehouseId()), eq(project), eq(new BigDecimal("2.5")),
                eq(order.getCode()), anyString(), any());
        verify(stockClient, never()).consume(resized.getStockReservationId());

        // Nada usado ni retenido: la linea deja de estar pendiente. Antes /sync respondia 503 para siempre.
        MaintenanceMaterialUsage neverReserved = line(order);
        neverReserved.markFailed("reserve: stock down");
        assertDoesNotThrow(() -> synchronizer.syncNow(neverReserved));
        assertEquals(StockSyncStatus.NOT_REQUESTED, neverReserved.getStockSyncStatus());
        MaintenanceMaterialUsage unknownToStock = reservedLine(order, "0");
        unknownToStock.markFailed("consume: stock down");
        synchronizer.syncNow(unknownToStock);
        assertEquals(StockSyncStatus.RELEASED, unknownToStock.getStockSyncStatus());
        verify(stockClient, never()).release(unknownToStock.getStockReservationId());

        // Un estado que este servicio no conoce no se interpreta.
        MaintenanceMaterialUsage odd = reservedLine(order, "1");
        stockSays(stockClient, odd, "EXPIRED");
        assertThrows(StockUnavailableException.class, () -> synchronizer.syncNow(odd));
        assertEquals(StockSyncStatus.FAILED, odd.getStockSyncStatus());
        assertTrue(odd.getStockSyncError().contains("EXPIRED"));
    }

    @Test
    void cancellingReleasesOnlyWhatStockStillHoldsAndClosesEveryPendingLine() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher());
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.CANCELLED);

        MaintenanceMaterialUsage held = reservedLine(order, "0");
        held.markFailed("release: stock down");
        stockSays(stockClient, held, "ACTIVE");
        MaintenanceMaterialUsage consumed = reservedLine(order, "0");
        consumed.markFailed("release: stock down");
        stockSays(stockClient, consumed, "CONSUMED");
        MaintenanceMaterialUsage gone = reservedLine(order, "0");
        gone.markFailed("release: stock down");
        MaintenanceMaterialUsage rejected = line(order);
        rejected.markRejected("reserve: mto-stock rejected 'reserve 2 of GA70' with 409 STK-001: Insufficient available stock");

        List.of(held, consumed, gone, rejected).forEach(synchronizer::syncNow);

        assertEquals(StockSyncStatus.RELEASED, held.getStockSyncStatus());
        verify(stockClient).release(held.getStockReservationId());
        assertEquals(StockSyncStatus.CONSUMED, consumed.getStockSyncStatus(), "Consumed before the cancellation: the material left the warehouse");
        assertEquals(StockSyncStatus.RELEASED, gone.getStockSyncStatus());
        assertEquals(StockSyncStatus.NOT_REQUESTED, rejected.getStockSyncStatus());
        verify(stockClient, times(1)).release(any());
        verify(stockClient, never()).output(any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * Una reserva que se queda sin respuesta puede haber llegado a stock. El reintento de /sync manda
     * la misma clave, y stock devuelve la que ya hizo en vez de reservar otra vez.
     */
    @Test
    void aReservationRetriedAfterAnOutageSendsTheSameIdempotencyKey() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher());
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        order.setStockProjectId(UUID.randomUUID());
        MaintenanceMaterialUsage line = line(order);
        UUID reservationId = UUID.randomUUID();
        when(stockClient.reserve(any(), any(), any(), any(), any()))
                .thenThrow(new StockUnavailableException("Read timed out"))
                .thenReturn(new StockReservation(reservationId, null, null, null, new BigDecimal("2"), "ACTIVE"));

        synchronizer.reserve(line);
        assertEquals(StockSyncStatus.FAILED, line.getStockSyncStatus());
        assertEquals(StockRequestType.RESERVATION, line.getStockRequestInDoubt());
        synchronizer.syncNow(line);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(stockClient, times(2)).reserve(eq(line.getMaterialId()), eq(line.getWarehouseId()), eq(order.getStockProjectId()),
                eq(new BigDecimal("2")), keys.capture());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        assertEquals("mto-maintenance:" + line.getId() + ":reserve:first", keys.getValue());
        assertEquals(StockSyncStatus.RESERVED, line.getStockSyncStatus());
        assertEquals(reservationId, line.getStockReservationId());
        assertNull(line.getStockRequestInDoubt());
    }

    /**
     * Se uso menos de lo reservado: la reserva se libera y lo usado sale directo. Si esa salida se
     * queda sin respuesta, el reintento la encuentra liberada y llega a la salida por el otro camino;
     * tiene que mandar el mismo cuerpo con la misma clave, o stock la haria dos veces.
     */
    @Test
    void anOutputRetriedAfterAnOutageSendsTheSameBodyAndKeyWhicheverWayItComesBack() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher());
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.COMPLETED);
        UUID project = UUID.randomUUID();
        order.setStockProjectId(project);
        MaintenanceMaterialUsage partial = reservedLine(order, "0.5");
        stockSays(stockClient, partial, "ACTIVE");
        doThrow(new StockUnavailableException("Read timed out")).doNothing()
                .when(stockClient).output(any(), any(), any(), any(), any(), any(), any());

        synchronizer.consume(partial);
        assertEquals(StockSyncStatus.FAILED, partial.getStockSyncStatus());
        assertEquals(StockRequestType.OUTPUT, partial.getStockRequestInDoubt());
        stockSays(stockClient, partial, "RELEASED");
        synchronizer.syncNow(partial);

        ArgumentCaptor<String> notes = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(stockClient, times(2)).output(eq(partial.getMaterialId()), eq(partial.getWarehouseId()), eq(project),
                eq(new BigDecimal("0.5")), eq(order.getCode()), notes.capture(), keys.capture());
        assertEquals(notes.getAllValues().get(0), notes.getAllValues().get(1));
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        verify(stockClient, times(1)).release(partial.getStockReservationId());
        assertEquals(StockSyncStatus.CONSUMED, partial.getStockSyncStatus());
        assertNull(partial.getStockRequestInDoubt());
    }

    /**
     * La clave es de la linea, de la peticion y, en una reserva, de la anterior: no del cuerpo. Un
     * reintento lleva la misma aunque algo hubiera cambiado, y stock lo rechazaria con IDEM-001 en vez
     * de reservar otra vez; por eso lo que viaja no cambia mientras hay una peticion en duda. Una linea
     * sin guardar no tiene clave, porque la compartiria con otra igual.
     */
    @Test
    void theIdempotencyKeyIsTheLineTheRequestAndThePreviousReservationNotTheBody() {
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceMaterialUsage line = line(order);
        MaintenanceMaterialUsage twin = line(order);
        UUID previous = UUID.randomUUID();
        String first = MaterialStockSynchronizer.idempotencyKey(line, StockRequestType.RESERVATION, null);

        assertEquals("mto-maintenance:" + line.getId() + ":reserve:first", first);
        assertEquals("mto-maintenance:" + line.getId() + ":reserve:" + previous,
                MaterialStockSynchronizer.idempotencyKey(line, StockRequestType.RESERVATION, previous));
        assertEquals("mto-maintenance:" + line.getId() + ":output", MaterialStockSynchronizer.idempotencyKey(line, StockRequestType.OUTPUT, null));
        assertNotEquals(first, MaterialStockSynchronizer.idempotencyKey(twin, StockRequestType.RESERVATION, null));
        line.setPlannedQuantity(new BigDecimal("7"));
        assertEquals(first, MaterialStockSynchronizer.idempotencyKey(line, StockRequestType.RESERVATION, null));
        // Lo que acepta mto-stock en la cabecera: de 1 a 255 caracteres ASCII visibles.
        assertTrue(MaterialStockSynchronizer.idempotencyKey(line, StockRequestType.RESERVATION, previous).matches("[\\x21-\\x7E]{1,255}"));

        MaintenanceMaterialUsage unsaved = MaintenanceMaterialUsage.builder().order(order).materialId(UUID.randomUUID())
                .warehouseId(UUID.randomUUID()).plannedQuantity(BigDecimal.ONE).unit("ud").build();
        assertThrows(IllegalStateException.class, () -> MaterialStockSynchronizer.idempotencyKey(unsaved, StockRequestType.RESERVATION, null));
    }

    /**
     * Completar con una reserva en duda: antes de nada se repite, con su clave, y lo que stock devuelve
     * se consume. Antes, lo usado salia como salida directa y la reserva que quiza se habia creado se
     * quedaba en stock reteniendo el material.
     */
    @Test
    void completingAnOrderConfirmsAReservationInDoubtBeforeConsumingIt() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher());
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        order.setStockProjectId(UUID.randomUUID());
        MaintenanceMaterialUsage line = line(order);
        line.setConsumedQuantity(new BigDecimal("2"));
        UUID reservationId = UUID.randomUUID();
        when(stockClient.reserve(any(), any(), any(), any(), any()))
                .thenThrow(new StockUnavailableException("Read timed out"))
                .thenReturn(new StockReservation(reservationId, line.getMaterialId(), line.getWarehouseId(), order.getStockProjectId(),
                        new BigDecimal("2"), "ACTIVE"));
        when(stockClient.findReservation(reservationId)).thenReturn(Optional.of(new StockReservation(reservationId, line.getMaterialId(),
                line.getWarehouseId(), order.getStockProjectId(), new BigDecimal("2"), "ACTIVE")));

        synchronizer.reserve(line);
        synchronizer.consume(line);

        verify(stockClient, times(2)).reserve(any(), any(), any(), any(),
                eq(MaterialStockSynchronizer.idempotencyKey(line, StockRequestType.RESERVATION, null)));
        verify(stockClient).consume(reservationId);
        verify(stockClient, never()).output(any(), any(), any(), any(), any(), any(), any());
        assertEquals(StockSyncStatus.CONSUMED, line.getStockSyncStatus());
        assertNull(line.getStockRequestInDoubt());
    }

    /**
     * Cancelar con una reserva en duda: se repite y se libera lo que stock devuelva, que es lo que la
     * linea retenia. Si stock la rechaza ahora, es que nunca llego, y no hay nada que liberar.
     */
    @Test
    void cancellingAnOrderReleasesWhatAReservationInDoubtLeftInStock() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher());
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.CANCELLED);
        order.setStockProjectId(UUID.randomUUID());
        MaintenanceMaterialUsage landed = inDoubt(line(order), StockRequestType.RESERVATION);
        MaintenanceMaterialUsage lost = inDoubt(line(order), StockRequestType.RESERVATION);
        UUID reservationId = UUID.randomUUID();
        StockReservation reservation = new StockReservation(reservationId, landed.getMaterialId(), landed.getWarehouseId(),
                order.getStockProjectId(), new BigDecimal("2"), "ACTIVE");
        when(stockClient.reserve(eq(landed.getMaterialId()), any(), any(), any(), any())).thenReturn(reservation);
        when(stockClient.findReservation(reservationId)).thenReturn(Optional.of(reservation));
        when(stockClient.reserve(eq(lost.getMaterialId()), any(), any(), any(), any()))
                .thenThrow(new StockRejectedException("No stock", 409, StockRejectedException.INSUFFICIENT_STOCK, null));

        synchronizer.release(landed);
        synchronizer.release(lost);

        verify(stockClient).release(reservationId);
        assertEquals(StockSyncStatus.RELEASED, landed.getStockSyncStatus());
        assertEquals(reservationId, landed.getStockReservationId());
        assertEquals(StockSyncStatus.NOT_REQUESTED, lost.getStockSyncStatus());
        assertNull(lost.getStockRequestInDoubt());
        verify(stockClient, times(1)).release(any());
    }

    /**
     * Quitar una linea con una reserva en duda la confirma para liberarla; con una salida en duda no se
     * quita, porque el material quiza ya salio: primero hay que sincronizarla.
     */
    @Test
    void removingALineReleasesItsReservationInDoubtButNotWhileAnOutputIsInDoubt() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher());
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        order.setStockProjectId(UUID.randomUUID());
        MaintenanceMaterialUsage reserving = inDoubt(line(order), StockRequestType.RESERVATION);
        UUID reservationId = UUID.randomUUID();
        StockReservation reservation = new StockReservation(reservationId, reserving.getMaterialId(), reserving.getWarehouseId(),
                order.getStockProjectId(), new BigDecimal("2"), "ACTIVE");
        when(stockClient.reserve(any(), any(), any(), any(), any())).thenReturn(reservation);
        when(stockClient.findReservation(reservationId)).thenReturn(Optional.of(reservation));

        synchronizer.releaseNow(reserving);
        verify(stockClient).release(reservationId);

        MaintenanceMaterialUsage outputting = inDoubt(reservedLine(order, "1"), StockRequestType.OUTPUT);
        MaterialUsageException refused = assertThrows(MaterialUsageException.class, () -> synchronizer.releaseNow(outputting));
        assertTrue(refused.getMessage().contains("sync the line"), refused.getMessage());
        verify(stockClient, never()).findReservation(outputting.getStockReservationId());
        verify(stockClient, times(1)).release(any());
    }

    /**
     * Una salida en duda en una orden todavia abierta (su cierre fallo): sincronizar la termina, con su
     * clave, en vez de pedir otra reserva para un material que quiza ya salio del almacen.
     */
    @Test
    void syncingAnOpenOrderFinishesAnOutputInDoubtInsteadOfReservingAgain() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher());
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        order.setStockProjectId(UUID.randomUUID());
        MaintenanceMaterialUsage partial = reservedLine(order, "0.5");
        stockSays(stockClient, partial, "ACTIVE");
        doThrow(new StockUnavailableException("Read timed out")).doNothing()
                .when(stockClient).output(any(), any(), any(), any(), any(), any(), any());

        synchronizer.consume(partial);
        assertEquals(StockRequestType.OUTPUT, partial.getStockRequestInDoubt());
        stockSays(stockClient, partial, "RELEASED");
        synchronizer.syncNow(partial);

        verify(stockClient, never()).reserve(any(), any(), any(), any(), any());
        verify(stockClient, times(2)).output(any(), any(), any(), eq(new BigDecimal("0.5")), any(), any(),
                eq(MaterialStockSynchronizer.idempotencyKey(partial, StockRequestType.OUTPUT, null)));
        assertEquals(StockSyncStatus.CONSUMED, partial.getStockSyncStatus());
        assertNull(partial.getStockRequestInDoubt());
    }

    /**
     * Lo que viaja en una peticion en duda no cambia hasta que stock conteste: ni lo previsto ni lo
     * consumido de la linea, ni el proyecto de stock de la orden. Lo demas, y lo mismo con el mismo
     * valor, si; y en cuanto la peticion se resuelve, todo vuelve a poder cambiarse.
     */
    @Test
    void whileARequestIsInDoubtWhatTravelsInItCannotChange() {
        MaterialFixture materials = new MaterialFixture();
        MaintenanceOrder planned = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        planned.setStockProjectId(UUID.randomUUID());
        when(materials.lookups.order(planned.getId())).thenReturn(planned);
        MaintenanceMaterialUsage reserving = storedLine(materials, planned, inDoubt(line(planned), StockRequestType.RESERVATION));

        MaterialUsageException planning = assertThrows(MaterialUsageException.class, () -> materials.service.patch(planned.getId(),
                reserving.getId(), MergePatch.of(new MaterialUsageUpdateRequest(new BigDecimal("3"), null, null, null))));
        assertTrue(planning.getMessage().contains("reservation sent to stock without an answer yet"), planning.getMessage());
        materials.service.patch(planned.getId(), reserving.getId(),
                MergePatch.of(new MaterialUsageUpdateRequest(new BigDecimal("2.000"), null, true, null)));
        assertTrue(reserving.getAllowOverConsumption());
        assertEquals(0, new BigDecimal("2").compareTo(reserving.getPlannedQuantity()));

        MaintenanceOrder running = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        when(materials.lookups.order(running.getId())).thenReturn(running);
        MaintenanceMaterialUsage outputting = storedLine(materials, running, inDoubt(line(running), StockRequestType.OUTPUT));
        assertThrows(MaterialUsageException.class, () -> materials.service.patch(running.getId(), outputting.getId(),
                MergePatch.of(new MaterialUsageUpdateRequest(null, new BigDecimal("1"), null, null))));

        OrderFixture orders = new OrderFixture();
        when(orders.lookups.order(planned.getId())).thenReturn(planned);
        UUID project = planned.getStockProjectId();
        MaintenanceOrderUpdateRequest otherProject = new MaintenanceOrderUpdateRequest(null, null, null, null, null, null, null, null, null,
                null, null, null, UUID.randomUUID(), null);
        assertThrows(MaterialUsageException.class, () -> orders.service.patch(planned.getId(), MergePatch.of(otherProject)));
        assertThrows(MaterialUsageException.class,
                () -> orders.service.patch(planned.getId(), new MergePatch<>(orderUpdate(null), Set.of("stockProjectId"))));
        assertEquals(project, planned.getStockProjectId());
        orders.service.patch(planned.getId(), new MergePatch<>(orderUpdate(null), Set.of("description")));

        reserving.markReserved(UUID.randomUUID());
        orders.service.patch(planned.getId(), MergePatch.of(otherProject));
        assertEquals(otherProject.stockProjectId(), planned.getStockProjectId());
    }

    // ---------------------------------------------------------- stock sync retry

    /**
     * Una vuelta sincroniza las lineas FAILED y se para en la primera a la que stock sigue sin
     * contestar: las demas fallarian igual. Un rechazo o una linea que ya no esta no la paran.
     */
    @Test
    void theRetrySynchronizesFailedLinesAndStopsAtTheFirstOneStockDoesNotAnswer() {
        MaintenanceMaterialUsageRepository repository = mock(MaintenanceMaterialUsageRepository.class);
        MaintenanceMaterialUsageService materials = mock(MaintenanceMaterialUsageService.class);
        StockSyncRetryServiceImpl retry = new StockSyncRetryServiceImpl(repository, materials, freeLock());
        UUID orderId = UUID.randomUUID();
        List<UUID> ids = Stream.generate(UUID::randomUUID).limit(5).sorted().toList();
        when(repository.findRefsByStockSyncStatus(StockSyncStatus.FAILED))
                .thenReturn(ids.stream().map(id -> lineRef(orderId, id)).toList());
        when(materials.sync(orderId, ids.get(0))).thenReturn(usageResponse(StockSyncStatus.RESERVED));
        when(materials.sync(orderId, ids.get(1)))
                .thenThrow(new StockRejectedException("No stock", 409, StockRejectedException.INSUFFICIENT_STOCK, null));
        when(materials.sync(orderId, ids.get(2))).thenThrow(new NotFoundException("Material usage", ids.get(2)));
        when(materials.sync(orderId, ids.get(3))).thenThrow(new StockUnavailableException("Read timed out"));

        assertEquals(1, retry.retryFailedLines());

        verify(materials, times(4)).sync(any(), any());
        verify(materials, never()).sync(orderId, ids.get(4));
    }

    /**
     * La vuelta siguiente empieza despues de la ultima intentada: una linea que falla siempre no deja
     * a las de detras sin intentar.
     */
    @Test
    void theNextRetryStartsAfterTheLastLineTriedSoNoneIsLeftBehind() {
        MaintenanceMaterialUsageRepository repository = mock(MaintenanceMaterialUsageRepository.class);
        MaintenanceMaterialUsageService materials = mock(MaintenanceMaterialUsageService.class);
        StockSyncRetryServiceImpl retry = new StockSyncRetryServiceImpl(repository, materials, freeLock());
        UUID orderId = UUID.randomUUID();
        List<UUID> ids = Stream.generate(UUID::randomUUID).limit(3).sorted().toList();
        when(repository.findRefsByStockSyncStatus(StockSyncStatus.FAILED))
                .thenReturn(ids.stream().map(id -> lineRef(orderId, id)).toList());
        when(materials.sync(orderId, ids.get(0))).thenThrow(new StockUnavailableException("mto-stock answered 500"));
        when(materials.sync(orderId, ids.get(1))).thenReturn(usageResponse(StockSyncStatus.CONSUMED));
        when(materials.sync(orderId, ids.get(2))).thenReturn(usageResponse(StockSyncStatus.FAILED));

        assertEquals(0, retry.retryFailedLines());
        assertEquals(1, retry.retryFailedLines());

        InOrder order = inOrder(materials);
        order.verify(materials).sync(orderId, ids.get(0));
        order.verify(materials).sync(orderId, ids.get(1));
        order.verify(materials).sync(orderId, ids.get(2));
        order.verify(materials).sync(orderId, ids.get(0));
        when(repository.findRefsByStockSyncStatus(StockSyncStatus.FAILED)).thenReturn(List.of());
        assertEquals(0, retry.retryFailedLines());
    }

    /**
     * Con varias replicas la vuelta es una: la que no consigue el cerrojo de aviso no lee las lineas
     * ni llama a stock. El cerrojo de verdad, contra PostgreSQL, en {@code PersistenceLayerTest}.
     */
    @Test
    void theRetryDoesNothingWhileAnotherInstanceHoldsItsLock() {
        MaintenanceMaterialUsageRepository repository = mock(MaintenanceMaterialUsageRepository.class);
        MaintenanceMaterialUsageService materials = mock(MaintenanceMaterialUsageService.class);
        SessionAdvisoryLock heldElsewhere = mock(SessionAdvisoryLock.class);
        when(heldElsewhere.runIfFree(anyLong(), any())).thenReturn(false);
        StockSyncRetryServiceImpl retry = new StockSyncRetryServiceImpl(repository, materials, heldElsewhere);
        when(repository.findRefsByStockSyncStatus(StockSyncStatus.FAILED))
                .thenReturn(List.of(lineRef(UUID.randomUUID(), UUID.randomUUID())));

        assertEquals(0, retry.retryFailedLines());

        verify(heldElsewhere).runIfFree(eq(StockSyncRetryServiceImpl.LOCK_KEY), any());
        verify(repository, never()).findRefsByStockSyncStatus(any());
        verify(materials, never()).sync(any(), any());
        assertNotEquals(PreventiveDueSoonServiceImpl.LOCK_KEY, StockSyncRetryServiceImpl.LOCK_KEY);
    }

    /** Un cerrojo que siempre esta libre: corre el trabajo, como la unica instancia. */
    private static SessionAdvisoryLock freeLock() {
        SessionAdvisoryLock lock = mock(SessionAdvisoryLock.class);
        when(lock.runIfFree(anyLong(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });
        return lock;
    }

    /**
     * Cada 5 minutos con stock encendido; ni con {@code app.stock.enabled=false}, que es como corren
     * los tests, ni con {@code app.stock.sync-retry.enabled=false}.
     */
    @Test
    void theRetryIsScheduledEveryFiveMinutesOnlyWithStockEnabled() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(StockSyncRetryConfiguration.class)
                .withBean(StockSyncRetryService.class, () -> mock(StockSyncRetryService.class));

        runner.run(context -> {
            assertNull(context.getStartupFailure());
            List<Duration> intervals = context.getBean(ScheduledTaskHolder.class).getScheduledTasks().stream()
                    .map(task -> ((FixedDelayTask) task.getTask()).getIntervalDuration())
                    .toList();
            assertEquals(List.of(Duration.ofMinutes(5)), intervals);
        });
        runner.withPropertyValues("app.stock.enabled=false")
                .run(context -> assertTrue(context.getBeansOfType(StockSyncRetryConfiguration.class).isEmpty()));
        runner.withPropertyValues("app.stock.sync-retry.enabled=false")
                .run(context -> assertTrue(context.getBeansOfType(StockSyncRetryConfiguration.class).isEmpty()));
    }

    @Test
    void syncInAnOrderInProgressKeepsTheReservationOfTheLineAndReplacesOneReleasedFromTheWarehouse() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher());
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        UUID project = UUID.randomUUID();
        order.setStockProjectId(project);
        UUID replacement = UUID.randomUUID();
        when(stockClient.reserve(any(), any(), any(), any(), any())).thenReturn(new StockReservation(replacement, null, null, project, new BigDecimal("2"), "ACTIVE"));

        MaintenanceMaterialUsage alive = reservedLine(order, "0");
        UUID aliveId = alive.getStockReservationId();
        stockSays(stockClient, alive, "ACTIVE");
        synchronizer.syncNow(alive);
        assertEquals(StockSyncStatus.RESERVED, alive.getStockSyncStatus());
        assertEquals(aliveId, alive.getStockReservationId());

        MaintenanceMaterialUsage released = reservedLine(order, "0");
        UUID releasedId = released.getStockReservationId();
        stockSays(stockClient, released, "RELEASED");
        synchronizer.syncNow(released);
        assertEquals(StockSyncStatus.RESERVED, released.getStockSyncStatus());
        assertEquals(replacement, released.getStockReservationId());
        // Otra reserva y no un reintento de la liberada, asi que otra clave: la anterior va en ella.
        // Con la de aquella, stock devolveria la liberada.
        verify(stockClient).reserve(eq(released.getMaterialId()), eq(released.getWarehouseId()), eq(project), eq(new BigDecimal("2")),
                eq(MaterialStockSynchronizer.idempotencyKey(released, StockRequestType.RESERVATION, releasedId)));

        // Un cierre rechazado que llego a consumir la reserva: sigue siendo la de la linea, y se liquida al completar.
        MaintenanceMaterialUsage consumed = reservedLine(order, "3");
        UUID consumedId = consumed.getStockReservationId();
        consumed.markFailed("consume: stock down");
        stockSays(stockClient, consumed, "CONSUMED");
        synchronizer.syncNow(consumed);
        assertEquals(StockSyncStatus.RESERVED, consumed.getStockSyncStatus());
        assertEquals(consumedId, consumed.getStockReservationId());

        verify(stockClient, times(1)).reserve(any(), any(), any(), any(), any());
        verify(stockClient, never()).consume(any());
        verify(stockClient, never()).output(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aRejectionFromStockLeavesTheLineRejectedWithItsReasonAndAnExplicitSyncSaysWhy() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher());
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        order.setStockProjectId(UUID.randomUUID());
        MaintenanceMaterialUsage line = line(order);
        when(stockClient.reserve(any(), any(), any(), any(), any())).thenThrow(new StockRejectedException(
                "mto-stock rejected 'reserve 2 of GA70' with 409 STK-001: Insufficient available stock", 409, "STK-001", null));

        synchronizer.reserveAll(order);

        assertEquals(StockSyncStatus.REJECTED, line.getStockSyncStatus());
        assertEquals("reserve: mto-stock rejected 'reserve 2 of GA70' with 409 STK-001: Insufficient available stock", line.getStockSyncError());
        assertTrue(line.isSyncFailed(), "A rejected line blocks completing the order as a failed one does");

        StockRejectedException thrown = assertThrows(StockRejectedException.class, () -> synchronizer.syncNow(line));
        assertTrue(thrown.isInsufficientStock());
        assertEquals(StockSyncStatus.REJECTED, line.getStockSyncStatus());
    }

    @Test
    void reservingResolvesTheProjectOfThePackageOnceAndAnOutageThereLeavesTheLinesFailed() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher());
        UUID project = UUID.randomUUID();
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        MaintenanceMaterialUsage first = line(order);
        MaintenanceMaterialUsage second = line(order);
        when(stockClient.findProjectIdByCode("EP-6")).thenReturn(Optional.of(project));
        when(stockClient.reserve(any(), any(), eq(project), any(), any()))
                .thenAnswer(invocation -> new StockReservation(UUID.randomUUID(), null, null, project, invocation.getArgument(3), "ACTIVE"));

        synchronizer.reserveAll(order);

        assertEquals(project, order.getStockProjectId());
        assertEquals(StockSyncStatus.RESERVED, first.getStockSyncStatus());
        assertEquals(StockSyncStatus.RESERVED, second.getStockSyncStatus());
        verify(stockClient, times(1)).findProjectIdByCode("EP-6");

        // Stock caido al buscar el proyecto: no se reserva nada, pero las lineas lo dicen.
        MaintenanceOrder unresolved = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        unresolved.setExecutionPackageId(7L);
        MaintenanceMaterialUsage waiting = line(unresolved);
        when(stockClient.findProjectIdByCode("EP-7")).thenThrow(new StockUnavailableException("stock down"));
        synchronizer.reserveAll(unresolved);
        assertEquals(StockSyncStatus.FAILED, waiting.getStockSyncStatus());
        assertEquals("resolve project: stock down", waiting.getStockSyncError());
        assertNull(unresolved.getStockProjectId());

        // Stock responde, pero no tiene proyecto para el paquete: no hay contra que reservar y la linea ya no esta pendiente.
        doReturn(Optional.empty()).when(stockClient).findProjectIdByCode("EP-7");
        synchronizer.syncNow(waiting);
        assertEquals(StockSyncStatus.NOT_REQUESTED, waiting.getStockSyncStatus());
        verify(stockClient, times(2)).reserve(any(), any(), any(), any(), any());
    }

    @Test
    void aLineAlreadyConsumedInStockCannotChangeAnyMore() {
        MaterialFixture fixture = new MaterialFixture();
        // En curso con una linea consumida: un cierre rechazado por otra linea deja asi la orden.
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        MaintenanceMaterialUsage consumed = storedLine(fixture, order, reservedLine(order, "2"));
        consumed.markConsumed();

        assertThrows(MaterialUsageException.class, () -> fixture.service.update(order.getId(), consumed.getId(),
                new MaterialUsageUpdateRequest(null, new BigDecimal("3"), true, null)));
        assertEquals(0, new BigDecimal("2").compareTo(consumed.getConsumedQuantity()));
        assertFalse(consumed.getAllowOverConsumption());
    }

    @Test
    void materialLinesAreFrozenOnTerminalOrdersAndAReservedPlannedQuantityCannotChange() {
        MaterialFixture fixture = new MaterialFixture();
        MaintenanceOrder completed = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.COMPLETED);
        when(fixture.lookups.order(completed.getId())).thenReturn(completed);
        MaterialUsageRequest request = new MaterialUsageRequest(UUID.randomUUID(), "GA70", UUID.randomUUID(), BigDecimal.ONE, "ud", null, null);

        assertThrows(MaterialUsageException.class, () -> fixture.service.register(completed.getId(), request));

        MaintenanceOrder planned = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        MaintenanceMaterialUsage line = reservedLine(planned, "0");
        ReflectionTestUtils.setField(line, "id", UUID.randomUUID());
        when(fixture.lookups.order(planned.getId())).thenReturn(planned);
        when(fixture.repository.findByIdAndOrderId(line.getId(), planned.getId())).thenReturn(Optional.of(line));

        assertThrows(MaterialUsageException.class, () -> fixture.service.update(planned.getId(), line.getId(), new MaterialUsageUpdateRequest(new BigDecimal("5"), null, null, null)));
        fixture.service.update(planned.getId(), line.getId(), new MaterialUsageUpdateRequest(null, null, true, null));
        assertTrue(line.getAllowOverConsumption());
        // Un formulario reenvia la linea entera: la misma cantidad prevista no es un cambio.
        fixture.service.update(planned.getId(), line.getId(), new MaterialUsageUpdateRequest(new BigDecimal("2.000"), BigDecimal.ONE, null, null));
        assertEquals(0, BigDecimal.ONE.compareTo(line.getConsumedQuantity()));
        assertEquals(0, new BigDecimal("2").compareTo(line.getPlannedQuantity()));

        fixture.service.sync(planned.getId(), line.getId());
        verify(fixture.stock).syncNow(line);

        assertThrows(NotFoundException.class, () -> fixture.service.update(planned.getId(), UUID.randomUUID(), new MaterialUsageUpdateRequest(null, null, true, null)));
        assertThrows(NotFoundException.class, () -> fixture.service.register(planned.getId(),
                new MaterialUsageRequest(UUID.randomUUID(), "GA70", UUID.randomUUID(), BigDecimal.ONE, "ud", UUID.randomUUID(), null)),
                "A task id that is not one of the order's tasks");
    }

    @Test
    void aMaterialLineWithNeitherIdNorCodeIsRejectedInsteadOfFailing() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialLineFactory factory = new MaterialLineFactory(mock(MaintenanceMaterialUsageRepository.class), stockClient);
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);

        assertThrows(ValidationException.class, () -> factory.buildLine(order, null, null, " ", UUID.randomUUID(), BigDecimal.ONE, "ud", false),
                "It used to call trim() on the missing code and answer 500");
        verify(stockClient, never()).findMaterialByCode(any());
    }

    @Test
    void removingALineReleasesItsReservationFirstAndKeepsWhatCannotGo() {
        MaterialFixture fixture = new MaterialFixture();
        MaintenanceOrder planned = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        when(fixture.lookups.order(planned.getId())).thenReturn(planned);
        MaintenanceMaterialUsage reserved = storedLine(fixture, planned, reservedLine(planned, "0"));
        assertTrue(planned.getMaterials().contains(reserved));

        fixture.service.remove(planned.getId(), reserved.getId());

        InOrder sequence = inOrder(fixture.stock, fixture.repository);
        sequence.verify(fixture.stock).releaseNow(reserved);
        sequence.verify(fixture.repository).delete(reserved);
        assertFalse(planned.getMaterials().contains(reserved));

        // Stock no responde: la excepcion sale y la linea no se borra, porque su reserva seguiria viva.
        MaintenanceMaterialUsage held = storedLine(fixture, planned, reservedLine(planned, "0"));
        doThrow(new StockUnavailableException("stock down")).when(fixture.stock).releaseNow(held);
        assertThrows(StockUnavailableException.class, () -> fixture.service.remove(planned.getId(), held.getId()));
        verify(fixture.repository, never()).delete(held);

        // Consumida, o de una orden terminal: 409 MAT-001 sin hablar con stock.
        MaintenanceMaterialUsage consumed = reservedLine(planned, "1");
        consumed.markConsumed();
        storedLine(fixture, planned, consumed);
        assertThrows(MaterialUsageException.class, () -> fixture.service.remove(planned.getId(), consumed.getId()));
        MaintenanceOrder completed = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.COMPLETED);
        when(fixture.lookups.order(completed.getId())).thenReturn(completed);
        MaintenanceMaterialUsage frozen = storedLine(fixture, completed, line(completed));
        assertThrows(MaterialUsageException.class, () -> fixture.service.remove(completed.getId(), frozen.getId()));
        verify(fixture.stock, never()).releaseNow(consumed);
        verify(fixture.stock, never()).releaseNow(frozen);

        assertThrows(NotFoundException.class, () -> fixture.service.remove(planned.getId(), UUID.randomUUID()),
                "A line that is not one of the order's lines");
    }

    @Test
    void removingALineAsksStockHowItsReservationIsBeforeReleasingIt() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, new RecordingEventPublisher());
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.PLANNED);

        MaintenanceMaterialUsage held = reservedLine(order, "0");
        stockSays(stockClient, held, "ACTIVE");
        synchronizer.releaseNow(held);
        verify(stockClient).release(held.getStockReservationId());

        // Cancelada desde Almacen, o desconocida para stock: no retiene nada y no impide quitar la linea.
        MaintenanceMaterialUsage cancelled = reservedLine(order, "0");
        stockSays(stockClient, cancelled, "CANCELLED");
        assertDoesNotThrow(() -> synchronizer.releaseNow(cancelled));
        assertDoesNotThrow(() -> synchronizer.releaseNow(reservedLine(order, "0")));

        // Consumida desde Almacen: el material salio con esta linea, que no se quita.
        MaintenanceMaterialUsage consumed = reservedLine(order, "0");
        stockSays(stockClient, consumed, "CONSUMED");
        assertThrows(MaterialUsageException.class, () -> synchronizer.releaseNow(consumed));

        // Stock caido, al preguntar o al liberar: la reserva seguiria viva, asi que la excepcion sale.
        MaintenanceMaterialUsage unreachable = reservedLine(order, "0");
        when(stockClient.findReservation(unreachable.getStockReservationId())).thenThrow(new StockUnavailableException("stock down"));
        assertThrows(StockUnavailableException.class, () -> synchronizer.releaseNow(unreachable));
        MaintenanceMaterialUsage stuck = reservedLine(order, "0");
        stockSays(stockClient, stuck, "ACTIVE");
        doThrow(new StockUnavailableException("stock down")).when(stockClient).release(stuck.getStockReservationId());
        assertThrows(StockUnavailableException.class, () -> synchronizer.releaseNow(stuck));
        verify(stockClient, times(2)).release(any());

        // Sin reserva no hay nada que liberar, ni siquiera con stock deshabilitado.
        synchronizer.releaseNow(line(order));
        when(stockClient.isEnabled()).thenReturn(false);
        assertDoesNotThrow(() -> synchronizer.releaseNow(line(order)));
        assertThrows(StockUnavailableException.class, () -> synchronizer.releaseNow(reservedLine(order, "0")),
                "A reservation cannot be released with the stock client disabled");
    }

    private static MaintenanceMaterialUsage storedLine(MaterialFixture fixture, MaintenanceOrder order, MaintenanceMaterialUsage line) {
        ReflectionTestUtils.setField(line, "id", UUID.randomUUID());
        when(fixture.repository.findByIdAndOrderId(line.getId(), order.getId())).thenReturn(Optional.of(line));
        return line;
    }

    // ---------------------------------------------------------------- reports

    @Test
    void theProgressReportGroupsAssetsByPackageTrackAndTypeAndCountsTheSpansCovered() {
        MaintenanceReportRepository repository = mock(MaintenanceReportRepository.class);
        MaintenanceReportServiceImpl service = new MaintenanceReportServiceImpl(repository, UTC_REPORTS);
        CatenaryAsset first = profile("12-2.27", "12847.990");
        CatenaryAsset second = profile("12-2.28", "12899.290");
        CatenaryAsset third = profile("13-2.01", "13007.290");
        third.setLastPreventiveCompletedAt(Instant.parse("2026-01-15T00:00:00Z"));
        CatenaryAsset disconnector = disconnector("HSA-NS5");
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        when(repository.findReportableAssets(6L, null, null)).thenReturn(List.of(first, second, third, disconnector));
        when(repository.findAssetIdsWorkedBetween(from, to, UTC)).thenReturn(Set.of(first.getId()));

        ProgressReportResponse report = service.progress(6L, null, null, from, to);

        assertEquals(2, report.rows().size());
        ProgressRowResponse profiles = report.rows().get(0);
        assertEquals(CatenaryAssetType.PROFILE, profiles.assetType());
        assertEquals(3, profiles.totalAssets());
        assertEquals(2, profiles.checkedAssets(), "One worked in the window, one with a preventive completed inside it");
        assertEquals(0, new BigDecimal("0.6667").compareTo(profiles.completionRatio()));
        assertEquals(0, new BigDecimal("0.159").compareTo(profiles.totalKm()), "Two spans: 51.3 m and 108 m");
        assertEquals(0, new BigDecimal("0.051").compareTo(profiles.coveredKm()), "Only the span after the profile actually worked");
        ProgressRowResponse disconnectors = report.rows().get(1);
        assertEquals(1, disconnectors.totalAssets());
        assertEquals(0, disconnectors.checkedAssets());
        assertEquals(0, BigDecimal.ZERO.compareTo(disconnectors.totalKm()));
        assertEquals(4, report.totalAssets());
        assertEquals(2, report.checkedAssets());
        assertEquals(0, new BigDecimal("0.5000").compareTo(report.completionRatio()));

        // Fuera de la ventana el preventivo antiguo ya no cuenta.
        Instant laterFrom = Instant.parse("2026-02-01T00:00:00Z");
        when(repository.findAssetIdsWorkedBetween(laterFrom, null, UTC)).thenReturn(Set.of());
        assertEquals(0, service.progress(6L, null, null, laterFrom, null).checkedAssets());
    }

    @Test
    void filteredByTrackTheProgressReportCountsAnInsulatorOfTwoTracksUnderTheOneAskedFor() {
        MaintenanceReportRepository repository = mock(MaintenanceReportRepository.class);
        MaintenanceReportServiceImpl service = new MaintenanceReportServiceImpl(repository, UTC_REPORTS);
        CatenaryAsset profile = profile("40-1.02", "40100.000");
        profile.setTrackId(4L);
        CatenaryAsset insulator = CatenaryAsset.builder().code("SIN-12").name("SI-12").type(CatenaryAssetType.SECTION_INSULATOR)
                .trackId(2L).connectedTrackId(4L).executionPackageId(6L).build();
        ReflectionTestUtils.setField(insulator, "id", UUID.randomUUID());
        when(repository.findReportableAssets(null, 4L, null)).thenReturn(List.of(profile, insulator));
        when(repository.findAssetIdsWorkedBetween(null, null, UTC)).thenReturn(Set.of());

        ProgressReportResponse report = service.progress(null, 4L, null, null, null);

        assertEquals(List.of(4L, 4L), report.rows().stream().map(ProgressRowResponse::trackId).toList(),
                "The insulator reaches track 4 through its other track, and is counted there");
        assertEquals(List.of(CatenaryAssetType.PROFILE, CatenaryAssetType.SECTION_INSULATOR),
                report.rows().stream().map(ProgressRowResponse::assetType).toList());
    }

    @Test
    void theMonthlyReportAggregatesShiftsTasksProfilesAndMaterialsByMaterial() {
        MaintenanceReportRepository repository = mock(MaintenanceReportRepository.class);
        MaintenanceReportServiceImpl service = new MaintenanceReportServiceImpl(repository, UTC_REPORTS);
        YearMonth month = YearMonth.of(2026, 1);
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");
        MaintenanceShift closedWithKp = shift(2L, PossessionType.PARTIAL, ShiftStatus.CLOSED);
        closedWithKp.setNetWorkMinutes(290);
        closedWithKp.setStartKp(new BigDecimal("12847.990"));
        closedWithKp.setEndKp(new BigDecimal("14078.090"));
        MaintenanceShift closedWithoutKp = shift(1L, PossessionType.FULL, ShiftStatus.CLOSED);
        closedWithoutKp.setNetWorkMinutes(250);
        MaintenanceShift cancelled = shift(2L, PossessionType.PARTIAL, ShiftStatus.CANCELLED);
        MaintenanceShift planned = shift(2L, PossessionType.PARTIAL, ShiftStatus.PLANNED);
        when(repository.findShiftsBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 6L))
                .thenReturn(List.of(closedWithKp, closedWithoutKp, cancelled, planned));
        MaintenanceOrder completedOrder = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.COMPLETED);
        when(repository.findOrdersCompletedBetween(from, to, 6L)).thenReturn(List.of(completedOrder));
        CatenaryAsset profile = profile("12-2.27", "12847.990");
        MaintenanceTask onProfile = task(completedOrder, MaintenanceTaskStatus.COMPLETED);
        onProfile.setAsset(profile);
        MaintenanceTask onProfileAgain = task(completedOrder, MaintenanceTaskStatus.COMPLETED);
        onProfileAgain.setAsset(profile);
        MaintenanceTask onOtherProfile = task(completedOrder, MaintenanceTaskStatus.COMPLETED);
        onOtherProfile.setAsset(profile("12-2.28", "12899.290"));
        MaintenanceTask withoutAsset = task(completedOrder, MaintenanceTaskStatus.COMPLETED);
        when(repository.findTasksCompletedBetween(from, to, 6L)).thenReturn(List.of(onProfile, onProfileAgain, onOtherProfile, withoutAsset));
        when(repository.findOrdersCreatedBetween(from, to, 6L)).thenReturn(List.of(
                order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.DRAFT), order(MaintenanceOrderType.URGENT, MaintenanceOrderStatus.DRAFT),
                order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT)));
        when(repository.countDefectsDetectedBetween(from, to, 6L)).thenReturn(4L);
        when(repository.countDefectsResolvedBetween(from, to, 6L)).thenReturn(3L);
        MaintenanceMaterialUsage droppers = line(completedOrder);
        droppers.setConsumedQuantity(new BigDecimal("2"));
        MaintenanceMaterialUsage moreDroppers = MaintenanceMaterialUsage.builder().order(completedOrder).materialId(droppers.getMaterialId()).materialCode("GA70")
                .warehouseId(UUID.randomUUID()).plannedQuantity(new BigDecimal("1.5")).consumedQuantity(new BigDecimal("1.5")).unit("ud").build();
        MaintenanceMaterialUsage clamps = MaintenanceMaterialUsage.builder().order(completedOrder).materialId(UUID.randomUUID()).materialCode("CL-10")
                .warehouseId(UUID.randomUUID()).plannedQuantity(new BigDecimal("4")).consumedQuantity(new BigDecimal("4")).unit("ud").build();
        when(repository.findMaterialsOfOrders(List.of(completedOrder.getId()))).thenReturn(List.of(droppers, moreDroppers, clamps));

        MonthlyReportResponse report = service.monthly(month, 6L);

        assertEquals(month, report.month());
        assertEquals(4, report.shiftsPlanned());
        assertEquals(2, report.shiftsClosed());
        assertEquals(1, report.shiftsCancelled());
        assertEquals(540, report.netWorkMinutes());
        assertEquals(0, new BigDecimal("270.0").compareTo(report.averageNetMinutesPerShift()));
        assertEquals(0, new BigDecimal("1.230").compareTo(report.coveredKm()), "Only closed shifts with a kp range add kilometres");
        assertEquals(1, report.ordersCompleted());
        assertEquals(4, report.tasksCompleted());
        assertEquals(2, report.profilesChecked(), "The same profile twice counts once; a task without asset does not count");
        assertEquals(4, report.defectsDetected());
        assertEquals(3, report.defectsResolved());
        assertEquals(2, report.correctiveOrdersCreated(), "CORRECTIVE and URGENT, not PREVENTIVE");
        assertEquals(List.of("GA70", "CL-10"), report.materials().stream().map(line -> line.materialCode()).toList());
        assertEquals(0, new BigDecimal("3.5").compareTo(report.materials().getFirst().consumedQuantity()));

        when(repository.findShiftsBetween(any(), any(), isNull())).thenReturn(List.of());
        assertEquals(0, BigDecimal.ZERO.compareTo(service.monthly(month, null).averageNetMinutesPerShift()), "No closed shifts: no division by zero");
    }

    // -------------------------------------------------------- report exports

    @Test
    void theExportServiceHandsEachReportToTheExporterOfTheRequestedFormat() {
        RecordingExporter workbook = new RecordingExporter(ReportFormat.XLSX);
        RecordingExporter printable = new RecordingExporter(ReportFormat.PDF);
        ReportExportServiceImpl service = new ReportExportServiceImpl(List.of(workbook, printable), UTC_REPORTS);

        service.exportShiftReport(shiftReport(ShiftStatus.CLOSED, List.of(shiftReportRow())), ReportFormat.XLSX);
        service.exportProgressReport(progressReport(), ReportFormat.PDF);
        service.exportMonthlyReport(monthlyReport(6L), ReportFormat.XLSX);

        assertEquals(List.of("Shift report", "Monthly report"), workbook.sheetNames());
        assertEquals(List.of("Progress"), printable.sheetNames());
        // JSON no llega hasta aqui: el controlador responde el DTO. Si llegara seria un error de
        // programacion, no una peticion mal hecha, y por eso no es una ValidationException.
        assertThrows(IllegalArgumentException.class,
                () -> service.exportProgressReport(progressReport(), ReportFormat.JSON));
    }

    @Test
    void theExportServiceRefusesToStartWithADuplicatedFormatOrWithoutOneForEveryFormatOffered() {
        RecordingExporter workbook = new RecordingExporter(ReportFormat.XLSX);

        // Con dos para el mismo formato, cual gana dependeria del orden de escaneo del classpath.
        assertThrows(IllegalStateException.class,
                () -> new ReportExportServiceImpl(List.of(workbook, new RecordingExporter(ReportFormat.XLSX)), UTC_REPORTS));
        // Un formato que ?format acepta y nadie escribe seria un 500 la primera vez que lo pidieran.
        assertThrows(IllegalStateException.class, () -> new ReportExportServiceImpl(List.of(workbook), UTC_REPORTS));
        assertThrows(IllegalStateException.class, () -> new ReportExportServiceImpl(List.of(new RecordingExporter(ReportFormat.JSON)), UTC_REPORTS));
        assertDoesNotThrow(() -> new ReportExportServiceImpl(List.of(workbook, new RecordingExporter(ReportFormat.PDF)), UTC_REPORTS));
    }

    @Test
    void anAbsentFormatMeansJsonAndAnUnknownOneIsARejectedRequest() {
        assertEquals(ReportFormat.JSON, ReportFormat.of(null));
        assertEquals(ReportFormat.JSON, ReportFormat.of("  "));
        assertEquals(ReportFormat.JSON, ReportFormat.of("json"));
        assertEquals(ReportFormat.XLSX, ReportFormat.of("xlsx"));
        assertEquals(ReportFormat.XLSX, ReportFormat.of(" XLSX "), "Case does not decide whether a link works");
        assertEquals(ReportFormat.PDF, ReportFormat.of("Pdf"));

        ValidationException rejected = assertThrows(ValidationException.class, () -> ReportFormat.of("csv"));
        assertTrue(rejected.getMessage().contains("csv"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("xlsx"), "The message has to say what is accepted");
    }

    @Test
    void theShiftLayoutExportsAShiftThatHasNotStartedWithItsTimesEmptyAndItsFileNameStable() {
        ReportDocument document = ShiftReportLayout.of(shiftReport(ShiftStatus.PLANNED, List.of()), AT_UTC);

        assertEquals("shift-report-2026-01-27-SH-000001", document.fileBaseName());
        assertEquals(ReportDocument.Layout.LANDSCAPE, document.layout());
        assertEquals(ReportValue.EMPTY, headerField(document, "Actual start"));
        assertEquals(ReportValue.EMPTY, headerField(document, "Net work invalid"), "an unknown label is absent, not a crash");
        assertEquals(ReportValue.EMPTY, headerField(document, "Net work minutes"));
        assertEquals(new ReportValue.Text("PLANNED"), headerField(document, "Status"));
        assertTrue(document.table().rows().isEmpty(), "a shift with no tasks still has a table to head");
        assertEquals(19, document.table().columns().size());
        assertEquals("Asset", document.table().columns().get(4).header(), "a row can be an insulator, not only a profile");
        // Sin inspecciones el anexo sigue estando, vacio: el lector ve que no hubo, no que se perdio.
        assertEquals(List.of("Inspections", "Crew", "Vehicles", "Instruments"), document.appendices().stream().map(ReportDocument.Appendix::title).toList());
        assertTrue(document.appendices().getFirst().table().rows().isEmpty());
    }

    @Test
    void theShiftLayoutPutsTheInspectionsOfTheShiftInTheirOwnAppendixAndCountsThem() {
        ShiftReportResponse base = shiftReport(ShiftStatus.CLOSED, List.of(shiftReportRow()));
        ShiftReportInspectionResponse inspection = new ShiftReportInspectionResponse(UUID.randomUUID(), "INS-000040", LocalDate.of(2026, 1, 28),
                "SIN-41", "SI-W41", CatenaryAssetType.SECTION_INSULATOR, 3L, new BigDecimal("12921.500"), InspectionKind.VISUAL,
                InspectionResult.UNSAFE, "dana", "Runner burnt", "DEF-000007", "MO-000031");
        ShiftReportResponse report = new ShiftReportResponse(base.shift(), base.tasksCompleted(), base.tasksPending(), base.profilesReviewed(),
                base.defectsFound(), base.defectsResolved(), base.rows(), 1, List.of(inspection));

        ReportDocument document = ShiftReportLayout.of(report, AT_UTC);

        ReportDocument.Table table = document.appendices().getFirst().table();
        assertEquals(List.of("Code", "Date", "Asset", "Asset name", "Type", "Track", "Kp", "Kind", "Result", "Inspector", "Findings",
                "Defect", "Corrective order"), table.columns().stream().map(ReportDocument.Column::header).toList());
        List<ReportValue> row = table.rows().getFirst();
        assertEquals(new ReportValue.Text("INS-000040"), row.get(0));
        assertEquals(new ReportValue.Text("SECTION_INSULATOR"), row.get(4));
        assertEquals(new ReportValue.Text("UNSAFE"), row.get(8));
        assertEquals(new ReportValue.Text("MO-000031"), row.get(12));
        assertEquals(new ReportValue.Count(1), totalField(document, "Inspections recorded"));
    }

    @Test
    void theProgressAndMonthlyFileNamesCarryTheScopeTheyActuallyCover() {
        // El DTO del avance no lleva los filtros que lo produjeron: el ambito sale de las filas, y
        // solo cuando todas coinciden, para que el nombre no afirme algo que el contenido desmiente.
        assertEquals("progress-report-2026-01-31-ep6-track2",
                ProgressReportLayout.of(progressReport(), AT_UTC).fileBaseName());
        assertEquals("progress-report-2026-01-31-ep6",
                ProgressReportLayout.of(progressReportAcrossTwoTracks(), AT_UTC).fileBaseName(),
                "the package every row shares stays; the track they do not share drops");

        assertEquals("monthly-report-2026-01-ep6", MonthlyReportLayout.of(monthlyReport(6L), AT_UTC).fileBaseName());
        assertEquals("monthly-report-2026-01", MonthlyReportLayout.of(monthlyReport(null), AT_UTC).fileBaseName());
        assertEquals(ReportDocument.Layout.PORTRAIT, MonthlyReportLayout.of(monthlyReport(6L), AT_UTC).layout());
    }

    @Test
    void reportsPrintAndCutTheirDaysInTheConfiguredZoneWhileTheJsonStaysUtc() {
        ZoneId jerusalem = ZoneId.of("Asia/Jerusalem");
        ShiftReportResponse report = shiftReport(ShiftStatus.CLOSED, List.of(shiftReportRow()));

        // El turno arranco a las 21:10 UTC del 27 de enero: en Israel, en invierno, eran las 23:10.
        ReportDocument local = ShiftReportLayout.of(report, new ReportTime(GENERATED_AT, jerusalem));
        assertEquals(ReportValue.timestamp(LocalDateTime.of(2026, 1, 27, 23, 10)), headerField(local, "Actual start"));
        assertEquals(LocalDateTime.of(2026, 1, 28, 7, 30), local.generatedAt());
        assertEquals(ReportValue.timestamp(LocalDateTime.of(2026, 1, 27, 21, 10)),
                headerField(ShiftReportLayout.of(report, AT_UTC), "Actual start"));

        // Y el mes que cuenta es el mismo en el que se imprime: febrero empieza a las 22:00 UTC del 31.
        MaintenanceReportRepository repository = mock(MaintenanceReportRepository.class);
        MaintenanceReportServiceImpl service = new MaintenanceReportServiceImpl(repository, new ReportProperties("Asia/Jerusalem"));
        service.monthly(YearMonth.of(2026, 2), 6L);
        verify(repository).findOrdersCompletedBetween(Instant.parse("2026-01-31T22:00:00Z"), Instant.parse("2026-02-28T22:00:00Z"), 6L);
        verify(repository).findTasksCompletedBetween(Instant.parse("2026-01-31T22:00:00Z"), Instant.parse("2026-02-28T22:00:00Z"), 6L);

        service.progress(6L, null, null, Instant.parse("2026-02-01T00:00:00Z"), null);
        verify(repository).findAssetIdsWorkedBetween(Instant.parse("2026-02-01T00:00:00Z"), null, jerusalem);
    }

    @Test
    void theReportTimeZoneDefaultsToWhereTheCrewsWorkAndAMisspeltOneStopsTheStartup() {
        assertEquals(ZoneId.of("Asia/Jerusalem"), new ReportProperties(null).zone());
        assertEquals(ZoneId.of("Asia/Jerusalem"), new ReportProperties(" ").zone());
        assertEquals(ZoneId.of("Europe/Madrid"), new ReportProperties(" Europe/Madrid ").zone());
        IllegalArgumentException misspelt = assertThrows(IllegalArgumentException.class, () -> new ReportProperties("Asia/Jerusalen"));
        assertTrue(misspelt.getMessage().contains("Asia/Jerusalen"), misspelt.getMessage());
    }

    @Test
    void aRowThatDoesNotMatchItsColumnsIsRejectedWhereTheLayoutIsToBlame() {
        List<ReportDocument.Column> columns = List.of(new ReportDocument.Column("Kp", 5), new ReportDocument.Column("Status", 5));

        assertThrows(IllegalArgumentException.class,
                () -> new ReportDocument.Table(columns, List.of(List.of(ReportValue.EMPTY))));
        assertDoesNotThrow(() -> new ReportDocument.Table(columns, List.of(List.of(ReportValue.EMPTY, ReportValue.EMPTY))));
    }

    private static final Instant GENERATED_AT = Instant.parse("2026-01-28T05:30:00Z");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ReportProperties UTC_REPORTS = new ReportProperties("UTC");
    private static final ReportTime AT_UTC = new ReportTime(GENERATED_AT, UTC);

    /** Se queda con el documento en vez de escribirlo: aqui se prueba el reparto, no el fichero. */
    private static final class RecordingExporter implements ReportExporter {

        private final ReportFormat format;
        private final List<ReportDocument> exported = new ArrayList<>();

        RecordingExporter(ReportFormat format) {
            this.format = format;
        }

        @Override
        public ReportFormat format() {
            return format;
        }

        @Override
        public ExportedReport export(ReportDocument document) {
            exported.add(document);
            return new ExportedReport(document.fileBaseName(), "application/octet-stream", new byte[0]);
        }

        List<String> sheetNames() {
            return exported.stream().map(ReportDocument::sheetName).toList();
        }
    }

    private static ReportValue totalField(ReportDocument document, String label) {
        return document.totals().stream()
                .filter(field -> field.label().equals(label))
                .map(ReportDocument.Field::value)
                .findFirst().orElse(ReportValue.EMPTY);
    }

    private static ReportValue headerField(ReportDocument document, String label) {
        return document.header().stream()
                .filter(field -> field.label().equals(label))
                .map(ReportDocument.Field::value)
                .findFirst().orElse(ReportValue.EMPTY);
    }

    private static ShiftReportResponse shiftReport(ShiftStatus status, List<ShiftReportRowResponse> rows) {
        boolean closed = status == ShiftStatus.CLOSED;
        MaintenanceShiftResponse shift = new MaintenanceShiftResponse(UUID.randomUUID(), "SH-000001", LocalDate.of(2026, 1, 27),
                new MaintenanceTeamSummaryResponse(UUID.randomUUID(), "A", "Team A - Rishpon", "Rishpon"),
                "Rishpon", "Maintenance vehicle A", PossessionType.PARTIAL,
                Instant.parse("2026-01-27T21:00:00Z"), Instant.parse("2026-01-28T05:00:00Z"),
                closed ? Instant.parse("2026-01-27T21:10:00Z") : null,
                closed ? Instant.parse("2026-01-28T04:30:00Z") : null,
                closed ? Instant.parse("2026-01-27T21:25:00Z") : null,
                closed ? 290 : null,
                List.of(), "PT-14", "Rishpon depot", 6L, List.of(2L),
                new BigDecimal("12847.990"), new BigDecimal("14078.090"),
                "3 linemen", null, null, List.of(), List.of(), List.of(), "Laser", status, null, null, null);
        return new ShiftReportResponse(shift, rows.size(), 0, rows.size(), 0, 0, rows, 0, List.of());
    }

    private static ShiftReportRowResponse shiftReportRow() {
        return new ShiftReportRowResponse(1, UUID.randomUUID(), "MO-000001", 6L, 2L, "PRF-12-2.27", "12-2.27",
                new BigDecimal("12847.990"), "A/S", List.of("W31 1:9"), List.of("RG-01"), "Insulators checked", null,
                List.of("2 ud GA70"), Instant.parse("2026-01-27T21:40:00Z"), Instant.parse("2026-01-27T22:00:00Z"),
                MaintenanceTaskStatus.COMPLETED, true, null, List.of());
    }

    private static ProgressReportResponse progressReport() {
        return new ProgressReportResponse(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-31T23:59:59Z"),
                412, 318, new BigDecimal("0.7718"), new BigDecimal("17.240"), new BigDecimal("22.340"),
                List.of(new ProgressRowResponse(6L, 2L, CatenaryAssetType.PROFILE, 412, 318,
                        new BigDecimal("0.7718"), new BigDecimal("17.240"), new BigDecimal("22.340"))));
    }

    private static ProgressReportResponse progressReportAcrossTwoTracks() {
        ProgressReportResponse single = progressReport();
        List<ProgressRowResponse> rows = new ArrayList<>(single.rows());
        rows.add(new ProgressRowResponse(6L, 3L, CatenaryAssetType.PROFILE, 10, 1,
                new BigDecimal("0.1000"), BigDecimal.ZERO, BigDecimal.ZERO));
        return new ProgressReportResponse(single.from(), single.to(), single.totalAssets(), single.checkedAssets(),
                single.completionRatio(), single.coveredKm(), single.totalKm(), rows);
    }

    private static MonthlyReportResponse monthlyReport(Long executionPackageId) {
        return new MonthlyReportResponse(YearMonth.of(2026, 1), executionPackageId, 18, 16, 2, 4640,
                new BigDecimal("290.0"), 12, 287, 264, new BigDecimal("17.240"), 31, 22, 5,
                List.of(new MonthlyMaterialLineResponse(UUID.randomUUID(), "GA70", "ud", new BigDecimal("48.000"))));
    }

    // ---------------------------------------------------------------- events

    @Test
    void orderAndDefectTransitionsPublishTheirEvents() {
        RecordingEventPublisher events = new RecordingEventPublisher();
        StatusHistoryServiceImpl service = new StatusHistoryServiceImpl(mock(MaintenanceStatusHistoryRepository.class),
                mock(StatusHistoryMapper.class), events);
        MaintenanceOrder order = order(MaintenanceOrderType.URGENT, MaintenanceOrderStatus.DRAFT);
        order.setAssignedUser("yossi");
        CatenaryDefect defect = defect(DefectStatus.OPEN);

        service.recordOrderChange(order, null, "DRAFT", "Order created");
        service.recordOrderChange(order, "PLANNED", "ASSIGNED", "week 4");
        service.recordOrderChange(order, "IN_PROGRESS", "IN_PROGRESS", "Reassigned");
        service.recordDefectChange(defect, null, "OPEN", "found");
        service.recordDefectChange(defect, "OPEN", "IN_PROGRESS", "Linked to order MO-1");
        service.recordDefectChange(defect, "IN_PROGRESS", "IN_PROGRESS", "Re-linked to order MO-2");

        assertEquals(List.of("order.created", "order.status-changed", "order.reassigned", "defect.created", "defect.status-changed"),
                events.names(), "A defect re-linked without changing state stays in the history only");
        DomainEvent created = events.published.get(0);
        assertEquals(order.getId().toString(), created.entityId());
        assertEquals("MO-DRAFT", created.values().get("code"));
        assertEquals("URGENT", created.values().get("type"));
        assertEquals("CRITICAL", created.values().get("priority"));
        assertEquals("SEC-T2", created.values().get("assetCode"));
        assertEquals(2L, created.values().get("trackId"));
        assertEquals("Order created", created.values().get("comment"));
        DomainEvent assigned = events.published.get(1);
        assertEquals("PLANNED", assigned.values().get("from"));
        assertEquals("ASSIGNED", assigned.values().get("to"));
        assertEquals("yossi", assigned.values().get("assignedUser"), "With to=ASSIGNED it is whom to notify");
        assertEquals("Reassigned", events.published.get(2).values().get("comment"));
        DomainEvent defectCreated = events.published.get(3);
        assertEquals(defect.getId().toString(), defectCreated.entityId());
        assertEquals("MEDIUM", defectCreated.values().get("severity"));
        assertEquals("PRF-13-2.10", defectCreated.values().get("assetCode"));
        assertEquals("IN_PROGRESS", events.published.get(4).values().get("to"));
    }

    @Test
    void startingAndClosingAShiftPublishTheirEvents() {
        ShiftFixture fixture = new ShiftFixture();
        MaintenanceShift shift = shift(2L, PossessionType.PARTIAL, ShiftStatus.PLANNED);
        when(fixture.lookups.shift(shift.getId())).thenReturn(shift);
        Instant actualStart = Instant.parse("2026-01-27T21:10:00Z");

        fixture.service.start(shift.getId(), new StartShiftRequest(actualStart, null));
        fixture.service.close(shift.getId(), new CloseShiftRequest(Instant.parse("2026-01-28T04:30:00Z"), null, null, "smooth night"));

        assertEquals(List.of("shift.started", "shift.closed"), fixture.events.names());
        DomainEvent started = fixture.events.published.get(0);
        assertEquals(shift.getId().toString(), started.entityId());
        assertEquals(shift.getCode(), started.values().get("code"));
        assertEquals("IN_PROGRESS", started.values().get("status"));
        assertEquals("PARTIAL", started.values().get("possessionType"));
        assertEquals(actualStart, started.values().get("actualStart"));
        assertEquals(List.of(2L), started.values().get("trackIds"));
        DomainEvent closed = fixture.events.published.get(1);
        assertEquals("CLOSED", closed.values().get("status"));
        assertEquals(Instant.parse("2026-01-28T04:30:00Z"), closed.values().get("actualEnd"));
        assertEquals(440, closed.values().get("netWorkMinutes"), "From 21:10 to 04:30 without a voltage cut-off");
    }

    @Test
    void materialLinesTellWhatStockAnswered() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        RecordingEventPublisher events = new RecordingEventPublisher();
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, events);
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        order.setStockProjectId(UUID.randomUUID());

        MaintenanceMaterialUsage rejected = line(order);
        when(stockClient.reserve(any(), any(), any(), any(), any())).thenThrow(new StockRejectedException("Insufficient stock", 409, "STK-001", null));
        synchronizer.reserve(rejected);

        MaintenanceMaterialUsage inDoubt = line(order);
        org.mockito.Mockito.reset(stockClient);
        when(stockClient.isEnabled()).thenReturn(true);
        when(stockClient.reserve(any(), any(), any(), any(), any())).thenThrow(new StockUnavailableException("Read timed out"));
        synchronizer.reserve(inDoubt);

        MaintenanceOrder withoutProject = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        MaintenanceMaterialUsage failed = line(withoutProject);
        when(stockClient.findProjectIdByCode("EP-6")).thenThrow(new StockUnavailableException("stock down"));
        synchronizer.reserve(failed);

        assertEquals(List.of("material.rejected", "material.in-doubt", "material.failed"), events.names());
        DomainEvent first = events.published.get(0);
        assertEquals(rejected.getId().toString(), first.entityId());
        assertEquals("STK-001", first.values().get("stockErrorCode"));
        assertEquals(409, first.values().get("stockHttpStatus"));
        assertEquals("reserve", first.values().get("step"));
        assertEquals("Insufficient stock", first.values().get("reason"));
        assertEquals(order.getCode(), first.values().get("orderCode"));
        assertEquals("GA70", first.values().get("materialCode"));
        assertEquals("REJECTED", first.values().get("stockSyncStatus"));
        assertNull(first.values().get("request"), "Stock said no, so nothing is in doubt");
        DomainEvent second = events.published.get(1);
        assertEquals(inDoubt.getId().toString(), second.entityId());
        assertEquals("RESERVATION", second.values().get("request"), "The reservation was sent and got no answer");
        assertEquals("FAILED", second.values().get("stockSyncStatus"));
        assertEquals("Read timed out", second.values().get("reason"));
        DomainEvent third = events.published.get(2);
        assertEquals(failed.getId().toString(), third.entityId());
        assertNull(third.values().get("request"), "Nothing was sent: the project could not be resolved");
        assertEquals("resolve project", third.values().get("step"));
    }

    @Test
    void aStockFailureIsPublishedOnceAndNotAgainOnEveryRetry() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        RecordingEventPublisher events = new RecordingEventPublisher();
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient, events);
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        order.setStockProjectId(UUID.randomUUID());
        MaintenanceMaterialUsage line = line(order);

        when(stockClient.reserve(any(), any(), any(), any(), any())).thenThrow(new StockUnavailableException("Connection refused"));
        synchronizer.reserve(line);
        // Lo que hace el reintento automatico cada 5 minutos mientras stock sigue caido, con el texto que toque.
        assertThrows(StockUnavailableException.class, () -> synchronizer.syncNow(line));
        org.mockito.Mockito.reset(stockClient);
        when(stockClient.isEnabled()).thenReturn(true);
        when(stockClient.reserve(any(), any(), any(), any(), any())).thenThrow(new StockUnavailableException("CircuitBreaker 'stock' is OPEN"));
        assertThrows(StockUnavailableException.class, () -> synchronizer.syncNow(line));

        assertEquals(List.of("material.in-doubt"), events.names(), "One failure, however many rounds it takes stock to come back");
        assertEquals(StockSyncStatus.FAILED, line.getStockSyncStatus());

        // Stock vuelve: la reserva en duda se confirma. Si despues vuelve a fallar, es otro fallo y se cuenta.
        org.mockito.Mockito.reset(stockClient);
        when(stockClient.isEnabled()).thenReturn(true);
        UUID reservation = UUID.randomUUID();
        when(stockClient.reserve(any(), any(), any(), any(), any()))
                .thenReturn(new StockReservation(reservation, line.getMaterialId(), line.getWarehouseId(), order.getStockProjectId(), new BigDecimal("2"), "ACTIVE"));
        synchronizer.syncNow(line);
        assertEquals(StockSyncStatus.RESERVED, line.getStockSyncStatus());
        when(stockClient.findReservation(reservation)).thenThrow(new StockUnavailableException("Read timed out"));
        assertThrows(StockUnavailableException.class, () -> synchronizer.syncNow(line));

        assertEquals(List.of("material.in-doubt", "material.failed"), events.names());

        // Un rechazo repetido tampoco se repite, salvo que cambie el motivo.
        MaintenanceMaterialUsage rejected = line(order);
        when(stockClient.reserve(any(), any(), any(), any(), any()))
                .thenThrow(new StockRejectedException("Insufficient stock", 409, "STK-001", null))
                .thenThrow(new StockRejectedException("Insufficient stock", 409, "STK-001", null))
                .thenThrow(new StockRejectedException("Material retired", 422, "STK-422", null));
        synchronizer.reserve(rejected);
        assertThrows(StockRejectedException.class, () -> synchronizer.syncNow(rejected));
        assertThrows(StockRejectedException.class, () -> synchronizer.syncNow(rejected));

        assertEquals(List.of("material.in-doubt", "material.failed", "material.rejected", "material.rejected"), events.names());
    }

    @Test
    void disablingAnAssetHerePublishesOnceAndOnlyWhenItWasNotDisabledYet() {
        AssetFixture fixture = new AssetFixture();
        CatenaryAsset asset = profile("12-2.27", "12847.990");
        when(fixture.lookups.asset(asset.getId())).thenReturn(asset);

        fixture.service.disable(asset.getId());
        fixture.service.disable(asset.getId());
        fixture.service.update(asset.getId(), new CatenaryAssetUpdateRequest(null, null, true, null, null, null, null, null, null, null, null, null, null));
        fixture.service.update(asset.getId(), new CatenaryAssetUpdateRequest(null, null, false, null, null, null, null, null, null, null, null, null, null));

        assertEquals(List.of("asset.disabled", "asset.disabled"), fixture.events.names(),
                "Once per DELETE and once per PUT enabled=false; repeating a disable and re-enabling publish nothing");
        DomainEvent event = fixture.events.published.get(0);
        assertEquals(asset.getId().toString(), event.entityId());
        assertEquals("PRF-12-2.27", event.values().get("code"));
        assertEquals("PROFILE", event.values().get("type"));
        assertEquals(2L, event.values().get("trackId"));
        assertEquals(true, event.values().get("disabledLocally"));
    }

    @Test
    void inspectionsPublishTheirCreationTheirFailedItemsAndWhatTheyGenerate() {
        InspectionFixture fixture = new InspectionFixture();
        CatenaryAsset asset = profile("13-2.10", "13499.290");
        when(fixture.lookups.enabledAsset(asset.getId())).thenReturn(asset);
        when(fixture.codeGenerator.nextInspectionCode()).thenReturn("INS-000031");
        fixture.service.create(new MaintenanceInspectionRequest(asset.getId(), LocalDate.of(2026, 1, 28), "dana", null,
                InspectionResult.MAJOR_DEFECT, null, "Cracked insulator", "Replace it", null, null, null));

        MaintenanceInspection inspection = inspection(InspectionResult.MAJOR_DEFECT);
        MaintenanceInspectionItem height = MaintenanceInspectionItem.fromTemplate(inspection, InspectionTemplateItem.builder()
                .code("CW_HEIGHT").label("Contact wire height").unit("mm").minValue(new BigDecimal("5000")).maxValue(new BigDecimal("5500"))
                .requiresMeasure(true).build());
        ReflectionTestUtils.setField(height, "id", UUID.randomUUID());
        inspection.getItems().add(height);
        when(fixture.repository.findById(inspection.getId())).thenReturn(Optional.of(inspection));
        when(fixture.defectRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        fixture.service.updateItem(inspection.getId(), height.getId(),
                new CheckItemUpdateRequest(new BigDecimal("5620"), null, null, CheckItemResult.DEFECT, "Above tolerance", null));
        fixture.service.updateItem(inspection.getId(), height.getId(),
                new CheckItemUpdateRequest(null, null, null, null, "Still above tolerance", null));

        when(fixture.codeGenerator.nextDefectCode()).thenReturn("DEF-000045");
        fixture.service.createDefect(inspection.getId(), new CreateDefectFromInspectionRequest(null, null, null, null));

        MaintenanceOrder created = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.DRAFT);
        MaintenanceOrderResponse response = mock(MaintenanceOrderResponse.class);
        when(response.id()).thenReturn(created.getId());
        when(fixture.orderService.create(any())).thenReturn(response);
        when(fixture.orderRepository.findById(created.getId())).thenReturn(Optional.of(created));
        fixture.service.createCorrectiveOrder(inspection.getId(), new CreateCorrectiveOrderRequest(null, null, null, null, null));

        assertEquals(List.of("inspection.created", "inspection.item-failed", "inspection.defect-created", "inspection.corrective-order-created"),
                fixture.events.names(), "An item that stays DEFECT does not fail twice");
        DomainEvent createdEvent = fixture.events.published.get(0);
        assertEquals("INS-000031", createdEvent.values().get("code"));
        assertEquals("MAJOR_DEFECT", createdEvent.values().get("result"));
        assertEquals("PRF-13-2.10", createdEvent.values().get("assetCode"));
        assertEquals(0, createdEvent.values().get("itemCount"));
        DomainEvent itemFailed = fixture.events.published.get(1);
        assertEquals(inspection.getId().toString(), itemFailed.entityId());
        assertEquals("CW_HEIGHT", itemFailed.values().get("itemCode"));
        assertEquals(new BigDecimal("5620"), itemFailed.values().get("measuredValue"));
        assertEquals("DEFECT", itemFailed.values().get("itemResult"));
        DomainEvent defectCreated = fixture.events.published.get(2);
        assertEquals("DEF-000045", defectCreated.values().get("defectCode"));
        assertEquals("HIGH", defectCreated.values().get("severity"));
        DomainEvent correctiveCreated = fixture.events.published.get(3);
        assertEquals(created.getId(), correctiveCreated.values().get("orderId"));
        assertEquals("MO-DRAFT", correctiveCreated.values().get("orderCode"));
        assertEquals("DEF-000045", correctiveCreated.values().get("defectCode"));
    }

    @Test
    void theDailyPreventiveCheckPublishesOneEventPerDayWithADeterministicOperationId() {
        CatenaryAssetRepository repository = mock(CatenaryAssetRepository.class);
        RecordingEventPublisher events = new RecordingEventPublisher();
        PreventiveDueSoonServiceImpl service = new PreventiveDueSoonServiceImpl(repository, events, new PreventiveDueSoonProperties(7, 1));
        Instant now = Instant.parse("2026-09-29T06:07:00Z");
        CatenaryAsset overdue = profile("12-2.27", "12847.990");
        overdue.setPreventiveIntervalDays(365);
        overdue.setLastPreventiveCompletedAt(Instant.parse("2025-09-01T00:00:00Z"));
        CatenaryAsset soon = profile("12-2.28", "12899.290");
        soon.setPreventiveIntervalDays(365);
        soon.setLastPreventiveCompletedAt(Instant.parse("2025-10-02T00:00:00Z"));
        CatenaryAsset never = profile("12-2.29", "12950.000");
        never.setPreventiveIntervalDays(180);
        when(repository.tryAdvisoryTransactionLock(anyLong())).thenReturn(true);
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(soon, overdue, never));

        PreventiveDueSoonReport report = service.publishDueSoon(now);

        assertEquals(LocalDate.of(2026, 9, 29), report.date());
        assertEquals(3, report.dueCount());
        assertEquals(2, report.overdueCount());
        assertTrue(report.published());
        assertEquals(List.of("preventive.due-soon"), events.names());
        DomainEvent event = events.published.getFirst();
        assertEquals("2026-09-29", event.entityId());
        assertEquals(UUID.nameUUIDFromBytes("preventive-due-soon:2026-09-29".getBytes(StandardCharsets.UTF_8)), events.operationIds.getFirst(),
                "The same day publishes the same operationId from any instance: a duplicate for the consumer's inbox");
        assertEquals(3, event.values().get("count"));
        assertEquals(2, event.values().get("overdueCount"));
        assertEquals(7, event.values().get("horizonDays"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assets = (List<Map<String, Object>>) event.values().get("assets");
        assertEquals(1, assets.size(), "Only sample-size assets travel; the count is the whole");
        assertEquals("PRF-12-2.29", assets.getFirst().get("assetCode"), "Never checked comes first, then the earliest due");
        assertEquals(true, assets.getFirst().get("overdue"));
        assertNull(assets.getFirst().get("dueAt"));

        events.published.clear();
        when(repository.tryAdvisoryTransactionLock(anyLong())).thenReturn(false);
        assertFalse(service.publishDueSoon(now).published(), "Another instance holds the lock of the day");
        assertTrue(events.published.isEmpty());

        when(repository.tryAdvisoryTransactionLock(anyLong())).thenReturn(true);
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        assertFalse(service.publishDueSoon(now).published(), "Nothing due, nothing said");
        assertTrue(events.published.isEmpty());
    }

    /** Lo que los ganchos publican, tal cual, para leerlo en el test; los eventos con nombre entidad.evento. */
    static final class RecordingEventPublisher implements DomainEventPublisher {
        final List<DomainEvent> published = new ArrayList<>();
        final List<UUID> operationIds = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            publish(UUID.randomUUID(), event);
        }

        @Override
        public void publish(UUID operationId, DomainEvent event) {
            operationIds.add(operationId);
            published.add(event);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        List<String> names() {
            return published.stream().map(event -> event.entityName() + "." + event.eventName()).toList();
        }
    }

    /** Como hace JPA al guardar: la entidad sale con id si no lo tenia. */
    private static <T> T withId(T entity) {
        if (ReflectionTestUtils.getField(entity, "id") == null) {
            ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        }
        return entity;
    }

    // -------------------------------------------------------------- fixtures

    /**
     * El ambito de quien escribe en un fixture ({@link MaintenanceScope}): sin limite mientras nadie diga
     * otra cosa (el token no trae usuario, como un consumidor o una cuenta de servicio); {@link #actAs}
     * hace que escriba una persona con sus EP y sus equipos.
     */
    private static final class ScopeMocks {
        final CurrentUserService currentUser = mock(CurrentUserService.class);
        final CrewPersonRepository people = mock(CrewPersonRepository.class);
        final MaintenanceTeamRepository teams = mock(MaintenanceTeamRepository.class);

        MaintenanceScope scope() {
            return new MaintenanceScope(currentUser, people, teams, new ScopeProperties(true));
        }

        void actAs(CrewPerson person, List<Long> executionPackageIds, List<UUID> teamIds) {
            String username = "user-" + person.getCode().toLowerCase();
            ReflectionTestUtils.setField(person, "username", username);
            when(currentUser.getUsername()).thenReturn(Optional.of(username));
            when(people.findByUsername(username)).thenReturn(Optional.of(person));
            when(teams.findExecutionPackageIdsOfPerson(person.getId())).thenReturn(executionPackageIds);
            when(teams.findTeamIdsOfPerson(person.getId())).thenReturn(teamIds);
        }
    }

    private static final class ShiftFixture {
        final ScopeMocks scopeMocks = new ScopeMocks();
        final MaintenanceShiftRepository repository = mock(MaintenanceShiftRepository.class);
        final MaintenanceTaskRepository taskRepository = mock(MaintenanceTaskRepository.class);
        final CatenaryDefectRepository defectRepository = mock(CatenaryDefectRepository.class);
        final MaintenanceMaterialUsageRepository materialRepository = mock(MaintenanceMaterialUsageRepository.class);
        final MaintenanceInspectionRepository inspectionRepository = mock(MaintenanceInspectionRepository.class);
        final MaintenanceLookups lookups = mock(MaintenanceLookups.class);
        final MaintenanceCodeGenerator codeGenerator = mock(MaintenanceCodeGenerator.class);
        final CrewPersonRepository people = mock(CrewPersonRepository.class);
        final FleetVehicleRepository vehicles = mock(FleetVehicleRepository.class);
        final MaintenanceInstrumentRepository instruments = mock(MaintenanceInstrumentRepository.class);
        final RecordingEventPublisher events = new RecordingEventPublisher();
        final MaintenanceShiftServiceImpl service;

        ShiftFixture() {
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            CatenaryAssetMapper assetMapper = mock(CatenaryAssetMapper.class);
            when(assetMapper.toSummary(any())).thenAnswer(invocation -> {
                CatenaryAsset asset = invocation.getArgument(0);
                return new CatenaryAssetSummaryResponse(asset.getId(), asset.getCode(), asset.getName(), asset.getType(), asset.getTrackId(),
                        asset.getStartKp(), asset.getEndKp(), asset.getSectioning(), asset.getEnabled(), asset.getConnectedTrackId(),
                        asset.getExecutionPackageId(), asset.getStationId(), asset.getDriveType(), asset.getDisconnectorFunction());
            });
            service = new MaintenanceShiftServiceImpl(repository, taskRepository, defectRepository, materialRepository, inspectionRepository,
                    mock(MaintenanceShiftMapper.class), assetMapper, lookups, codeGenerator, mock(EntityAuditService.class), events,
                    new ShiftCrew(people, vehicles, repository, instruments, new ShiftProperties(Duration.ofHours(12))), scopeMocks.scope());
        }

        MaintenanceShift savedShift() {
            ArgumentCaptor<MaintenanceShift> captor = ArgumentCaptor.forClass(MaintenanceShift.class);
            verify(repository).save(captor.capture());
            return captor.getValue();
        }
    }

    private static final class OrderFixture {
        final ScopeMocks scopeMocks = new ScopeMocks();
        final MaintenanceOrderRepository repository = mock(MaintenanceOrderRepository.class);
        final CatenaryAssetRepository assetRepository = mock(CatenaryAssetRepository.class);
        final CatenaryDefectRepository defectRepository = mock(CatenaryDefectRepository.class);
        final MaintenanceInspectionRepository inspectionRepository = mock(MaintenanceInspectionRepository.class);
        final MaintenanceLookups lookups = mock(MaintenanceLookups.class);
        final MaintenanceCodeGenerator codeGenerator = mock(MaintenanceCodeGenerator.class);
        final StatusHistoryService history = mock(StatusHistoryService.class);
        final WorkloadEstimator workloadEstimator = mock(WorkloadEstimator.class);
        final MaterialStockSynchronizer stock = mock(MaterialStockSynchronizer.class);
        final MaintenanceOrderServiceImpl service;

        OrderFixture() {
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(assetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(workloadEstimator.estimate(any(MaintenanceOrder.class))).thenReturn(WorkloadEstimate.ofMinutes(BigDecimal.ZERO));
            service = new MaintenanceOrderServiceImpl(repository, assetRepository, defectRepository, inspectionRepository,
                    mock(MaintenanceOrderMapper.class), lookups, codeGenerator, history, workloadEstimator, stock, mock(EntityAuditService.class),
                    scopeMocks.scope());
        }

        MaintenanceOrder savedOrder() {
            ArgumentCaptor<MaintenanceOrder> captor = ArgumentCaptor.forClass(MaintenanceOrder.class);
            verify(repository).save(captor.capture());
            return captor.getValue();
        }
    }

    private static final class AssetFixture {
        final ScopeMocks scopeMocks = new ScopeMocks();
        final CatenaryAssetRepository repository = mock(CatenaryAssetRepository.class);
        final MaintenanceLookups lookups = mock(MaintenanceLookups.class);
        final RecordingEventPublisher events = new RecordingEventPublisher();
        final CatenaryAssetServiceImpl service;

        AssetFixture() {
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = new CatenaryAssetServiceImpl(repository, mock(CatenaryAssetMapper.class), lookups, mock(EntityAuditService.class), events,
                    scopeMocks.scope());
        }
    }

    private static final class DefectFixture {
        final ScopeMocks scopeMocks = new ScopeMocks();
        final CatenaryDefectRepository repository = mock(CatenaryDefectRepository.class);
        final MaintenanceLookups lookups = mock(MaintenanceLookups.class);
        final MaintenanceCodeGenerator codeGenerator = mock(MaintenanceCodeGenerator.class);
        final StatusHistoryService history = mock(StatusHistoryService.class);
        final CatenaryDefectServiceImpl service;

        DefectFixture() {
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = new CatenaryDefectServiceImpl(repository, mock(MaintenanceInspectionRepository.class), mock(CatenaryDefectMapper.class),
                    lookups, codeGenerator, history, mock(EntityAuditService.class), scopeMocks.scope());
        }

        CatenaryDefect savedDefect() {
            ArgumentCaptor<CatenaryDefect> captor = ArgumentCaptor.forClass(CatenaryDefect.class);
            verify(repository).save(captor.capture());
            return captor.getValue();
        }
    }

    private static final class TeamFixture {
        final MaintenanceTeamRepository repository = mock(MaintenanceTeamRepository.class);
        final MaintenanceLookups lookups = mock(MaintenanceLookups.class);
        final MaintenanceTeamServiceImpl service;

        TeamFixture() {
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = new MaintenanceTeamServiceImpl(repository, mock(MaintenanceTeamMapper.class), lookups);
        }
    }

    private static final class MaterialFixture {
        final ScopeMocks scopeMocks = new ScopeMocks();
        final MaintenanceMaterialUsageRepository repository = mock(MaintenanceMaterialUsageRepository.class);
        final MaintenanceLookups lookups = mock(MaintenanceLookups.class);
        final MaterialStockSynchronizer stock = mock(MaterialStockSynchronizer.class);
        final StockClient stockClient = mock(StockClient.class);
        final MaintenanceMaterialUsageServiceImpl service;

        MaterialFixture() {
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(stockClient.isEnabled()).thenReturn(false);
            service = new MaintenanceMaterialUsageServiceImpl(repository, mock(MaintenanceTaskRepository.class), mock(MaterialUsageMapper.class),
                    lookups, new MaterialLineFactory(repository, stockClient), stock, scopeMocks.scope());
        }

        MaintenanceMaterialUsage savedLine() {
            ArgumentCaptor<MaintenanceMaterialUsage> captor = ArgumentCaptor.forClass(MaintenanceMaterialUsage.class);
            verify(repository).saveAndFlush(captor.capture());
            return captor.getValue();
        }
    }

    private static final class TaskFixture {
        final ScopeMocks scopeMocks = new ScopeMocks();
        final MaintenanceTaskRepository repository = mock(MaintenanceTaskRepository.class);
        final CatenaryAssetRepository assetRepository = mock(CatenaryAssetRepository.class);
        final CatenaryDefectRepository defectRepository = mock(CatenaryDefectRepository.class);
        final MaintenanceTaskMapper mapper = mock(MaintenanceTaskMapper.class);
        final MaintenanceLookups lookups = mock(MaintenanceLookups.class);
        final MaintenanceCodeGenerator codeGenerator = mock(MaintenanceCodeGenerator.class);
        final StatusHistoryService history = mock(StatusHistoryService.class);
        final WorkloadEstimator workloadEstimator = mock(WorkloadEstimator.class);
        final MaterialLineFactory lineFactory = mock(MaterialLineFactory.class);
        final MaterialStockSynchronizer stock = mock(MaterialStockSynchronizer.class);
        final GenerationLocks generationLocks = spy(new GenerationLocks());
        final MaintenanceTaskServiceImpl service;

        TaskFixture() {
            this(TransactionOperations.withoutTransaction());
        }

        TaskFixture(TransactionOperations transactions) {
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = new MaintenanceTaskServiceImpl(repository, assetRepository, defectRepository, mapper, lookups,
                    codeGenerator, history, workloadEstimator, lineFactory, stock,
                    new PreventiveTaskSelector(assetRepository, repository, lookups), transactions, generationLocks, scopeMocks.scope());
        }

        MaintenanceTask savedTask() {
            ArgumentCaptor<MaintenanceTask> captor = ArgumentCaptor.forClass(MaintenanceTask.class);
            verify(repository).save(captor.capture());
            return captor.getValue();
        }
    }

    private static final class InspectionFixture {
        final ScopeMocks scopeMocks = new ScopeMocks();
        final MaintenanceInspectionRepository repository = mock(MaintenanceInspectionRepository.class);
        final CatenaryDefectRepository defectRepository = mock(CatenaryDefectRepository.class);
        final MaintenanceOrderRepository orderRepository = mock(MaintenanceOrderRepository.class);
        final MaintenanceLookups lookups = mock(MaintenanceLookups.class);
        final MaintenanceCodeGenerator codeGenerator = mock(MaintenanceCodeGenerator.class);
        final StatusHistoryService history = mock(StatusHistoryService.class);
        final MaintenanceOrderService orderService = mock(MaintenanceOrderService.class);
        final RecordingEventPublisher events = new RecordingEventPublisher();
        final MaintenanceInspectionServiceImpl service;

        InspectionFixture() {
            // Como JPA: lo guardado sale con id, que es lo que el evento de la inspeccion lleva como entityId.
            when(repository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
            service = new MaintenanceInspectionServiceImpl(repository, defectRepository, orderRepository, mock(MaintenanceInspectionMapper.class),
                    mock(CatenaryDefectMapper.class), lookups, codeGenerator, history, orderService, mock(EntityAuditService.class), events,
                    scopeMocks.scope());
        }
    }

    private static CatenaryAsset trackSection() {
        CatenaryAsset asset = CatenaryAsset.builder().code("SEC-T2").name("Ranana-Herzliya T2").type(CatenaryAssetType.TRACK_SECTION)
                .trackId(2L).executionPackageId(6L).startKp(new BigDecimal("12847.990")).endKp(new BigDecimal("14078.090"))
                .trackKind(TrackKind.MAIN).build();
        ReflectionTestUtils.setField(asset, "id", UUID.randomUUID());
        return asset;
    }

    private static CatenaryAsset profile(String name, String kp) {
        CatenaryAsset asset = CatenaryAsset.builder().code("PRF-" + name).name(name).type(CatenaryAssetType.PROFILE).trackId(2L)
                .executionPackageId(6L).startKp(new BigDecimal(kp)).endKp(new BigDecimal(kp)).build();
        ReflectionTestUtils.setField(asset, "id", UUID.randomUUID());
        return asset;
    }

    private static CatenaryAsset insulator(String name, Long trackId, Long connectedTrackId, String startKp, String endKp) {
        CatenaryAsset asset = CatenaryAsset.builder().code("SIN-" + name).name(name).type(CatenaryAssetType.SECTION_INSULATOR)
                .trackId(trackId).connectedTrackId(connectedTrackId).executionPackageId(6L)
                .startKp(new BigDecimal(startKp)).endKp(new BigDecimal(endKp)).build();
        ReflectionTestUtils.setField(asset, "id", UUID.randomUUID());
        return asset;
    }

    /** Un aislador con una aguja, como los que conectan dos vias. */
    private static CatenaryAsset withSwitch(CatenaryAsset insulator, String switchCode) {
        insulator.getSwitches().add(CatenaryAssetSwitch.builder().asset(insulator).code(switchCode)
                .kp(insulator.getStartKp()).turnoutDenominator(9).trackId(insulator.getTrackId()).build());
        return insulator;
    }

    private static Set<String> codesOf(MaintenanceTask task) {
        return task.getTaskTypes().stream().map(MaintenanceTaskType::getCode).collect(Collectors.toSet());
    }

    private static CatenaryAsset profileOn(String name, Long trackId, String kp) {
        CatenaryAsset asset = profile(name, kp);
        asset.setTrackId(trackId);
        return asset;
    }

    private static CatenaryAsset disconnectorAt(String name, Long trackId, String kp, DisconnectorDriveType driveType) {
        CatenaryAsset asset = disconnector(name);
        asset.setTrackId(trackId);
        asset.setStartKp(new BigDecimal(kp));
        asset.setEndKp(new BigDecimal(kp));
        asset.setDriveType(driveType);
        return asset;
    }

    private static CatenaryAsset sectionOn(Long trackId, Long packageId, TrackKind kind) {
        CatenaryAsset asset = trackSection();
        asset.setCode("SEC-T" + trackId);
        asset.setTrackId(trackId);
        asset.setExecutionPackageId(packageId);
        asset.setTrackKind(kind);
        return asset;
    }

    /** Una orden preventiva sobre cualquier activo: con selecciones solo aporta el EP y la clase de via. */
    private static MaintenanceOrder orderOn(CatenaryAsset asset) {
        MaintenanceOrder order = MaintenanceOrder.builder().code("MO-ON-" + asset.getCode()).title("Order").type(MaintenanceOrderType.PREVENTIVE)
                .status(MaintenanceOrderStatus.DRAFT).priority(MaintenancePriority.MEDIUM).asset(asset).build();
        order.locateAt(asset);
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        return order;
    }

    private static MaintenanceTaskRepository.AssetInOtherOrder inOtherOrder(UUID assetId, String orderCode) {
        return new MaintenanceTaskRepository.AssetInOtherOrder() {
            @Override
            public UUID getAssetId() {
                return assetId;
            }

            @Override
            public String getOrderCode() {
                return orderCode;
            }
        };
    }

    /** Una seleccion por partes, para no escribir los nueve componentes en cada caso. */
    private static SelectionBuilder selection(CatenaryAssetType type) {
        return new SelectionBuilder(type);
    }

    private static final class SelectionBuilder {
        private final CatenaryAssetType type;
        private UUID sectionId;
        private Long trackId;
        private BigDecimal fromKp;
        private BigDecimal toKp;
        private Long stationId;
        private List<UUID> assetIds;
        private List<String> taskTypeCodes;

        private SelectionBuilder(CatenaryAssetType type) {
            this.type = type;
        }

        SelectionBuilder sectionId(UUID value) {
            sectionId = value;
            return this;
        }

        SelectionBuilder trackId(Long value) {
            trackId = value;
            return this;
        }

        SelectionBuilder fromKp(BigDecimal value) {
            fromKp = value;
            return this;
        }

        SelectionBuilder toKp(BigDecimal value) {
            toKp = value;
            return this;
        }

        SelectionBuilder stationId(Long value) {
            stationId = value;
            return this;
        }

        SelectionBuilder assetIds(List<UUID> value) {
            assetIds = value;
            return this;
        }

        SelectionBuilder taskTypeCodes(List<String> value) {
            taskTypeCodes = value;
            return this;
        }

        GenerationSelection build() {
            return new GenerationSelection(type, sectionId, trackId, fromKp, toKp, stationId, assetIds, taskTypeCodes, null);
        }
    }

    private static CatenaryAsset disconnector(String name) {
        CatenaryAsset asset = CatenaryAsset.builder().code("DSC-" + name).name(name).type(CatenaryAssetType.DISCONNECTOR).trackId(2L)
                .executionPackageId(6L).build();
        ReflectionTestUtils.setField(asset, "id", UUID.randomUUID());
        return asset;
    }

    private static MaintenanceTeam team(String code) {
        MaintenanceTeam team = MaintenanceTeam.builder().code(code).name("Team " + code).baseName("Rishpon").vehicle("Vehicle " + code).build();
        ReflectionTestUtils.setField(team, "id", UUID.randomUUID());
        return team;
    }

    private static MaintenanceOrder order(MaintenanceOrderType type, MaintenanceOrderStatus status) {
        CatenaryAsset section = trackSection();
        MaintenanceOrder order = MaintenanceOrder.builder().code("MO-" + status).title("Order").type(type).status(status)
                .priority(type == MaintenanceOrderType.URGENT ? MaintenancePriority.CRITICAL : MaintenancePriority.MEDIUM).asset(section).build();
        order.locateAt(section);
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        return order;
    }

    private static MaintenanceTask task(MaintenanceOrder order, MaintenanceTaskStatus status) {
        MaintenanceTask task = MaintenanceTask.builder().order(order).sequence(order.getTasks().size() + 1).description("Task").status(status).build();
        ReflectionTestUtils.setField(task, "id", UUID.randomUUID());
        return task;
    }

    /** El executor de produccion: un hilo virtual por tarea. */
    private static java.util.concurrent.Executor virtualThreads() {
        return new com.alejandro.mtomaintenance.configuration.async.AsyncConfiguration().applicationTaskExecutor();
    }

    private static MaintenanceMaterialUsage line(MaintenanceOrder order) {
        MaintenanceMaterialUsage line = MaintenanceMaterialUsage.builder().order(order).materialId(UUID.randomUUID()).materialCode("GA70")
                .warehouseId(UUID.randomUUID()).plannedQuantity(new BigDecimal("2")).unit("ud").build();
        // Guardada, como cualquier linea que llega a hablar con stock: su id va en la clave de idempotencia.
        ReflectionTestUtils.setField(line, "id", UUID.randomUUID());
        order.getMaterials().add(line);
        return line;
    }

    private static MaintenanceMaterialUsage reservedLine(MaintenanceOrder order, String consumed) {
        MaintenanceMaterialUsage line = line(order);
        line.markReserved(UUID.randomUUID());
        line.setConsumedQuantity(new BigDecimal(consumed));
        return line;
    }

    /** Una linea FAILED con esa peticion en duda: la mando y stock no contesto. */
    private static MaintenanceMaterialUsage inDoubt(MaintenanceMaterialUsage line, StockRequestType request) {
        line.markInDoubt(request);
        line.markFailed(request + ": Read timed out");
        return line;
    }

    private static LineRef lineRef(UUID orderId, UUID lineId) {
        return new LineRef() {
            @Override
            public UUID getId() {
                return lineId;
            }

            @Override
            public UUID getOrderId() {
                return orderId;
            }
        };
    }

    private static MaterialUsageResponse usageResponse(StockSyncStatus status) {
        return new MaterialUsageResponse(UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), "GA70", null, UUID.randomUUID(),
                BigDecimal.ONE, BigDecimal.ZERO, "ud", false, null, status, null, null, 0L, null);
    }

    /** Lo que stock dice de la reserva de la linea: ese estado, con la cantidad prevista. */
    private static void stockSays(StockClient stockClient, MaintenanceMaterialUsage line, String status) {
        when(stockClient.findReservation(line.getStockReservationId())).thenReturn(Optional.of(new StockReservation(
                line.getStockReservationId(), line.getMaterialId(), line.getWarehouseId(), null, line.getPlannedQuantity(), status)));
    }

    private static CatenaryDefect defect(DefectStatus status) {
        CatenaryDefect defect = CatenaryDefect.builder().code("DEF-" + status).asset(profile("13-2.10", "13499.290")).severity(DefectSeverity.MEDIUM)
                .status(status).description("Cracked insulator").detectedAt(Instant.parse("2026-01-20T00:00:00Z")).build();
        if (status == DefectStatus.RESOLVED) {
            defect.setResolvedAt(Instant.parse("2026-01-27T23:00:00Z"));
        }
        ReflectionTestUtils.setField(defect, "id", UUID.randomUUID());
        return defect;
    }

    private static CrewPerson crewPerson(String category) {
        CrewPerson person = BeanUtils.instantiateClass(CrewPerson.class);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(person, "id", id);
        ReflectionTestUtils.setField(person, "code", "PER-" + id.toString().substring(0, 6));
        ReflectionTestUtils.setField(person, "fullName", "Person " + id.toString().substring(0, 4));
        ReflectionTestUtils.setField(person, "category", category);
        ReflectionTestUtils.setField(person, "active", true);
        return person;
    }

    private static FleetVehicle fleetVehicle(String mobility, boolean selfPropelled, boolean towing, Integer seats) {
        FleetVehicle vehicle = BeanUtils.instantiateClass(FleetVehicle.class);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(vehicle, "id", id);
        ReflectionTestUtils.setField(vehicle, "code", "V-" + id.toString().substring(0, 6));
        ReflectionTestUtils.setField(vehicle, "mobility", mobility);
        ReflectionTestUtils.setField(vehicle, "category", "TEST");
        ReflectionTestUtils.setField(vehicle, "selfPropelled", selfPropelled);
        ReflectionTestUtils.setField(vehicle, "portable", false);
        ReflectionTestUtils.setField(vehicle, "towing", towing);
        ReflectionTestUtils.setField(vehicle, "seats", seats);
        ReflectionTestUtils.setField(vehicle, "operationalStatus", "OPERATIONAL");
        ReflectionTestUtils.setField(vehicle, "available", true);
        ReflectionTestUtils.setField(vehicle, "active", true);
        return vehicle;
    }

    /** Le anade una cualificacion, como la deja el evento de mto-resources en la instantanea. */
    private static CrewPerson withQualification(CrewPerson person, String code, LocalDate expiresOn) {
        List<CrewPerson.Qualification> qualifications = new ArrayList<>(person.getQualifications());
        qualifications.add(new CrewPerson.Qualification(code, "VEHICLE_OPERATION", null, expiresOn));
        ReflectionTestUtils.setField(person, "qualifications", qualifications);
        return person;
    }

    private static FleetVehicle withDocument(FleetVehicle vehicle, String kind, LocalDate expiresOn) {
        List<FleetVehicle.Document> documents = new ArrayList<>(vehicle.getDocuments());
        documents.add(new FleetVehicle.Document(kind, kind + "-1", expiresOn));
        ReflectionTestUtils.setField(vehicle, "documents", documents);
        return vehicle;
    }

    private static FleetVehicle withDriverQualification(FleetVehicle vehicle, String code) {
        ReflectionTestUtils.setField(vehicle, "requiredDriverQualification", code);
        return vehicle;
    }

    private static MaintenanceInstrument instrument(String code, boolean active, LocalDate calibrationExpiresOn) {
        MaintenanceInstrument instrument = BeanUtils.instantiateClass(MaintenanceInstrument.class);
        ReflectionTestUtils.setField(instrument, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(instrument, "code", code);
        ReflectionTestUtils.setField(instrument, "name", "Instrument " + code);
        ReflectionTestUtils.setField(instrument, "kind", "EARTHING_POLE");
        ReflectionTestUtils.setField(instrument, "active", active);
        ReflectionTestUtils.setField(instrument, "calibrationExpiresOn", calibrationExpiresOn);
        return instrument;
    }

    /** Un turno de esa noche con su jefe y, si se dicen, sus instrumentos. */
    private static MaintenanceShiftRequest shiftWith(LocalDate night, UUID leaderId, Set<UUID> instrumentIds) {
        return new MaintenanceShiftRequest(night, null, null, null, PossessionType.FULL, null, null, null, null, null, null, Set.of(2L),
                null, null, null, null, null, null, leaderId, null, null, instrumentIds);
    }

    private static MaintenanceShiftUpdateRequest instruments(Set<UUID> instrumentIds) {
        return new MaintenanceShiftUpdateRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, instrumentIds, null);
    }

    private static MaintenanceTeam syncedTeam(CrewPerson leader, CrewPerson supervisor, FleetVehicle defaultVehicle, CrewPerson... members) {
        MaintenanceTeam team = team("A");
        team.setBaseName(null);
        ReflectionTestUtils.setField(team, "sourceService", "mto-resources");
        ReflectionTestUtils.setField(team, "sourceEntityId", UUID.randomUUID());
        ReflectionTestUtils.setField(team, "leader", leader);
        ReflectionTestUtils.setField(team, "supervisor", supervisor);
        ReflectionTestUtils.setField(team, "defaultVehicleId", defaultVehicle.getId());
        team.getMembers().addAll(List.of(members));
        return team;
    }

    /** Un turno planificado con su jefe (o sin el) y tantos linemen. */
    private static MaintenanceShift crewedShift(CrewPerson leader, int linemen) {
        MaintenanceShift shift = shift(2L, PossessionType.FULL, ShiftStatus.PLANNED);
        shift.setTeamLeader(leader);
        for (int i = 0; i < linemen; i++) {
            shift.getLinemen().add(crewPerson("WORKER"));
        }
        return shift;
    }

    private static MaintenanceShiftUpdateRequest observations(String text) {
        return new MaintenanceShiftUpdateRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, text, null);
    }

    private static MaintenanceShift shift(Long trackId, PossessionType possession, ShiftStatus status) {
        return shift(Set.of(trackId), possession, status);
    }

    private static MaintenanceShift shift(Set<Long> trackIds, PossessionType possession, ShiftStatus status) {
        MaintenanceShift shift = MaintenanceShift.builder().code("SH-" + trackIds + possession).shiftDate(LocalDate.now())
                .trackIds(new LinkedHashSet<>(trackIds)).possessionType(possession).status(status).build();
        ReflectionTestUtils.setField(shift, "id", UUID.randomUUID());
        return shift;
    }

    private static MaintenanceInspection inspection(InspectionResult result) {
        MaintenanceInspection inspection = MaintenanceInspection.builder().code("INS-" + result).asset(profile("13-2.10", "13499.290"))
                .inspectionDate(LocalDate.of(2026, 1, 28)).result(result).detectedDefects("Cracked insulator").build();
        ReflectionTestUtils.setField(inspection, "id", UUID.randomUUID());
        return inspection;
    }

    private static InspectionTemplate template(String... itemCodes) {
        InspectionTemplate template = InspectionTemplate.builder().assetType(CatenaryAssetType.PROFILE).name("Profile inspection").build();
        int index = 0;
        for (String code : itemCodes) {
            InspectionTemplateItem item = templateItem(code, ++index, index == 1);
            item.setTemplate(template);
            template.getItems().add(item);
        }
        return template;
    }

    private static InspectionTemplateItem templateItem(String code, int orderIndex, boolean measured) {
        InspectionTemplateItem.InspectionTemplateItemBuilder builder = InspectionTemplateItem.builder().code(code).label(code).orderIndex(orderIndex);
        if (measured) {
            builder.unit("mm").minValue(new BigDecimal("5000")).maxValue(new BigDecimal("5500")).requiresMeasure(true);
        }
        return builder.build();
    }

    private static Set<MaintenanceTaskType> taskTypes(String... codes) {
        Set<MaintenanceTaskType> types = new LinkedHashSet<>();
        int index = 0;
        for (String code : codes) {
            types.add(MaintenanceTaskType.builder().code(code).description(code).orderIndex(++index).build());
        }
        return types;
    }

    private static Set<MaintenanceTaskType> fullPossessionTypes() {
        Set<MaintenanceTaskType> types = new LinkedHashSet<>();
        types.add(MaintenanceTaskType.builder().code("RG-03").description("Section insulators").requiresFullPossession(true).orderIndex(3).build());
        return types;
    }

    /** Tipos que piden posesion total, como los de un aislador, sus agujas y un seccionador. */
    private static Set<MaintenanceTaskType> fullPossessionTypesOf(String... codes) {
        Set<MaintenanceTaskType> types = new LinkedHashSet<>();
        int index = 0;
        for (String code : codes) {
            types.add(MaintenanceTaskType.builder().code(code).description(code).requiresFullPossession(true).orderIndex(++index).build());
        }
        return types;
    }

    private static Set<MaintenanceTaskType> fullPossessionTypes(String code) {
        Set<MaintenanceTaskType> types = new LinkedHashSet<>();
        types.add(MaintenanceTaskType.builder().code(code).description(code).requiresFullPossession(true).orderIndex(26).build());
        return types;
    }

    private static Set<MaintenanceTaskType> diagnosticTypes() {
        Set<MaintenanceTaskType> types = new LinkedHashSet<>();
        types.add(MaintenanceTaskType.builder().code("RP-09").description("Static auscultation").diagnostic(true).orderIndex(29).build());
        return types;
    }

    private static CompleteTaskRequest complete(UUID shiftId, List<String> codes) {
        return new CompleteTaskRequest(shiftId, codes, "done", null, true, null, null, null, null);
    }

    private static CatenaryAssetRequest sectionRequest(String code, String startKp, String endKp) {
        return new CatenaryAssetRequest(code, "Ranana-Herzliya T3", null, 6L, 3L, null, new BigDecimal(startKp), new BigDecimal(endKp), TrackKind.MAIN, 365);
    }

    private static MaintenanceShiftRequest shiftRequest(UUID teamId, UUID disconnector, Instant plannedStart, Instant plannedEnd, String startKp, String endKp) {
        return shiftRequestWith(teamId, disconnector == null ? null : Set.of(disconnector), plannedStart, plannedEnd, startKp, endKp);
    }

    private static MaintenanceShiftRequest shiftRequestWith(UUID teamId, Set<UUID> disconnectors, Instant plannedStart, Instant plannedEnd, String startKp, String endKp) {
        return new MaintenanceShiftRequest(LocalDate.of(2026, 1, 27), teamId, null, null, PossessionType.PARTIAL, plannedStart, plannedEnd, disconnectors,
                "HSA-NS5 / HSA-NS6", "Rishpon", 6L, Set.of(2L), startKp == null ? null : new BigDecimal(startKp), endKp == null ? null : new BigDecimal(endKp),
                null, null, null);
    }

    private static MaintenanceShiftUpdateRequest shiftUpdate(Set<Long> trackIds, PossessionType possession) {
        return new MaintenanceShiftUpdateRequest(null, null, null, null, possession, null, null, null, null, null, null, trackIds, null, null,
                null, null, null, null);
    }
}
