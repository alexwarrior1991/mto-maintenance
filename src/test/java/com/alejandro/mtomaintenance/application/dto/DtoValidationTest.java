package com.alejandro.mtomaintenance.application.dto;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetRequest;
import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageRequest;
import com.alejandro.mtomaintenance.application.dto.order.AssignOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderRequest;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftRequest;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.application.dto.task.CompleteTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.InlineDefectRequest;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import com.alejandro.mtomaintenance.application.dto.defect.CatenaryDefectRequest;
import com.alejandro.mtomaintenance.application.dto.defect.DefectCommentRequest;
import com.alejandro.mtomaintenance.application.dto.defect.ResolveDefectRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.CheckItemUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.CreateDefectFromInspectionRequest;
import com.alejandro.mtomaintenance.application.dto.inspection.MaintenanceInspectionRequest;
import com.alejandro.mtomaintenance.application.dto.order.CompleteOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.shift.CancelShiftRequest;
import com.alejandro.mtomaintenance.application.dto.shift.CloseShiftRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksRequest;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.task.TaskMaterialRequest;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamRequest;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import jakarta.validation.ConstraintViolation;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void trackSectionRequestRejectsAnInvertedKilometricRange() {
        CatenaryAssetRequest request = new CatenaryAssetRequest("SEC-1", "Ranana-Herzliya T2", null, 6L, 2L, null,
                new BigDecimal("14078.090"), new BigDecimal("12847.990"), TrackKind.MAIN, 365);

        var violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("validRange", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void materialUsageRequestNeedsAMaterialReferenceAndANonNegativeQuantity() {
        MaterialUsageRequest request = new MaterialUsageRequest(null, " ", UUID.randomUUID(), new BigDecimal("-2"), "ud", null, null);

        var violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(violation -> "plannedQuantity".contentEquals(violation.getPropertyPath().toString())));
        assertTrue(violations.stream().anyMatch(violation -> "materialReference".contentEquals(violation.getPropertyPath().toString())));
    }

    @Test
    void orderRequestNeedsTitleTypeAndAsset() {
        MaintenanceOrderRequest request = new MaintenanceOrderRequest(" ", null, null, null, null, null, null, null, null);

        var violations = validator.validate(request);

        assertEquals(3, violations.size());
    }

    @Test
    void assignRequestNeedsATeamOrAUser() {
        assertEquals(1, validator.validate(new AssignOrderRequest(null, " ", null)).size());
        assertTrue(validator.validate(new AssignOrderRequest(UUID.randomUUID(), null, null)).isEmpty());
    }

    @Test
    void completeTaskRequestValidatesNestedInlineDefects() {
        CompleteTaskRequest request = new CompleteTaskRequest(UUID.randomUUID(), List.of("RG-01"), "Cleaned", null, true, null,
                List.of(new InlineDefectRequest(DefectSeverity.LOW, " ", null, null, null)), null, null);

        var violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getPropertyPath().toString().startsWith("inlineDefects[0].description"));
        assertTrue(request.isWorkComplete());
    }

    @Test
    void aShiftNeedsAtLeastOneTrack() {
        MaintenanceShiftRequest request = new MaintenanceShiftRequest(LocalDate.of(2026, 1, 27), null, null, null, PossessionType.PARTIAL,
                null, null, null, null, null, null, Set.of(), null, null, null, null, null);

        var violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("trackIds", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void defectRequestNeedsAssetSeverityAndDescriptionWithThreeDecimalKpAndAShortCorrectionType() {
        CatenaryDefectRequest request = new CatenaryDefectRequest(null, null, " ", null, null, null, null,
                new BigDecimal("12847.9901"), null, "x".repeat(121), null, null, null);

        assertEquals(Set.of("assetId", "severity", "description", "startKp", "correctionType"), fields(validator.validate(request)));
        assertTrue(validator.validate(new CatenaryDefectRequest(UUID.randomUUID(), DefectSeverity.HIGH, "kink", null, null, null, null,
                new BigDecimal("12847.990"), null, null, null, null, null)).isEmpty());
    }

    @Test
    void teamAndInspectionRequestsLimitTheirTextsAndNeedTheirKeys() {
        assertEquals(Set.of("code", "name"), fields(validator.validate(new MaintenanceTeamRequest("A".repeat(17), " ", null, null, null, null))));
        assertTrue(validator.validate(new MaintenanceTeamRequest("A", "Team A", null, null, null, null)).isEmpty());

        MaintenanceInspectionRequest inspection = new MaintenanceInspectionRequest(null, null, "x".repeat(101), null, null, null, null, null, null, null, null);
        assertEquals(Set.of("assetId", "inspectionDate", "result", "inspector"), fields(validator.validate(inspection)));
    }

    @Test
    void aTaskMaterialLineNeedsAMaterialIdOrCode() {
        UUID warehouseId = UUID.randomUUID();

        assertTrue(validator.validate(new TaskMaterialRequest(UUID.randomUUID(), null, warehouseId, BigDecimal.ONE, "ud")).isEmpty());
        assertTrue(validator.validate(new TaskMaterialRequest(null, "GA70", warehouseId, BigDecimal.ONE, null)).isEmpty());
        assertEquals(Set.of("materialReference"), fields(validator.validate(new TaskMaterialRequest(null, " ", warehouseId, BigDecimal.ONE, "ud"))),
                "Without it the line reached MaterialLineFactory and answered 500");
    }

    @Test
    void reasonsMeasurementsAndTaskMaterialsAreValidated() {
        assertEquals(Set.of("resolutionNotes"), fields(validator.validate(new ResolveDefectRequest(" ", null, null, null))));
        assertEquals(Set.of("reason"), fields(validator.validate(new DefectCommentRequest(""))));
        assertTrue(validator.validate(new CancelShiftRequest("rain")).isEmpty());
        assertEquals(Set.of("warehouseId", "quantity", "materialReference"), fields(validator.validate(new TaskMaterialRequest(null, null, null, new BigDecimal("-1"), null))));
        assertEquals(Set.of("netWorkMinutes"), fields(validator.validate(new CloseShiftRequest(null, null, -5, null))));
        assertEquals(Set.of("measuredValue"), fields(validator.validate(new CheckItemUpdateRequest(new BigDecimal("1.2345"), null, null, null, null, null))));
    }

    @Test
    void requestFlagsDefaultToTheSafeSide() {
        assertFalse(new CompleteOrderRequest(null, null, null).isForced());
        assertTrue(new CompleteOrderRequest(null, true, null).isForced());
        assertFalse(new CreateDefectFromInspectionRequest(null, null, null, null).isForced());
        assertTrue(new CompleteTaskRequest(UUID.randomUUID(), null, null, null, null, null, null, null, null).isWorkComplete());
        assertFalse(new CompleteTaskRequest(UUID.randomUUID(), null, null, null, false, null, null, null, null).isWorkComplete());
        assertFalse(new MaintenanceOrderUpdateRequest(null, "d", MaintenancePriority.HIGH, null, null, null, "n", null, null, null, null, null, null, null).touchesRestrictedFields());
        assertTrue(new MaintenanceOrderUpdateRequest("t", null, null, null, null, null, null, null, null, null, null, null, null, null).touchesRestrictedFields());
        assertTrue(new MaintenanceOrderUpdateRequest(null, null, null, null, null, null, null, null, null, null, null, null, UUID.randomUUID(), null).touchesRestrictedFields());
    }

    @Test
    void aNullInsideAListIsRejectedOnItsListInsteadOfReachingTheService() {
        // Llegaban al servicio: un codigo de tipo de tarea nulo era un NPE al resolverlo (un 500), un
        // defecto o un material nulos otro NPE al completar, una via o un paquete nulos un 409 de la
        // base, y una foto nula se guardaba como el texto "null".
        List<String> codes = Arrays.asList("RG-01", null);
        CompleteTaskRequest complete = new CompleteTaskRequest(UUID.randomUUID(), codes, null, null, true, null,
                Arrays.asList((InlineDefectRequest) null), Arrays.asList((TaskMaterialRequest) null), codes);
        assertEquals(Set.of("taskTypeCodes", "inlineDefects", "materials", "photoRefs"), lists(validator.validate(complete)));
        assertEquals(Set.of("taskTypeCodes"), lists(validator.validate(new GeneratePreventiveTasksRequest(codes, null))));
        assertEquals(Set.of("taskTypeCodes", "photoRefs"), lists(validator.validate(new MaintenanceTaskUpdateRequest(null, null, codes, null, null, codes, 0L))));

        Set<Long> tracks = new HashSet<>(Arrays.asList(2L, null));
        Set<UUID> disconnectors = new HashSet<>(Arrays.asList(UUID.randomUUID(), null));
        assertEquals(Set.of("trackIds", "blockingDisconnectorIds"), lists(validator.validate(new MaintenanceShiftRequest(LocalDate.of(2026, 1, 27), null, null,
                null, PossessionType.PARTIAL, null, null, disconnectors, null, null, null, tracks, null, null, null, null, null))));
        assertEquals(Set.of("executionPackageIds"), lists(validator.validate(new MaintenanceTeamRequest("A", "Team A", null, null, null, tracks))));
        assertEquals(Set.of("photoRefs"), lists(validator.validate(new CatenaryDefectRequest(UUID.randomUUID(), DefectSeverity.HIGH, "kink", null, null,
                null, null, null, null, null, null, null, codes))));
    }

    @Test
    void aDefectCannotBeDetectedInTheFuture() {
        // Resolverlo despues pondria resolvedAt antes que detectedAt, y el CHECK de la base lo rechazaba
        // con un 409 generico.
        assertEquals(Set.of("detectedAt"), fields(validator.validate(new CatenaryDefectRequest(UUID.randomUUID(), DefectSeverity.HIGH, "kink", null,
                Instant.now().plus(Duration.ofHours(2)), null, null, null, null, null, null, null, null))));
        assertTrue(validator.validate(new CatenaryDefectRequest(UUID.randomUUID(), DefectSeverity.HIGH, "kink", null,
                Instant.now().minus(Duration.ofMinutes(5)), null, null, null, null, null, null, null, null)).isEmpty());
    }

    private static Set<String> fields(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(violation -> violation.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
    }

    /** La lista de cada violacion: la de un elemento nulo lleva su indice detras ({@code taskTypeCodes[1]}). */
    private static Set<String> lists(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(violation -> violation.getPropertyPath().iterator().next().getName())
                .collect(java.util.stream.Collectors.toSet());
    }
}
