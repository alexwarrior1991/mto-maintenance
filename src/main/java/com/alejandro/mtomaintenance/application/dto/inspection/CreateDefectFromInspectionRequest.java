package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;

/**
 * Sin severidad se deriva del resultado (UNSAFE -> CRITICAL, MAJOR -> HIGH, MINOR -> MEDIUM).
 *
 * <p>Ejemplo, {@code POST /inspections/{id}/create-defect} (INS-000004; una MINOR_DEFECT pide ademas {@code "force": true}):</p>
 * <pre>{@code
 * {"severity": "CRITICAL", "description": "Seccionador HSA-NS5 inoperable",
 *  "technicalNotes": "Resistencia de contacto 180 uOhm (max 100)"}
 * }</pre>
 */
public record CreateDefectFromInspectionRequest(
        DefectSeverity severity,
        String description,
        String technicalNotes,
        Boolean force
) {
    public boolean isForced() {
        return Boolean.TRUE.equals(force);
    }
}
