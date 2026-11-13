package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Una version nueva de la plantilla de inspeccion de un tipo de activo ({@code POST /inspection-templates}):
 * nace activa y sustituye a la activa de su tipo, que queda como anterior. La lista de puntos es la
 * entera, en su orden.
 *
 * <pre>{@code
 * {"assetType": "DISCONNECTOR", "name": "Disconnector inspection", "replacesVersion": 1,
 *  "items": [{"code": "CONTACTS", "label": "Main contacts state and alignment"},
 *            {"code": "CONTINUITY", "label": "Electrical continuity / contact resistance", "unit": "uOhm", "maxValue": 100,
 *             "requiresMeasure": true}]}
 * }</pre>
 *
 * @param assetType       el tipo de activo de la plantilla
 * @param name            su nombre
 * @param items           sus puntos, de 1 a {@value #MAX_ITEMS}, sin codigos repetidos
 * @param replacesVersion la {@code version} de la plantilla activa del tipo que leyo quien escribe: si la
 *                        activa ya es otra, o no hay ninguna, 409 {@code CON-001}; sin el no se comprueba
 */
public record InspectionTemplateRequest(
        @NotNull CatenaryAssetType assetType,
        @NotBlank @Size(max = 120) String name,
        @NotEmpty @Size(max = MAX_ITEMS) List<@NotNull @Valid InspectionTemplateItemRequest> items,
        @Positive Integer replacesVersion
) {

    public static final int MAX_ITEMS = 100;
}
