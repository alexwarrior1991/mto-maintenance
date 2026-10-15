package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Modificacion de una inspeccion: fecha, inspector, tipo, resultado, textos y kp.
 *
 * <p>Ejemplo, {@code PATCH /inspections/{id}} con {@code Content-Type: application/merge-patch+json}:</p>
 * <pre>{@code
 * {"detectedDefects": "Contactos principales quemados, 180 uOhm", "recommendedActions": "Sustituir el seccionador",
 *  "version": 2}
 * }</pre>
 */
public record MaintenanceInspectionUpdateRequest(
        LocalDate inspectionDate,
        @Size(max = 100) String inspector,
        InspectionKind inspectionKind,
        InspectionResult result,
        String description,
        String detectedDefects,
        String recommendedActions,
        @Digits(integer = 9, fraction = 3) BigDecimal kp,
        Long version
) {
}
