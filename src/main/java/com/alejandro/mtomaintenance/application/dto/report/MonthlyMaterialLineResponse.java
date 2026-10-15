package com.alejandro.mtomaintenance.application.dto.report;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Material consumido en el mes por las ordenes completadas, sumado por material.
 *
 * <p>Ejemplo, dentro de {@code MonthlyReportResponse.materials}:</p>
 * <pre>{@code
 * {"materialId": "…(DROPPER-STD)", "materialCode": "DROPPER-STD", "unit": "UNIT", "consumedQuantity": 1.000000}
 * }</pre>
 */
public record MonthlyMaterialLineResponse(UUID materialId, String materialCode, String unit, BigDecimal consumedQuantity) {
}
