package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.application.dto.common.AuditMetadataResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;

import java.util.List;
import java.util.UUID;

/**
 * Una version de la plantilla de inspeccion de un tipo de activo; la activa (como mucho una por tipo) es
 * la que copia cada inspeccion nueva.
 *
 * <p>{@code version} es el numero de version de la plantilla dentro de su tipo, no el bloqueo optimista:
 * ese es {@code lockVersion}, el que se devuelve en el {@code PATCH}. {@code audit} dice quien creo la
 * version y cuando, y quien la activo, la desactivo o la renombro por ultima vez.</p>
 *
 * <p>Ejemplo, {@code GET /inspection-templates/{id}}:</p>
 * <pre>{@code
 * {"id": "0195a000-0000-7000-8000-00000000a002", "assetType": "DISCONNECTOR", "version": 1, "name": "Disconnector inspection",
 *  "active": true, "items": [{"code": "CONTACTS", …}, …, {"code": "CONTINUITY", "unit": "uOhm", "maxValue": 100.000, …}, …],
 *  "lockVersion": 0, "audit": {"createdAt": "…", "updatedAt": "…", "createdBy": "system", "updatedBy": "system"}}
 * }</pre>
 */
public record InspectionTemplateResponse(
        UUID id,
        CatenaryAssetType assetType,
        Integer version,
        String name,
        Boolean active,
        List<InspectionTemplateItemResponse> items,
        Long lockVersion,
        AuditMetadataResponse audit
) {
}
