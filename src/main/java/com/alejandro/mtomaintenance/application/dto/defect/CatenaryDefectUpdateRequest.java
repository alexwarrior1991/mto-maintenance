package com.alejandro.mtomaintenance.application.dto.defect;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Modificacion de un defecto que no esta cerrado ni descartado; su estado cambia con resolve, close y discard.
 *
 * <p>Ejemplo, {@code PATCH /defects/{id}} con {@code Content-Type: application/merge-patch+json}:</p>
 * <pre>{@code
 * {"technicalNotes": "Resistencia de contacto 180 uOhm (max 100)", "repairPlannedDate": "2026-01-29", "version": 1}
 * }</pre>
 */
public record CatenaryDefectUpdateRequest(
        DefectSeverity severity,
        String description,
        String technicalNotes,
        @Size(max = 120) String correctionType,
        String partsReplaced,
        LocalDate repairPlannedDate,
        List<@NotNull String> photoRefs,
        Long version
) {
}
