package com.alejandro.mtomaintenance.application.dto.shift;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Set;

/**
 * Edicion de un turno, por PUT o por PATCH, con la version leida.
 *
 * <p>Ejemplo, {@code PATCH /shifts/{id}} con {@code Content-Type: application/merge-patch+json}
 * ({@code null} vacia el campo):</p>
 * <pre>{@code
 * {"blockingDisconnectorIds": ["…(DSC-77)", "…(DSC-81)", "…(DSC-84)"], "earthingPoints": null, "version": 1}
 * }</pre>
 *
 * <p>{@code vehicleIds}, {@code linemanPersonIds} e {@code instrumentIds} son conjuntos que se sustituyen
 * enteros; en un PATCH, {@code null} los quita todos.</p>
 */
public record MaintenanceShiftUpdateRequest(
        LocalDate shiftDate,
        UUID teamId,
        @Size(max = 120) String baseName,
        @Size(max = 120) String vehicle,
        PossessionType possessionType,
        Instant plannedStart,
        Instant plannedEnd,
        Set<@NotNull UUID> blockingDisconnectorIds,
        @Size(max = 500) String earthingPoints,
        @Size(max = 255) String parkingPlace,
        Long executionPackageId,
        Set<@NotNull Long> trackIds,
        @Digits(integer = 9, fraction = 3) BigDecimal startKp,
        @Digits(integer = 9, fraction = 3) BigDecimal endKp,
        String personnel,
        @Size(max = 500) String measurementEquipment,
        String observations,
        Set<@NotNull UUID> vehicleIds,
        UUID teamLeaderPersonId,
        UUID supervisorPersonId,
        Set<@NotNull UUID> linemanPersonIds,
        Set<@NotNull UUID> instrumentIds,
        Long version
) {

    /** Sin tocar la dotacion ni los vehiculos. */
    public MaintenanceShiftUpdateRequest(LocalDate shiftDate, UUID teamId, String baseName, String vehicle, PossessionType possessionType,
                                         Instant plannedStart, Instant plannedEnd, Set<UUID> blockingDisconnectorIds, String earthingPoints,
                                         String parkingPlace, Long executionPackageId, Set<Long> trackIds, BigDecimal startKp,
                                         BigDecimal endKp, String personnel, String measurementEquipment, String observations,
                                         Long version) {
        this(shiftDate, teamId, baseName, vehicle, possessionType, plannedStart, plannedEnd, blockingDisconnectorIds, earthingPoints,
                parkingPlace, executionPackageId, trackIds, startKp, endKp, personnel, measurementEquipment, observations,
                null, null, null, null, null, version);
    }

    /** Con dotacion y vehiculos, sin tocar los instrumentos. */
    public MaintenanceShiftUpdateRequest(LocalDate shiftDate, UUID teamId, String baseName, String vehicle, PossessionType possessionType,
                                         Instant plannedStart, Instant plannedEnd, Set<UUID> blockingDisconnectorIds, String earthingPoints,
                                         String parkingPlace, Long executionPackageId, Set<Long> trackIds, BigDecimal startKp,
                                         BigDecimal endKp, String personnel, String measurementEquipment, String observations,
                                         Set<UUID> vehicleIds, UUID teamLeaderPersonId, UUID supervisorPersonId, Set<UUID> linemanPersonIds,
                                         Long version) {
        this(shiftDate, teamId, baseName, vehicle, possessionType, plannedStart, plannedEnd, blockingDisconnectorIds, earthingPoints,
                parkingPlace, executionPackageId, trackIds, startKp, endKp, personnel, measurementEquipment, observations,
                vehicleIds, teamLeaderPersonId, supervisorPersonId, linemanPersonIds, null, version);
    }
}
