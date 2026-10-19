package com.alejandro.mtomaintenance.domain.model;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    // ------------------------------------------------------------ shift qualifications

    private static final LocalDate NIGHT = LocalDate.of(2026, 10, 17);

    @Test
    void whatExpiredBeforeTheShiftDateIsRefusedButItsLastDayStillCounts() {
        ShiftQualifications.Person lastDay = new ShiftQualifications.Person(UUID.randomUUID(), "Eli Katz (PER-000010)",
                new ShiftQualifications.Qualification("RAIL-DRIVER", NIGHT), new ShiftQualifications.Qualification("LIC-B", null));
        ShiftQualifications.Vehicle valid = new ShiftQualifications.Vehicle("RR-07", true, "RR-OPERATOR",
                List.of(new ShiftQualifications.Document("ROAD_INSPECTION", "ITV-2026-118", NIGHT.plusDays(1))));
        ShiftQualifications.Instrument noCalibration = new ShiftQualifications.Instrument("EP-07", true, null);

        assertEquals(Optional.empty(), ShiftQualifications.expiredViolation("Shift SH-000077", NIGHT, List.of(lastDay), List.of(valid),
                List.of(noCalibration)), "The day it expires it still holds, and a date that is not there never expires");

        ShiftQualifications.Person expired = new ShiftQualifications.Person(UUID.randomUUID(), "Avi Levi (PER-000012)",
                new ShiftQualifications.Qualification("LIC-C", NIGHT.minusDays(1)));
        ShiftQualifications.Vehicle inspected = new ShiftQualifications.Vehicle("DR-02", true, null,
                List.of(new ShiftQualifications.Document("RAIL_INSPECTION", "RI-44", NIGHT.minusDays(3))));
        ShiftQualifications.Instrument calibration = new ShiftQualifications.Instrument("VD-02", true, NIGHT.minusDays(10));
        ShiftQualifications.Instrument retired = new ShiftQualifications.Instrument("EP-09", false, NIGHT.plusYears(1));

        String reason = ShiftQualifications.expiredViolation("Shift SH-000077", NIGHT, List.of(lastDay, expired), List.of(valid, inspected),
                List.of(noCalibration, calibration, retired)).orElseThrow();
        // Todo lo caducado en un solo motivo, para arreglarlo de una vez.
        assertTrue(reason.contains("Avi Levi (PER-000012): qualification LIC-C expired on 2026-10-16"), reason);
        assertTrue(reason.contains("vehicle DR-02: RAIL_INSPECTION RI-44 expired on 2026-10-14"), reason);
        assertTrue(reason.contains("instrument VD-02: calibration expired on 2026-10-07"), reason);
        assertTrue(reason.contains("instrument EP-09: retired"), reason);
        assertFalse(reason.contains("Eli Katz"), reason);
        assertFalse(reason.contains("RR-07"), reason);
        // Caduca manana: hoy vale, pero un turno de pasado manana ya no.
        assertTrue(ShiftQualifications.expiredViolation("Shift SH-000078", NIGHT.plusDays(2), List.of(), List.of(valid), List.of()).isPresent(),
                "What counts is the night of the shift, not today");
        assertFalse(ShiftQualifications.expired(null, NIGHT));
        assertTrue(ShiftQualifications.expired(NIGHT.minusDays(1), NIGHT));
    }

    @Test
    void eachSelfPropelledVehicleThatRequiresItGetsADifferentQualifiedDriverByAMatchingNotAFirstComeLoop() {
        ShiftQualifications.Vehicle towerCar = new ShiftQualifications.Vehicle("DR-02", true, "RAIL-DRIVER", List.of());
        ShiftQualifications.Vehicle roadRail = new ShiftQualifications.Vehicle("RR-07", true, "RR-OPERATOR", List.of());
        ShiftQualifications.Vehicle wagon = new ShiftQualifications.Vehicle("WG-01", false, "RAIL-DRIVER", List.of());
        ShiftQualifications.Vehicle van = new ShiftQualifications.Vehicle("VAN-3", true, null, List.of());
        // Yossi sabe llevar los dos; Eli solo la dresina. Dando a cada vehiculo el primero que puede, Yossi
        // se quedaria la dresina y el bivial sin conductor: el emparejamiento le pasa la dresina a Eli.
        ShiftQualifications.Person yossi = new ShiftQualifications.Person(UUID.randomUUID(), "Yossi",
                new ShiftQualifications.Qualification("RAIL-DRIVER", null), new ShiftQualifications.Qualification("RR-OPERATOR", NIGHT.plusYears(1)));
        ShiftQualifications.Person eli = new ShiftQualifications.Person(UUID.randomUUID(), "Eli",
                new ShiftQualifications.Qualification("RAIL-DRIVER", NIGHT.plusMonths(2)));

        ShiftQualifications.DriverAssignment assignment = ShiftQualifications.assignDrivers(NIGHT, List.of(yossi, eli),
                List.of(towerCar, roadRail, wagon, van));

        assertEquals(eli, assignment.drivers().get("DR-02"));
        assertEquals(yossi, assignment.drivers().get("RR-07"), "Who could drive both drives one");
        assertEquals(List.of("DR-02", "RR-07"), List.copyOf(assignment.drivers().keySet()),
                "A towed wagon and a vehicle whose model requires nothing need no driver");
        assertTrue(assignment.withoutDriver().isEmpty());
        assertEquals(Optional.empty(), ShiftQualifications.driverViolation("Shift SH-000077", NIGHT, List.of(yossi, eli), List.of(towerCar, roadRail)));

        // Solo, Yossi conduce uno; el otro se queda sin conductor aunque el sepa llevarlo.
        String alone = ShiftQualifications.driverViolation("Shift SH-000077", NIGHT, List.of(yossi), List.of(towerCar, roadRail)).orElseThrow();
        assertTrue(alone.contains("no qualified driver for RR-07 (RR-OPERATOR)"), alone);
        assertFalse(alone.contains("DR-02 ("), alone);
        // Repetido en la lista sigue siendo una persona.
        assertEquals(1, ShiftQualifications.assignDrivers(NIGHT, List.of(yossi, yossi), List.of(towerCar, roadRail)).drivers().size());
        // Una habilitacion caducada esa noche no habilita.
        ShiftQualifications.Person lapsed = new ShiftQualifications.Person(UUID.randomUUID(), "Noam",
                new ShiftQualifications.Qualification("RAIL-DRIVER", NIGHT.minusDays(1)));
        assertTrue(ShiftQualifications.driverViolation("Shift SH-000077", NIGHT, List.of(lapsed), List.of(towerCar)).isPresent());
        assertEquals(Optional.empty(), ShiftQualifications.driverViolation("Shift SH-000077", NIGHT, List.of(), List.of(wagon, van)),
                "Nothing to drive, nothing to check");
    }

    @Test
    void anUnavailablePeriodCoversBothEndsAndAnOpenOneHasNoEndAndTheRestIsMeasuredWhenBothHoursAreKnown() {
        ShiftAvailability.Period leave = new ShiftAvailability.Period(NIGHT.minusDays(2), NIGHT);
        ShiftAvailability.Period repair = new ShiftAvailability.Period(NIGHT, null);

        assertTrue(leave.covers(NIGHT), "The last day of an absence is still absent");
        assertFalse(leave.covers(NIGHT.plusDays(1)));
        assertFalse(repair.covers(NIGHT.minusDays(1)));
        assertTrue(repair.covers(NIGHT.plusYears(1)), "A repair without end lasts until it is closed");

        String reason = ShiftAvailability.unavailableViolation("Shift SH-000077", NIGHT,
                List.of(new ShiftAvailability.Subject("Avi Levi (PER-000012)", List.of(leave)),
                        new ShiftAvailability.Subject("Eli Katz (PER-000010)", List.of(new ShiftAvailability.Period(NIGHT.plusDays(1), null)))),
                List.of(new ShiftAvailability.Subject("RR-07", List.of(repair)))).orElseThrow();
        assertTrue(reason.contains("Avi Levi (PER-000012) (unavailable " + NIGHT.minusDays(2) + " to " + NIGHT + ")"), reason);
        assertTrue(reason.contains("vehicle RR-07 (unavailable from " + NIGHT + ")"), reason);
        assertFalse(reason.contains("Eli Katz"), "Who is away from the next day works tonight");
        assertEquals(Optional.empty(), ShiftAvailability.unavailableViolation("Shift SH-000077", NIGHT, List.of(), List.of()));

        Instant end = Instant.parse("2026-10-17T04:00:00Z");
        ShiftAvailability.Night earlier = new ShiftAvailability.Night("SH-000076", null, end);
        ShiftAvailability.Night tooSoon = new ShiftAvailability.Night("SH-000077", end.plus(Duration.ofMinutes(8 * 60 + 30)), null);
        String rest = ShiftAvailability.restViolation("Avi Levi (PER-000012)", Duration.ofHours(12), earlier, tooSoon).orElseThrow();
        assertTrue(rest.contains("would rest 8h30 between shift SH-000076") && rest.contains("at least 12h"), rest);
        assertEquals(Optional.empty(), ShiftAvailability.restViolation("Avi", Duration.ofHours(12), earlier,
                new ShiftAvailability.Night("SH-000077", end.plus(Duration.ofHours(12)), null)), "Exactly the minimum is enough");
        assertEquals(Optional.empty(), ShiftAvailability.restViolation("Avi", Duration.ZERO, earlier, tooSoon), "PT0S switches it off");
        assertEquals(Optional.empty(), ShiftAvailability.restViolation("Avi", Duration.ofHours(12),
                new ShiftAvailability.Night("SH-000076", null, null), tooSoon), "Without the end of the night before it is not measured");
    }
}
