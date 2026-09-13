package com.alejandro.mtomaintenance.application.dto.task;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Defecto encontrado (y quiza corregido) al completar la tarea: la Corrective Action Table del informe. */
public record InlineDefectRequest(
        @NotNull DefectSeverity severity,
        @NotBlank String description,
        String technicalNotes,
        @Size(max = 120) String correctionType,
        String partsReplaced
) {
}
