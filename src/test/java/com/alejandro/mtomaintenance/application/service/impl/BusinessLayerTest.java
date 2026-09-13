package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.inspection.CreateCorrectiveOrderRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.CreateDefectFromInspectionRequest;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageRequest;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.order.AssignOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.CancelOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.CompleteOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderResponse;
import com.alejandro.mtomaintenance.application.dto.order.PlanOrderRequest;
import com.alejandro.mtomaintenance.application.dto.stock.StockReservation;
import com.alejandro.mtomaintenance.application.dto.task.CompleteTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksResponse;
import com.alejandro.mtomaintenance.application.dto.task.InlineDefectRequest;
import com.alejandro.mtomaintenance.application.exception.InspectionException;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.MaterialUsageException;
import com.alejandro.mtomaintenance.application.exception.ShiftException;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceShiftRepository;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceShiftMapper;
import com.alejandro.mtomaintenance.application.mapper.CatenaryAssetMapper;
import com.alejandro.mtomaintenance.application.dto.shift.CloseShiftRequest;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.application.mapper.CatenaryDefectMapper;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceInspectionMapper;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceOrderMapper;
import com.alejandro.mtomaintenance.application.mapper.MaintenanceTaskMapper;
import com.alejandro.mtomaintenance.application.mapper.MaterialUsageMapper;
import com.alejandro.mtomaintenance.application.service.EntityAuditService;
import com.alejandro.mtomaintenance.application.service.MaintenanceCodeGenerator;
import com.alejandro.mtomaintenance.application.service.MaintenanceOrderService;
import com.alejandro.mtomaintenance.application.service.StatusHistoryService;
import com.alejandro.mtomaintenance.application.service.StockClient;
import com.alejandro.mtomaintenance.application.service.WorkloadEstimator;
import com.alejandro.mtomaintenance.domain.model.WorkloadEstimate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspection;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TaskUnit;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryDefectRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceInspectionRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceMaterialUsageRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceOrderRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    // -------------------------------------------------------------- fixtures

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

    private static final class ShiftFixture {
        final MaintenanceShiftRepository repository = mock(MaintenanceShiftRepository.class);
        final MaintenanceTaskRepository taskRepository = mock(MaintenanceTaskRepository.class);
        final CatenaryDefectRepository defectRepository = mock(CatenaryDefectRepository.class);
        final MaintenanceLookups lookups = mock(MaintenanceLookups.class);
        final MaintenanceShiftServiceImpl service;

        ShiftFixture() {
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            CatenaryAssetMapper assetMapper = mock(CatenaryAssetMapper.class);
            when(assetMapper.toSummary(any())).thenAnswer(invocation -> {
                CatenaryAsset asset = invocation.getArgument(0);
                return new CatenaryAssetSummaryResponse(asset.getId(), asset.getCode(), asset.getName(), asset.getType(), asset.getTrackId(),
                        asset.getStartKp(), asset.getEndKp(), asset.getSectioning(), asset.getEnabled());
            });
            service = new MaintenanceShiftServiceImpl(repository, taskRepository, defectRepository, mock(MaintenanceMaterialUsageRepository.class),
                    mock(MaintenanceShiftMapper.class), assetMapper, lookups, mock(MaintenanceCodeGenerator.class), mock(EntityAuditService.class));
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
        final MaintenanceLookups lookups = mock(MaintenanceLookups.class);
        final MaintenanceCodeGenerator codeGenerator = mock(MaintenanceCodeGenerator.class);
        final StatusHistoryService history = mock(StatusHistoryService.class);
        final WorkloadEstimator workloadEstimator = mock(WorkloadEstimator.class);
        final MaintenanceTaskServiceImpl service;

        TaskFixture() {
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = new MaintenanceTaskServiceImpl(repository, assetRepository, defectRepository, mock(MaintenanceTaskMapper.class), lookups,
                    codeGenerator, history, workloadEstimator, mock(MaterialLineFactory.class), mock(MaterialStockSynchronizer.class));
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
        final MaintenanceCodeGenerator codeGenerator = mock(MaintenanceCodeGenerator.class);
        final StatusHistoryService history = mock(StatusHistoryService.class);
        final MaintenanceOrderService orderService = mock(MaintenanceOrderService.class);
        final MaintenanceInspectionServiceImpl service;

        InspectionFixture() {
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = new MaintenanceInspectionServiceImpl(repository, defectRepository, orderRepository, mock(MaintenanceInspectionMapper.class),
                    mock(CatenaryDefectMapper.class), mock(MaintenanceLookups.class), codeGenerator, history, orderService, mock(EntityAuditService.class));
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

    private static CompleteTaskRequest complete(UUID shiftId, List<String> codes) {
        return new CompleteTaskRequest(shiftId, codes, "done", null, true, null, null, null, null);
    }
}
