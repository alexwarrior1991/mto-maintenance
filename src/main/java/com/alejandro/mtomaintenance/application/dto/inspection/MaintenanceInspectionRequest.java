package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceInspectionRequest(
        @NotNull UUID assetId,
        @NotNull LocalDate inspectionDate,
        @Size(max = 100) String inspector,
        InspectionKind inspectionKind,
        @NotNull InspectionResult result,
        String description,
        String detectedDefects,
        String recommendedActions,
        @Digits(integer = 9, fraction = 3) BigDecimal kp,
        UUID originOrderId,
        UUID shiftId
) {
}
