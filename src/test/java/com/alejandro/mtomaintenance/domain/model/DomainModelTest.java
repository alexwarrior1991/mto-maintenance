package com.alejandro.mtomaintenance.domain.model;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
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
}
