package com.alejandro.mtomaintenance.application.dto.task;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Defecto encontrado (y quiza corregido) al completar la tarea: la Corrective Action Table del informe.
 *
 * <p>Ejemplo, dentro de {@code CompleteTaskRequest.inlineDefects}:</p>
 * <pre>{@code
 * {"severity": "LOW", "description": "Pendola danada en 13-2.01", "technicalNotes": "Grapa del lado del sustentador rota",
 *  "correctionType": "Sustitucion", "partsReplaced": "1 pendola DROPPER-STD"}
 * }</pre>
 */
public record InlineDefectRequest(
        @NotNull DefectSeverity severity,
        @NotBlank String description,
        String technicalNotes,
        @Size(max = 120) String correctionType,
        String partsReplaced
) {
}
