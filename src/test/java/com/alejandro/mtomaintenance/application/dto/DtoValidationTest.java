package com.alejandro.mtomaintenance.application.dto;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetRequest;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageRequest;
import com.alejandro.mtomaintenance.application.dto.order.AssignOrderRequest;
import com.alejandro.mtomaintenance.application.dto.order.MaintenanceOrderRequest;
import com.alejandro.mtomaintenance.application.dto.task.CompleteTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.InlineDefectRequest;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
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
}
