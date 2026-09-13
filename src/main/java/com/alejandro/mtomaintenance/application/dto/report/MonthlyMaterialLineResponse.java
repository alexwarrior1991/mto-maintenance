package com.alejandro.mtomaintenance.application.dto.report;

import java.math.BigDecimal;
import java.util.UUID;

public record MonthlyMaterialLineResponse(UUID materialId, String materialCode, String unit, BigDecimal consumedQuantity) {
}
