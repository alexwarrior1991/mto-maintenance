package com.alejandro.mtomaintenance.application.dto.defect;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Alta de un defecto en un activo (tambien se registran al completar una tarea, en {@code inlineDefects}).
 *
 * <p>Ejemplo, {@code POST /defects}:</p>
 * <pre>{@code
 * {"assetId": "…(PRF-4321)", "severity": "LOW", "description": "Pendola danada en 13-2.01",
 *  "detectedAt": "2026-01-27T22:40:00Z", "startKp": 13007.290, "endKp": 13007.290, "photoRefs": ["IMG_0412.jpg"]}
 * }</pre>
 */
public record CatenaryDefectRequest(
        @NotNull UUID assetId,
        @NotNull DefectSeverity severity,
        @NotBlank String description,
        String technicalNotes,
        @PastOrPresent Instant detectedAt,
        UUID inspectionId,
        UUID orderId,
        @Digits(integer = 9, fraction = 3) BigDecimal startKp,
        @Digits(integer = 9, fraction = 3) BigDecimal endKp,
        @Size(max = 120) String correctionType,
        String partsReplaced,
        LocalDate repairPlannedDate,
        List<@NotNull String> photoRefs
) {
}
