package com.alejandro.mtomaintenance.application.dto.order;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** En DRAFT/PLANNED se aplica todo; despues solo description, priority y closingNotes. */
public record MaintenanceOrderUpdateRequest(
        @Size(max = 255) String title,
        String description,
        MaintenancePriority priority,
        LocalDate plannedDate,
        UUID teamId,
        @Size(max = 100) String assignedUser,
        String closingNotes,
        Long executionPackageId,
        Long trackId,
        Long stationId,
        @Digits(integer = 9, fraction = 3) BigDecimal startKp,
        @Digits(integer = 9, fraction = 3) BigDecimal endKp,
        UUID stockProjectId
) {
    public boolean touchesRestrictedFields() {
        return title != null || plannedDate != null || teamId != null || assignedUser != null
                || executionPackageId != null || trackId != null || stationId != null
                || startKp != null || endKp != null || stockProjectId != null;
    }
}
