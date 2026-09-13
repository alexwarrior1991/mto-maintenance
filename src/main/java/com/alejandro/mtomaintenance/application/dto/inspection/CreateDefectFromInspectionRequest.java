package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;

/** Sin severidad se deriva del resultado (UNSAFE -> CRITICAL, MAJOR -> HIGH, MINOR -> MEDIUM). */
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
