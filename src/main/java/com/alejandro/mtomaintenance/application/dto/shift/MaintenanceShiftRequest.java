package com.alejandro.mtomaintenance.application.dto.shift;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Set;

public record MaintenanceShiftRequest(
        @NotNull LocalDate shiftDate,
        UUID teamId,
        @Size(max = 120) String baseName,
        @Size(max = 120) String vehicle,
        @NotNull PossessionType possessionType,
        Instant plannedStart,
        Instant plannedEnd,
        Set<UUID> blockingDisconnectorIds,
        @Size(max = 500) String earthingPoints,
        @Size(max = 255) String parkingPlace,
        Long executionPackageId,
        @NotEmpty Set<Long> trackIds,
        @Digits(integer = 9, fraction = 3) BigDecimal startKp,
        @Digits(integer = 9, fraction = 3) BigDecimal endKp,
        String personnel,
        @Size(max = 500) String measurementEquipment,
        String observations
) {
}
