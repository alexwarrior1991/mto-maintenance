package com.alejandro.mtomaintenance.domain.model;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainModelTest {

    @Test
    void kilometricRangeRejectsAnInvertedRangeAndMeasuresInKilometres() {
        KilometricRange range = new KilometricRange(new BigDecimal("12847.990"), new BigDecimal("14078.090"));

        assertEquals(0, new BigDecimal("1.230100").compareTo(range.lengthKm()));
        assertTrue(range.contains(new BigDecimal("13007.290")));
        assertFalse(range.contains(new BigDecimal("14100.000")));
        assertThrows(IllegalArgumentException.class, () -> new KilometricRange(BigDecimal.TEN, BigDecimal.ONE));
    }

    @Test
    void quantityRejectsNegativeAmountsAndBlankUnits() {
        assertThrows(IllegalArgumentException.class, () -> new Quantity(new BigDecimal("-1"), "ud"));
        assertThrows(IllegalArgumentException.class, () -> new Quantity(BigDecimal.ONE, " "));
        assertTrue(new Quantity(BigDecimal.TEN, "ud").exceeds(new Quantity(BigDecimal.ONE, "ud")));
    }

    @Test
    void orderStateMachineOnlyLetsUrgentOrdersStartFromDraft() {
        assertTrue(OrderStateMachine.canStart(MaintenanceOrderStatus.DRAFT, MaintenanceOrderType.URGENT));
        assertFalse(OrderStateMachine.canStart(MaintenanceOrderStatus.DRAFT, MaintenanceOrderType.CORRECTIVE));
        assertTrue(OrderStateMachine.canStart(MaintenanceOrderStatus.PLANNED, MaintenanceOrderType.PREVENTIVE));
        assertFalse(OrderStateMachine.canStart(MaintenanceOrderStatus.COMPLETED, MaintenanceOrderType.URGENT));
        assertFalse(OrderStateMachine.canStart(MaintenanceOrderStatus.CANCELLED, MaintenanceOrderType.URGENT));
        assertFalse(OrderStateMachine.canComplete(MaintenanceOrderStatus.CANCELLED));
        assertTrue(OrderStateMachine.canAssign(MaintenanceOrderStatus.IN_PROGRESS));
        assertFalse(OrderStateMachine.canCancel(MaintenanceOrderStatus.COMPLETED));
        assertFalse(OrderStateMachine.allowsFullUpdate(MaintenanceOrderStatus.ASSIGNED));
    }

    @Test
    void defectAndShiftStateMachinesFollowTheirTables() {
        assertTrue(DefectStateMachine.canResolve(DefectStatus.IN_PROGRESS));
        assertFalse(DefectStateMachine.canClose(DefectStatus.OPEN));
        assertTrue(DefectStateMachine.canClose(DefectStatus.RESOLVED));
        assertFalse(DefectStateMachine.canDiscard(DefectStatus.IN_PROGRESS));
        assertTrue(DefectStateMachine.isTerminal(DefectStatus.DISCARDED));
        assertTrue(ShiftStateMachine.canStart(ShiftStatus.PLANNED));
        assertFalse(ShiftStateMachine.canClose(ShiftStatus.PLANNED));
        assertFalse(ShiftStateMachine.allowsUpdate(ShiftStatus.CLOSED));
    }

    @Test
    void workloadEstimateRoundsShiftsUpToTheNextNight() {
        assertEquals(0, WorkloadEstimate.ofMinutes(BigDecimal.ZERO).estimatedShifts());
        assertEquals(1, WorkloadEstimate.ofMinutes(new BigDecimal("270")).estimatedShifts());
        assertEquals(2, WorkloadEstimate.ofMinutes(new BigDecimal("271")).estimatedShifts());
        assertEquals(4, new WorkloadEstimate(new BigDecimal("1000"), new BigDecimal("300")).estimatedShifts());
        assertThrows(IllegalArgumentException.class, () -> WorkloadEstimate.ofMinutes(new BigDecimal("-1")));
    }

    @Test
    void orderStateMachinePlansOnlyFromDraftAndCancelsAnythingNotTerminal() {
        assertTrue(OrderStateMachine.canPlan(MaintenanceOrderStatus.DRAFT));
        assertFalse(OrderStateMachine.canPlan(MaintenanceOrderStatus.PLANNED));
        for (MaintenanceOrderStatus status : MaintenanceOrderStatus.values()) {
            boolean terminal = EnumSet.of(MaintenanceOrderStatus.COMPLETED, MaintenanceOrderStatus.CANCELLED).contains(status);
            assertEquals(!terminal, OrderStateMachine.canCancel(status), status.name());
        }
        assertTrue(OrderStateMachine.allowsFullUpdate(MaintenanceOrderStatus.DRAFT));
        assertTrue(OrderStateMachine.allowsFullUpdate(MaintenanceOrderStatus.PLANNED));
        assertFalse(OrderStateMachine.allowsFullUpdate(MaintenanceOrderStatus.IN_PROGRESS));
        assertFalse(OrderStateMachine.canAssign(MaintenanceOrderStatus.DRAFT), "Assigning needs a planned order");
        assertTrue(OrderStateMachine.canAssign(MaintenanceOrderStatus.ASSIGNED), "Reassigning is allowed");
        assertFalse(OrderStateMachine.canComplete(MaintenanceOrderStatus.ASSIGNED));
        assertTrue(OrderStateMachine.canComplete(MaintenanceOrderStatus.IN_PROGRESS));
        assertTrue(OrderStateMachine.canStart(MaintenanceOrderStatus.ASSIGNED, MaintenanceOrderType.INSPECTION));
    }

    @Test
    void defectsLinkToOrdersOnlyWhileOpenOrInProgressAndShiftsCancelUntilClosed() {
        assertTrue(DefectStateMachine.canLinkOrder(DefectStatus.OPEN));
        assertTrue(DefectStateMachine.canLinkOrder(DefectStatus.IN_PROGRESS));
        assertFalse(DefectStateMachine.canLinkOrder(DefectStatus.RESOLVED));
        assertFalse(DefectStateMachine.canLinkOrder(DefectStatus.CLOSED));
        assertFalse(DefectStateMachine.canResolve(DefectStatus.RESOLVED));
        assertTrue(DefectStateMachine.canDiscard(DefectStatus.OPEN));
        assertFalse(DefectStateMachine.canDiscard(DefectStatus.RESOLVED));
        assertFalse(DefectStateMachine.isTerminal(DefectStatus.RESOLVED), "Resolved still waits for the close");
        assertTrue(DefectStateMachine.isTerminal(DefectStatus.CLOSED));

        assertTrue(ShiftStateMachine.canCancel(ShiftStatus.PLANNED));
        assertTrue(ShiftStateMachine.canCancel(ShiftStatus.IN_PROGRESS));
        assertFalse(ShiftStateMachine.canCancel(ShiftStatus.CLOSED));
        assertFalse(ShiftStateMachine.canCancel(ShiftStatus.CANCELLED));
        assertFalse(ShiftStateMachine.canStart(ShiftStatus.IN_PROGRESS));
        assertTrue(ShiftStateMachine.canClose(ShiftStatus.IN_PROGRESS));
        assertTrue(ShiftStateMachine.allowsUpdate(ShiftStatus.IN_PROGRESS));
        assertFalse(ShiftStateMachine.allowsUpdate(ShiftStatus.CANCELLED));
    }

    @Test
    void domainValidationsNameTheFieldAndTheValueTypesRejectNulls() {
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> DomainValidations.requireNonNull(null, "startKp"));
        assertEquals("startKp is required", missing.getMessage());
        assertEquals("x", DomainValidations.requireNonNull("x", "field"));
        IllegalArgumentException broken = assertThrows(IllegalArgumentException.class, () -> DomainValidations.require(false, "boom"));
        assertEquals("boom", broken.getMessage());
        assertDoesNotThrow(() -> DomainValidations.require(true, "never"));

        assertThrows(IllegalArgumentException.class, () -> new KilometricRange(null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new KilometricRange(BigDecimal.ONE, null));
        KilometricRange point = new KilometricRange(BigDecimal.TEN, BigDecimal.TEN);
        assertEquals(0, BigDecimal.ZERO.compareTo(point.lengthKm()), "A single kp is a valid, empty range");
        assertTrue(point.contains(BigDecimal.TEN));
        assertFalse(point.contains(null));

        assertThrows(IllegalArgumentException.class, () -> new Quantity(null, "ud"));
        assertThrows(IllegalArgumentException.class, () -> new Quantity(BigDecimal.ONE, null));
        assertFalse(new Quantity(BigDecimal.ONE, "ud").exceeds(new Quantity(BigDecimal.ONE, "ud")));

        assertThrows(IllegalArgumentException.class, () -> new WorkloadEstimate(BigDecimal.TEN, BigDecimal.ZERO), "A shift with no effective time cannot be divided by");
        assertThrows(IllegalArgumentException.class, () -> new WorkloadEstimate(null, BigDecimal.TEN));
        assertEquals(WorkloadEstimate.DEFAULT_EFFECTIVE_MINUTES_PER_SHIFT, WorkloadEstimate.ofMinutes(BigDecimal.ONE).effectiveMinutesPerShift());
    }
}
