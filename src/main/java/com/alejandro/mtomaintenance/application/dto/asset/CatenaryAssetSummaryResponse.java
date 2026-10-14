package com.alejandro.mtomaintenance.application.dto.asset;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DisconnectorDriveType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * El activo tal como lo llevan una orden, una tarea, una inspeccion o un defecto.
 *
 * @param connectedTrackId la otra via de un aislador de seccion o de un seccionador que une dos: se
 *                         trabaja desde un turno de cualquiera de las dos ({@code ShiftRules}), y el
 *                         cliente la necesita para ofrecer esos turnos
 * @param executionPackageId el EP del activo: una orden es de un solo EP, y lo que genera tambien
 * @param driveType          con motor o a mano, solo en un seccionador; null sin dato (V16)
 */
public record CatenaryAssetSummaryResponse(
        UUID id,
        String code,
        String name,
        CatenaryAssetType type,
        Long trackId,
        BigDecimal startKp,
        BigDecimal endKp,
        String sectioning,
        Boolean enabled,
        Long connectedTrackId,
        Long executionPackageId,
        Long stationId,
        DisconnectorDriveType driveType
) {
}
