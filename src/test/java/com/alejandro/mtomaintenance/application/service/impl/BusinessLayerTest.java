package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetRequest;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetUpdateRequest;
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
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.order.AssignOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.CancelOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.CompleteOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.order.PlanOrderRequest;
import com.alejandro.mtomaintenance.application.dto.report.MonthlyReportResponse;
import com.alejandro.mtomaintenance.application.dto.report.ProgressReportResponse;
import com.alejandro.mtomaintenance.application.dto.report.ProgressRowResponse;
import com.alejandro.mtomaintenance.application.dto.shift.CancelShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.CloseShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportRowResponse;
import com.alejandro.mtomaintenance.application.dto.shift.StartShiftRequest;
import com.alejandro.mtomaintenance.application.dto.stock.StockMaterial;
import com.alejandro.mtomaintenance.application.dto.stock.StockReservation;
import com.alejandro.mtomaintenance.application.dto.task.CancelTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.CompleteTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksResponse;
import com.alejandro.mtomaintenance.application.dto.task.InlineDefectRequest;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.task.StartTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.TaskMaterialRequest;
import com.alejandro.mtomaintenance.application.dto.tasktype.MaintenanceTaskTypeResponse;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamRequest;
import com.alejandro.mtomaintenance.application.exception.AssetDisabledException;
import com.alejandro.mtomaintenance.application.exception.DuplicateCodeException;
import com.alejandro.mtomaintenance.application.exception.InspectionException;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.MaterialUsageException;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.exception.ShiftException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
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
import com.alejandro.mtomaintenance.application.service.MaintenanceOrderService;
import com.alejandro.mtomaintenance.application.service.StatusHistoryService;
import com.alejandro.mtomaintenance.application.service.StockClient;
import com.alejandro.mtomaintenance.application.service.WorkloadEstimator;
import com.alejandro.mtomaintenance.domain.model.Quantity;
import com.alejandro.mtomaintenance.domain.model.WorkloadEstimate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CheckItemResult;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
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
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TaskUnit;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryDefectRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.InspectionTemplateRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceInspectionRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceMaterialUsageRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceOrderRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceReportRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceShiftRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceStatusHistoryRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskTypeRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTeamRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

        when(fixture.stock.resolveProjectId(order)).thenReturn(Optional.of(UUID.randomUUID()));
        fixture.service.plan(order.getId(), new PlanOrderRequest(LocalDate.now().plusDays(3), "week 4"));

        assertEquals(MaintenanceOrderStatus.PLANNED, order.getStatus());
        assertNotNull(order.getStockProjectId());
        verify(fixture.stock).reserve(line);
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
        verify(fixture.stock).consume(line);
        verify(fixture.history).recordOrderChange(order, "IN_PROGRESS", "COMPLETED", "all good");
    }

    @Test
    void completingWithAFailedMaterialLineNeedsForce() {
        OrderFixture fixture = new OrderFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        order.setActualStartDate(Instant.now());
        order.getTasks().add(task(order, MaintenanceTaskStatus.COMPLETED));
        MaintenanceMaterialUsage line = line(order);
        line.markFailed("reserve: stock down");
        when(fixture.lookups.order(order.getId())).thenReturn(order);

        assertThrows(MaterialUsageException.class, () -> fixture.service.complete(order.getId(), new CompleteOrderRequest(null, false, null)));
        assertEquals(MaintenanceOrderStatus.IN_PROGRESS, order.getStatus());

        fixture.service.complete(order.getId(), new CompleteOrderRequest(null, true, null));

        assertEquals(MaintenanceOrderStatus.COMPLETED, order.getStatus());
        assertTrue(order.getClosingNotes().contains("pending stock synchronization"));
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
        verify(fixture.stock).release(line);
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
                new MaterialUsageUpdateRequest(null, new BigDecimal("3"), null)));
        fixture.service.update(order.getId(), line.getId(), new MaterialUsageUpdateRequest(null, new BigDecimal("3"), true));
        assertEquals(0, new BigDecimal("3").compareTo(line.getConsumedQuantity()));
    }

    @Test
    void stockOutagesLeaveTheLineFailedAndSyncRetriesIt() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient);
        MaintenanceOrder order = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.PLANNED);
        order.setStockProjectId(UUID.randomUUID());
        MaintenanceMaterialUsage line = line(order);
        when(stockClient.reserve(any(), any(), any(), any())).thenThrow(new StockUnavailableException("stock down"));

        synchronizer.reserve(line);

        assertEquals(StockSyncStatus.FAILED, line.getStockSyncStatus());
        assertTrue(line.getStockSyncError().contains("stock down"));

        UUID reservationId = UUID.randomUUID();
        org.mockito.Mockito.reset(stockClient);
        when(stockClient.isEnabled()).thenReturn(true);
        when(stockClient.reserve(any(), any(), any(), any())).thenReturn(new StockReservation(reservationId, null, null, null, BigDecimal.ONE, "ACTIVE"));

        synchronizer.syncNow(line);

        assertEquals(StockSyncStatus.RESERVED, line.getStockSyncStatus());
        assertEquals(reservationId, line.getStockReservationId());
    }

    @Test
    void withoutAStockProjectTheConsumptionIsADirectOutput() {
        StockClient stockClient = mock(StockClient.class);
        when(stockClient.isEnabled()).thenReturn(true);
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient);
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        MaintenanceMaterialUsage line = line(order);
        line.setConsumedQuantity(new BigDecimal("2"));

        synchronizer.consume(line);

        assertEquals(StockSyncStatus.CONSUMED, line.getStockSyncStatus());
        verify(stockClient).output(eq(line.getMaterialId()), eq(line.getWarehouseId()), isNull(), eq(new BigDecimal("2")), eq(order.getCode()), anyString());
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

        GeneratePreventiveTasksResponse response = fixture.service.generatePreventiveTasks(order.getId(), new GeneratePreventiveTasksRequest(null, null));

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
    void generatingTasksNeedsAPreventiveOrderOnATrackSection() {
        TaskFixture fixture = new TaskFixture();
        MaintenanceOrder corrective = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.DRAFT);
        when(fixture.lookups.order(corrective.getId())).thenReturn(corrective);
        assertThrows(InvalidTransitionException.class, () -> fixture.service.generatePreventiveTasks(corrective.getId(), new GeneratePreventiveTasksRequest(null, null)));

        MaintenanceOrder inProgress = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        when(fixture.lookups.order(inProgress.getId())).thenReturn(inProgress);
        assertThrows(InvalidTransitionException.class, () -> fixture.service.generatePreventiveTasks(inProgress.getId(), new GeneratePreventiveTasksRequest(null, null)));
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
                new MaintenanceOrderUpdateRequest("New title", null, null, null, null, null, null, null, null, null, null, null, null)));
        fixture.service.update(assigned.getId(), new MaintenanceOrderUpdateRequest(null, "Re-tension the wire", MaintenancePriority.HIGH,
                null, null, null, "half done", null, null, null, null, null, null));

        assertEquals("Re-tension the wire", assigned.getDescription());
        assertEquals(MaintenancePriority.HIGH, assigned.getPriority());
        assertEquals("half done", assigned.getClosingNotes());
        assertEquals("Order", assigned.getTitle());

        // En DRAFT se edita todo, pero el rango kp resultante sigue teniendo que ser valido.
        MaintenanceOrder draft = order(MaintenanceOrderType.PREVENTIVE, MaintenanceOrderStatus.DRAFT);
        when(fixture.lookups.order(draft.getId())).thenReturn(draft);
        fixture.service.update(draft.getId(), new MaintenanceOrderUpdateRequest(" Preventive T2 ", null, null, LocalDate.of(2026, 2, 1),
                null, "yossi", null, 7L, null, null, null, null, null));
        assertEquals("Preventive T2", draft.getTitle());
        assertEquals(LocalDate.of(2026, 2, 1), draft.getPlannedDate());
        assertEquals(7L, draft.getExecutionPackageId());
        assertThrows(ValidationException.class, () -> fixture.service.update(draft.getId(),
                new MaintenanceOrderUpdateRequest(null, null, null, null, null, null, null, null, null, null, new BigDecimal("15000.000"), null, null)));

        // URGENT no baja nunca de CRITICAL aunque la peticion lo pida.
        MaintenanceOrder urgent = order(MaintenanceOrderType.URGENT, MaintenanceOrderStatus.IN_PROGRESS);
        when(fixture.lookups.order(urgent.getId())).thenReturn(urgent);
        fixture.service.update(urgent.getId(), new MaintenanceOrderUpdateRequest(null, null, MaintenancePriority.LOW, null, null, null, null, null, null, null, null, null, null));
        assertEquals(MaintenancePriority.CRITICAL, urgent.getPriority());

        MaintenanceOrder completed = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.COMPLETED);
        when(fixture.lookups.order(completed.getId())).thenReturn(completed);
        assertThrows(InvalidTransitionException.class, () -> fixture.service.update(completed.getId(),
                new MaintenanceOrderUpdateRequest(null, "late", null, null, null, null, null, null, null, null, null, null, null)));
    }

    @Test
    void startingAnUrgentDraftResolvesTheStockProjectAndReservesItsMaterials() {
        OrderFixture fixture = new OrderFixture();
        MaintenanceOrder order = order(MaintenanceOrderType.URGENT, MaintenanceOrderStatus.DRAFT);
        MaintenanceMaterialUsage line = line(order);
        UUID project = UUID.randomUUID();
        when(fixture.lookups.order(order.getId())).thenReturn(order);
        when(fixture.stock.resolveProjectId(order)).thenReturn(Optional.of(project));

        fixture.service.start(order.getId(), null);

        assertEquals(project, order.getStockProjectId());
        verify(fixture.stock).reserve(line);
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
    void assetsFromMasterDataOnlyAcceptDescriptionEnabledAndPreventiveInterval() {
        AssetFixture fixture = new AssetFixture();
        CatenaryAsset synced = profile("12-2.27", "12847.990");
        synced.setSourceService("mto-configuration");
        synced.setSourceEntityId("prf-1");
        when(fixture.lookups.asset(synced.getId())).thenReturn(synced);

        assertThrows(AssetDisabledException.class, () -> fixture.service.update(synced.getId(),
                new CatenaryAssetUpdateRequest("renamed", null, null, null, null, null, null, null, null, null)));
        fixture.service.update(synced.getId(), new CatenaryAssetUpdateRequest(null, "Next to the bridge", false, 180, null, null, null, null, null, null));

        assertEquals("12-2.27", synced.getName());
        assertEquals("Next to the bridge", synced.getDescription());
        assertFalse(synced.isEnabled());
        assertEquals(180, synced.getPreventiveIntervalDays());

        // Un activo local admite cambiar la localizacion, siempre con un rango kp coherente y trackKind solo en tramos.
        CatenaryAsset local = profile("13-2.01", "13007.290");
        when(fixture.lookups.asset(local.getId())).thenReturn(local);
        assertThrows(ValidationException.class, () -> fixture.service.update(local.getId(),
                new CatenaryAssetUpdateRequest(null, null, null, null, null, null, null, null, null, TrackKind.MAIN)));
        assertThrows(ValidationException.class, () -> fixture.service.update(local.getId(),
                new CatenaryAssetUpdateRequest(null, null, null, null, null, null, null, null, new BigDecimal("13000.000"), null)));

        CatenaryAsset section = trackSection();
        when(fixture.lookups.asset(section.getId())).thenReturn(section);
        fixture.service.update(section.getId(), new CatenaryAssetUpdateRequest(" T2 renamed ", null, null, null, 7L, 3L, 9L, null, null, TrackKind.DIVERTED));
        assertEquals("T2 renamed", section.getName());
        assertEquals(7L, section.getExecutionPackageId());
        assertEquals(3L, section.getTrackId());
        assertEquals(9L, section.getStationId());
        assertEquals(TrackKind.DIVERTED, section.getTrackKind());
    }

    @Test
    void disablingAnAssetIsIdempotent() {
        AssetFixture fixture = new AssetFixture();
        CatenaryAsset asset = profile("12-2.27", "12847.990");
        when(fixture.lookups.asset(asset.getId())).thenReturn(asset);

        fixture.service.disable(asset.getId());
        fixture.service.disable(asset.getId());

        assertFalse(asset.isEnabled());
        verify(fixture.repository, times(1)).save(asset);
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
                new CatenaryDefectUpdateRequest(DefectSeverity.LOW, null, null, null, null, null, null)));

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
                LocalDate.of(2026, 2, 3), List.of("kink.jpg")));
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
        assertEquals(disconnector, saved.getBlockADisconnector());
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
    void anInspectionCannotBeOkWithADefectiveItemAndAMeasureOutOfRangeIsOnlyOkOnceAdjusted() {
        InspectionFixture fixture = new InspectionFixture();
        MaintenanceInspection inspection = inspection(InspectionResult.MINOR_DEFECT);
        MaintenanceInspectionItem height = MaintenanceInspectionItem.fromTemplate(inspection, templateItem("CW_HEIGHT", 1, true));
        ReflectionTestUtils.setField(height, "id", UUID.randomUUID());
        inspection.getItems().add(height);
        when(fixture.repository.findById(inspection.getId())).thenReturn(Optional.of(inspection));

        assertThrows(InspectionException.class, () -> fixture.service.updateItem(inspection.getId(), height.getId(),
                new CheckItemUpdateRequest(new BigDecimal("5620"), null, null, CheckItemResult.OK, null)));
        fixture.service.updateItem(inspection.getId(), height.getId(),
                new CheckItemUpdateRequest(null, null, new BigDecimal("5300"), CheckItemResult.OK, "brought back to 5300"));

        assertTrue(height.getAdjusted(), "A value after adjustment marks the item as adjusted");
        assertEquals(CheckItemResult.OK, height.getItemResult());
        assertEquals(new BigDecimal("5300"), height.effectiveValue());
        assertFalse(height.isOutOfRange());

        fixture.service.updateItem(inspection.getId(), height.getId(), new CheckItemUpdateRequest(null, null, null, CheckItemResult.DEFECT, null));
        assertThrows(InspectionException.class, () -> fixture.service.update(inspection.getId(),
                new MaintenanceInspectionUpdateRequest(null, null, null, InspectionResult.OK, null, null, null, null)));
        assertEquals(InspectionResult.OK, inspection.getResult(), "The result is applied before the check so the caller sees why it failed");
        assertThrows(NotFoundException.class, () -> fixture.service.updateItem(inspection.getId(), UUID.randomUUID(),
                new CheckItemUpdateRequest(null, null, null, CheckItemResult.OK, null)));

        fixture.service.update(inspection.getId(), new MaintenanceInspectionUpdateRequest(LocalDate.of(2026, 1, 29), "dana", InspectionKind.TECHNICAL,
                InspectionResult.MAJOR_DEFECT, "measured", "Height out of range", "Adjust", new BigDecimal("13500.000")));
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
        UUID project = UUID.randomUUID();
        MaintenanceMaterialUsage line = MaintenanceMaterialUsage.builder().order(order).task(task).materialId(material).materialCode("GA70")
                .warehouseId(warehouse).plannedQuantity(new BigDecimal("3")).unit("ud").allowOverConsumption(true).build();
        when(fixture.lineFactory.buildLine(order, task, material, "GA70", warehouse, new BigDecimal("3"), "ud", true)).thenReturn(line);
        when(fixture.lineFactory.saveLine(line)).thenReturn(line);
        when(fixture.stock.resolveProjectId(order)).thenReturn(Optional.of(project));

        fixture.service.complete(order.getId(), task.getId(), new CompleteTaskRequest(shift.getId(), null, "droppers replaced", null, true, null, null,
                List.of(new TaskMaterialRequest(material, "GA70", warehouse, new BigDecimal("3"), "ud")), List.of("12-2.27.jpg")));

        assertEquals(MaintenanceTaskStatus.COMPLETED, task.getStatus());
        assertNotNull(task.getStartedAt(), "A task completed in one go gets its start stamped too");
        assertEquals(List.of("12-2.27.jpg"), task.getPhotoRefs());
        assertEquals(0, new BigDecimal("3").compareTo(line.getConsumedQuantity()), "What was used on the profile is already consumed");
        assertEquals(project, order.getStockProjectId());
        verify(fixture.stock).reserve(line);
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

        fixture.service.updateCheckItem(order.getId(), task.getId(), item.getId(), new CheckItemUpdateRequest(new BigDecimal("5200"), null, null, CheckItemResult.OK, null));
        fixture.service.update(order.getId(), task.getId(), new MaintenanceTaskUpdateRequest(" Pole 12-2.27 ", "dana", List.of("RG-01"), null, "bird nest", List.of("nest.jpg")));

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
                new CheckItemUpdateRequest(null, null, null, CheckItemResult.DEFECT, null)));
        assertThrows(InvalidTransitionException.class, () -> fixture.service.update(order.getId(), task.getId(),
                new MaintenanceTaskUpdateRequest("x", null, null, null, null, null)));
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
        disabled.setEnabled(false);
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
        StatusHistoryServiceImpl service = new StatusHistoryServiceImpl(repository, mapper);
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
                    type.getUnit(), type.getFixedMinutes(), type.getRequiresFullPossession(), type.getDiagnostic(), type.getActive(), type.getOrderIndex());
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
        MaterialStockSynchronizer synchronizer = new MaterialStockSynchronizer(stockClient);
        MaintenanceOrder order = order(MaintenanceOrderType.CORRECTIVE, MaintenanceOrderStatus.IN_PROGRESS);
        UUID project = UUID.randomUUID();
        order.setStockProjectId(project);

        MaintenanceMaterialUsage unused = reservedLine(order, "0");
        MaintenanceMaterialUsage exact = reservedLine(order, "2");
        MaintenanceMaterialUsage partial = reservedLine(order, "0.5");
        MaintenanceMaterialUsage over = reservedLine(order, "3.5");

        synchronizer.consume(unused);
        synchronizer.consume(exact);
        synchronizer.consume(partial);
        synchronizer.consume(over);

        verify(stockClient).release(unused.getStockReservationId());
        assertEquals(StockSyncStatus.RELEASED, unused.getStockSyncStatus());
        verify(stockClient).consume(exact.getStockReservationId());
        verify(stockClient).release(partial.getStockReservationId());
        verify(stockClient).output(eq(partial.getMaterialId()), eq(partial.getWarehouseId()), eq(project), eq(new BigDecimal("0.5")), eq(order.getCode()), anyString());
        verify(stockClient).consume(over.getStockReservationId());
        verify(stockClient).output(eq(over.getMaterialId()), eq(over.getWarehouseId()), eq(project), eq(new BigDecimal("1.5")), eq(order.getCode()), anyString());
        assertEquals(StockSyncStatus.CONSUMED, exact.getStockSyncStatus());
        assertEquals(StockSyncStatus.CONSUMED, partial.getStockSyncStatus());
        assertEquals(StockSyncStatus.CONSUMED, over.getStockSyncStatus());

        MaintenanceMaterialUsage reserved = reservedLine(order, "0");
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

        assertThrows(MaterialUsageException.class, () -> fixture.service.update(planned.getId(), line.getId(), new MaterialUsageUpdateRequest(new BigDecimal("5"), null, null)));
        fixture.service.update(planned.getId(), line.getId(), new MaterialUsageUpdateRequest(null, null, true));
        assertTrue(line.getAllowOverConsumption());

        fixture.service.sync(planned.getId(), line.getId());
        verify(fixture.stock).syncNow(line);

        assertThrows(NotFoundException.class, () -> fixture.service.update(planned.getId(), UUID.randomUUID(), new MaterialUsageUpdateRequest(null, null, true)));
        assertThrows(NotFoundException.class, () -> fixture.service.register(planned.getId(),
                new MaterialUsageRequest(UUID.randomUUID(), "GA70", UUID.randomUUID(), BigDecimal.ONE, "ud", UUID.randomUUID(), null)),
                "A task id that is not one of the order's tasks");
    }

    // ---------------------------------------------------------------- reports

    @Test
    void theProgressReportGroupsAssetsByPackageTrackAndTypeAndCountsTheSpansCovered() {
        MaintenanceReportRepository repository = mock(MaintenanceReportRepository.class);
        MaintenanceReportServiceImpl service = new MaintenanceReportServiceImpl(repository);
        CatenaryAsset first = profile("12-2.27", "12847.990");
        CatenaryAsset second = profile("12-2.28", "12899.290");
        CatenaryAsset third = profile("13-2.01", "13007.290");
        third.setLastPreventiveCompletedAt(Instant.parse("2026-01-15T00:00:00Z"));
        CatenaryAsset disconnector = disconnector("HSA-NS5");
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        when(repository.findReportableAssets(6L, null, null)).thenReturn(List.of(first, second, third, disconnector));
        when(repository.findAssetIdsWorkedBetween(from, to)).thenReturn(Set.of(first.getId()));

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
        when(repository.findAssetIdsWorkedBetween(laterFrom, null)).thenReturn(Set.of());
        assertEquals(0, service.progress(6L, null, null, laterFrom, null).checkedAssets());
    }

    @Test
    void theMonthlyReportAggregatesShiftsTasksProfilesAndMaterialsByMaterial() {
        MaintenanceReportRepository repository = mock(MaintenanceReportRepository.class);
        MaintenanceReportServiceImpl service = new MaintenanceReportServiceImpl(repository);
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

    // -------------------------------------------------------------- fixtures

    private static final class ShiftFixture {
        final MaintenanceShiftRepository repository = mock(MaintenanceShiftRepository.class);
        final MaintenanceTaskRepository taskRepository = mock(MaintenanceTaskRepository.class);
        final CatenaryDefectRepository defectRepository = mock(CatenaryDefectRepository.class);
        final MaintenanceMaterialUsageRepository materialRepository = mock(MaintenanceMaterialUsageRepository.class);
        final MaintenanceLookups lookups = mock(MaintenanceLookups.class);
        final MaintenanceCodeGenerator codeGenerator = mock(MaintenanceCodeGenerator.class);
        final MaintenanceShiftServiceImpl service;

        ShiftFixture() {
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            CatenaryAssetMapper assetMapper = mock(CatenaryAssetMapper.class);
            when(assetMapper.toSummary(any())).thenAnswer(invocation -> {
                CatenaryAsset asset = invocation.getArgument(0);
                return new CatenaryAssetSummaryResponse(asset.getId(), asset.getCode(), asset.getName(), asset.getType(), asset.getTrackId(),
                        asset.getStartKp(), asset.getEndKp(), asset.getSectioning(), asset.getEnabled());
            });
            service = new MaintenanceShiftServiceImpl(repository, taskRepository, defectRepository, materialRepository,
                    mock(MaintenanceShiftMapper.class), assetMapper, lookups, codeGenerator, mock(EntityAuditService.class));
        }

        MaintenanceShift savedShift() {
            ArgumentCaptor<MaintenanceShift> captor = ArgumentCaptor.forClass(MaintenanceShift.class);
            verify(repository).save(captor.capture());
            return captor.getValue();
        }
    }

    private static final class OrderFixture {
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
            when(stock.resolveProjectId(any())).thenReturn(Optional.empty());
            service = new MaintenanceOrderServiceImpl(repository, assetRepository, defectRepository, inspectionRepository,
                    mock(MaintenanceOrderMapper.class), lookups, codeGenerator, history, workloadEstimator, stock, mock(EntityAuditService.class));
        }

        MaintenanceOrder savedOrder() {
            ArgumentCaptor<MaintenanceOrder> captor = ArgumentCaptor.forClass(MaintenanceOrder.class);
            verify(repository).save(captor.capture());
            return captor.getValue();
        }
    }

    private static final class AssetFixture {
        final CatenaryAssetRepository repository = mock(CatenaryAssetRepository.class);
        final MaintenanceLookups lookups = mock(MaintenanceLookups.class);
        final CatenaryAssetServiceImpl service;

        AssetFixture() {
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = new CatenaryAssetServiceImpl(repository, mock(CatenaryAssetMapper.class), lookups, mock(EntityAuditService.class));
        }
    }

    private static final class DefectFixture {
        final CatenaryDefectRepository repository = mock(CatenaryDefectRepository.class);
        final MaintenanceLookups lookups = mock(MaintenanceLookups.class);
        final MaintenanceCodeGenerator codeGenerator = mock(MaintenanceCodeGenerator.class);
        final StatusHistoryService history = mock(StatusHistoryService.class);
        final CatenaryDefectServiceImpl service;

        DefectFixture() {
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = new CatenaryDefectServiceImpl(repository, mock(MaintenanceInspectionRepository.class), mock(CatenaryDefectMapper.class),
                    lookups, codeGenerator, history, mock(EntityAuditService.class));
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
                    lookups, new MaterialLineFactory(repository, stockClient), stock);
        }

        MaintenanceMaterialUsage savedLine() {
            ArgumentCaptor<MaintenanceMaterialUsage> captor = ArgumentCaptor.forClass(MaintenanceMaterialUsage.class);
            verify(repository).saveAndFlush(captor.capture());
            return captor.getValue();
        }
    }

    private static final class TaskFixture {
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
        final MaintenanceTaskServiceImpl service;

        TaskFixture() {
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = new MaintenanceTaskServiceImpl(repository, assetRepository, defectRepository, mapper, lookups,
                    codeGenerator, history, workloadEstimator, lineFactory, stock);
        }

        MaintenanceTask savedTask() {
            ArgumentCaptor<MaintenanceTask> captor = ArgumentCaptor.forClass(MaintenanceTask.class);
            verify(repository).save(captor.capture());
            return captor.getValue();
        }
    }

    private static final class InspectionFixture {
        final MaintenanceInspectionRepository repository = mock(MaintenanceInspectionRepository.class);
        final CatenaryDefectRepository defectRepository = mock(CatenaryDefectRepository.class);
        final MaintenanceOrderRepository orderRepository = mock(MaintenanceOrderRepository.class);
        final MaintenanceLookups lookups = mock(MaintenanceLookups.class);
        final MaintenanceCodeGenerator codeGenerator = mock(MaintenanceCodeGenerator.class);
        final StatusHistoryService history = mock(StatusHistoryService.class);
        final MaintenanceOrderService orderService = mock(MaintenanceOrderService.class);
        final MaintenanceInspectionServiceImpl service;

        InspectionFixture() {
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = new MaintenanceInspectionServiceImpl(repository, defectRepository, orderRepository, mock(MaintenanceInspectionMapper.class),
                    mock(CatenaryDefectMapper.class), lookups, codeGenerator, history, orderService, mock(EntityAuditService.class));
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

    private static MaintenanceMaterialUsage line(MaintenanceOrder order) {
        MaintenanceMaterialUsage line = MaintenanceMaterialUsage.builder().order(order).materialId(UUID.randomUUID()).materialCode("GA70")
                .warehouseId(UUID.randomUUID()).plannedQuantity(new BigDecimal("2")).unit("ud").build();
        order.getMaterials().add(line);
        return line;
    }

    private static MaintenanceMaterialUsage reservedLine(MaintenanceOrder order, String consumed) {
        MaintenanceMaterialUsage line = line(order);
        line.markReserved(UUID.randomUUID());
        line.setConsumedQuantity(new BigDecimal(consumed));
        return line;
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

    private static MaintenanceShiftRequest shiftRequest(UUID teamId, UUID blockA, Instant plannedStart, Instant plannedEnd, String startKp, String endKp) {
        return new MaintenanceShiftRequest(LocalDate.of(2026, 1, 27), teamId, null, null, PossessionType.PARTIAL, plannedStart, plannedEnd, blockA, null,
                "HSA-NS5 / HSA-NS6", "Rishpon", 6L, Set.of(2L), startKp == null ? null : new BigDecimal(startKp), endKp == null ? null : new BigDecimal(endKp),
                null, null, null);
    }

    private static MaintenanceShiftUpdateRequest shiftUpdate(Set<Long> trackIds, PossessionType possession) {
        return new MaintenanceShiftUpdateRequest(null, null, null, null, possession, null, null, null, null, null, null, null, trackIds, null, null,
                null, null, null);
    }
}
