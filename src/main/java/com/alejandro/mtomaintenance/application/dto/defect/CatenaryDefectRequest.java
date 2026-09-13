package com.alejandro.mtomaintenance.application.dto.defect;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CatenaryDefectRequest(
        @NotNull UUID assetId,
        @NotNull DefectSeverity severity,
        @NotBlank String description,
        String technicalNotes,
        Instant detectedAt,
        UUID inspectionId,
        UUID orderId,
        @Digits(integer = 9, fraction = 3) BigDecimal startKp,
        @Digits(integer = 9, fraction = 3) BigDecimal endKp,
        @Size(max = 120) String correctionType,
        String partsReplaced,
        LocalDate repairPlannedDate,
        List<String> photoRefs
) {
}
