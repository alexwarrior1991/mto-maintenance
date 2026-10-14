package com.alejandro.mtomaintenance.application.dto.asset;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * El activo tal como lo llevan una orden, una tarea, una inspeccion o un defecto.
 *
 * @param connectedTrackId la otra via de un aislador de seccion o de un seccionador que une dos: se
 *                         trabaja desde un turno de cualquiera de las dos ({@code ShiftRules}), y el
 *                         cliente la necesita para ofrecer esos turnos
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
        Long connectedTrackId
) {
}
