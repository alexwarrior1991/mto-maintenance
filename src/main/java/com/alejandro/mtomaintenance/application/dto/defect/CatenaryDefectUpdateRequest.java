package com.alejandro.mtomaintenance.application.dto.defect;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CatenaryDefectUpdateRequest(
        DefectSeverity severity,
        String description,
        String technicalNotes,
        @Size(max = 120) String correctionType,
        String partsReplaced,
        LocalDate repairPlannedDate,
        List<String> photoRefs
) {
}
