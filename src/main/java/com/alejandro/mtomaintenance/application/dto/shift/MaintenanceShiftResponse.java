package com.alejandro.mtomaintenance.application.dto.shift;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtomaintenance.application.dto.crew.CrewPersonSummary;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamSummaryResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Un turno nocturno con su ventana prevista y real, su posesion y los seccionadores que la bloquean.
 *
 * <p>Ejemplo, {@code GET /shifts/{id}} de un turno cerrado:</p>
 * <pre>{@code
 * {"id": "0195a…", "code": "SH-000001", "shiftDate": "2026-01-27", "team": {"code": "A", "name": "Team A - Rishpon", …},
 *  "possessionType": "PARTIAL", "actualStart": "2026-01-27T19:10:00Z", "voltageCutoffAt": "2026-01-27T21:40:00Z",
 *  "actualEnd": "2026-01-28T02:30:00Z", "netWorkMinutes": 290, "blockingDisconnectors": [{"code": "DSC-77", …}, …],
 *  "trackIds": [2], "status": "CLOSED", "observations": "Corte de tension retrasado por trafico", "version": 3, …}
 * }</pre>
 */
public record MaintenanceShiftResponse(
        UUID id,
        String code,
        LocalDate shiftDate,
        MaintenanceTeamSummaryResponse team,
        String baseName,
        String vehicle,
        PossessionType possessionType,
        Instant plannedStart,
        Instant plannedEnd,
        Instant actualStart,
        Instant actualEnd,
        Instant voltageCutoffAt,
        Integer netWorkMinutes,
        List<CatenaryAssetSummaryResponse> blockingDisconnectors,
        String earthingPoints,
        String parkingPlace,
        Long executionPackageId,
        List<Long> trackIds,
        BigDecimal startKp,
        BigDecimal endKp,
        String personnel,
        CrewPersonSummary teamLeader,
        CrewPersonSummary supervisor,
        List<CrewPersonSummary> linemen,
        List<ShiftVehicleResponse> vehicles,
        String measurementEquipment,
        ShiftStatus status,
        String observations,
        Long version,
        AuditMetadataResponse audit
) {
}
