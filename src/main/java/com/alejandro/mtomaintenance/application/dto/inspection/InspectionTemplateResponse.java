package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;

import java.util.List;
import java.util.UUID;

/**
 * Plantilla de inspeccion de un tipo de activo; la activa es la que copia cada inspeccion nueva.
 *
 * <p>Ejemplo, {@code GET /inspection-templates/{id}}:</p>
 * <pre>{@code
 * {"id": "0195a000-0000-7000-8000-00000000a002", "assetType": "DISCONNECTOR", "version": 1, "name": "Disconnector inspection",
 *  "active": true, "items": [{"code": "CONTACTS", …}, …, {"code": "CONTINUITY", "unit": "uOhm", "maxValue": 100.000, …}, …]}
 * }</pre>
 */
public record InspectionTemplateResponse(
        UUID id,
        CatenaryAssetType assetType,
        Integer version,
        String name,
        Boolean active,
        List<InspectionTemplateItemResponse> items
) {
}
