package com.alejandro.mtomaintenance.application.dto.team;

import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtomaintenance.application.dto.crew.CrewPersonSummary;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Un equipo de mantenimiento con su vehiculo y los EP que atiende. Uno sincronizado
 * ({@code synchronizedFromResources}) es de mto-resources y trae ademas su jefe, su supervisor y sus
 * linemen; no tiene base, y {@code vehicle} es el codigo de su vehiculo por defecto. Su
 * {@code sourceEntityId} es el id de su unidad en mto-resources: con el, una pantalla enlaza el equipo
 * con su ficha del organigrama.
 *
 * <p>Ejemplo, {@code GET /teams/{id}} de un equipo sincronizado:</p>
 * <pre>{@code
 * {"id": "0195a000-0000-7000-8000-000000000001", "code": "A", "name": "Team A", "baseName": null,
 *  "vehicle": "RR-07", "active": true, "executionPackageIds": [4, 5, 6], "synchronizedFromResources": true,
 *  "sourceEntityId": "0195b000-0000-7000-8000-00000000000a",
 *  "leader": {"code": "PER-000003", "fullName": "Yossi Cohen", "username": "equipo.jefe", …},
 *  "supervisor": {…}, "members": [{…}, {…}, {…}], "defaultVehicleId": "…", "audit": {…}}
 * }</pre>
 */
public record MaintenanceTeamResponse(
        UUID id,
        String code,
        String name,
        String baseName,
        String vehicle,
        Boolean active,
        Set<Long> executionPackageIds,
        Boolean synchronizedFromResources,
        UUID sourceEntityId,
        CrewPersonSummary leader,
        CrewPersonSummary supervisor,
        List<CrewPersonSummary> members,
        UUID defaultVehicleId,
        AuditMetadataResponse audit
) {
}
