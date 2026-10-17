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

/**
 * Alta de un turno nocturno: fecha, equipo, ventana, posesion, vias y seccionadores de bloqueo, y su
 * dotacion y sus vehiculos de mto-resources. Con un equipo sincronizado, lo que no se dice (jefe,
 * supervisor, linemen, vehiculos) se copia del equipo; iniciarlo exige un jefe y de 2 a 6 linemen.
 *
 * <p>Ejemplo, {@code POST /shifts}:</p>
 * <pre>{@code
 * {"shiftDate": "2026-01-27", "teamId": "…(Team A)", "possessionType": "PARTIAL", "plannedStart": "2026-01-27T19:00:00Z",
 *  "plannedEnd": "2026-01-28T03:00:00Z", "blockingDisconnectorIds": ["…(DSC-77)", "…(DSC-81)", "…(DSC-84)"],
 *  "executionPackageId": 6, "trackIds": [2], "startKp": 12847.990, "endKp": 14078.090,
 *  "vehicleIds": ["…(DR-02)"], "teamLeaderPersonId": "…", "linemanPersonIds": ["…", "…", "…"]}
 * }</pre>
 */
public record MaintenanceShiftRequest(
        @NotNull LocalDate shiftDate,
        UUID teamId,
        @Size(max = 120) String baseName,
        @Size(max = 120) String vehicle,
        @NotNull PossessionType possessionType,
        Instant plannedStart,
        Instant plannedEnd,
        Set<@NotNull UUID> blockingDisconnectorIds,
        @Size(max = 500) String earthingPoints,
        @Size(max = 255) String parkingPlace,
        Long executionPackageId,
        @NotEmpty Set<@NotNull Long> trackIds,
        @Digits(integer = 9, fraction = 3) BigDecimal startKp,
        @Digits(integer = 9, fraction = 3) BigDecimal endKp,
        String personnel,
        @Size(max = 500) String measurementEquipment,
        String observations,
        Set<@NotNull UUID> vehicleIds,
        UUID teamLeaderPersonId,
        UUID supervisorPersonId,
        Set<@NotNull UUID> linemanPersonIds
) {

    /** Sin dotacion ni vehiculos: los copia del equipo si esta sincronizado, como hacia antes con el texto. */
    public MaintenanceShiftRequest(LocalDate shiftDate, UUID teamId, String baseName, String vehicle, PossessionType possessionType,
                                   Instant plannedStart, Instant plannedEnd, Set<UUID> blockingDisconnectorIds, String earthingPoints,
                                   String parkingPlace, Long executionPackageId, Set<Long> trackIds, BigDecimal startKp, BigDecimal endKp,
                                   String personnel, String measurementEquipment, String observations) {
        this(shiftDate, teamId, baseName, vehicle, possessionType, plannedStart, plannedEnd, blockingDisconnectorIds, earthingPoints,
                parkingPlace, executionPackageId, trackIds, startKp, endKp, personnel, measurementEquipment, observations,
                null, null, null, null);
    }
}
