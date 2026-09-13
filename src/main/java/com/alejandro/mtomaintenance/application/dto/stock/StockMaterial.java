package com.alejandro.mtomaintenance.application.dto.stock;

import java.util.UUID;

/** Lo que este servicio necesita saber de un material de mto-stock. */
public record StockMaterial(UUID id, String code, String name, String unitOfMeasure, Boolean active) {
}
