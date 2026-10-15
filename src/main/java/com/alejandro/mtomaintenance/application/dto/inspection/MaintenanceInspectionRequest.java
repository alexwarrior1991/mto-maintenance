package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Alta de una inspeccion de un activo; sus puntos se copian de la plantilla activa de su tipo.
 *
 * <p>Ejemplo, {@code POST /inspections}:</p>
 * <pre>{@code
 * {"assetId": "…(DSC-77)", "inspectionDate": "2026-01-28", "inspector": "T. Tecnico", "inspectionKind": "TECHNICAL",
 *  "result": "UNSAFE", "detectedDefects": "Contactos principales quemados", "recommendedActions": "Sustituir el seccionador",
 *  "shiftId": "…(SH-000001)"}
 * }</pre>
 */
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
