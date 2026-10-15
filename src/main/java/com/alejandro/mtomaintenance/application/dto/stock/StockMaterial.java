package com.alejandro.mtomaintenance.application.dto.stock;

import java.util.UUID;

/**
 * Lo que este servicio necesita saber de un material de mto-stock.
 *
 * <p>Ejemplo, lo que {@code RestClientStockClient.findMaterialById} lee de {@code GET /api/v1/inventory/materials/{id}}
 * de mto-stock:</p>
 * <pre>{@code
 * {"id": "…", "code": "DROPPER-STD", "name": "Pendola estandar", "unitOfMeasure": "UNIT", "active": true}
 * }</pre>
 */
public record StockMaterial(UUID id, String code, String name, String unitOfMeasure, Boolean active) {
}
