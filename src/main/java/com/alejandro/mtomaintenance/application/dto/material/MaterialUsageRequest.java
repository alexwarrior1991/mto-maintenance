package com.alejandro.mtomaintenance.application.dto.material;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** materialId o materialCode: con codigo se resuelve el id contra mto-stock. */
public record MaterialUsageRequest(
        UUID materialId,
        @Size(max = 64) String materialCode,
        @NotNull UUID warehouseId,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal plannedQuantity,
        @Size(max = 32) String unit,
        UUID taskId,
        Boolean allowOverConsumption
) {
    @AssertTrue(message = "materialId or materialCode is required")
    public boolean hasMaterialReference() {
        return materialId != null || (materialCode != null && !materialCode.isBlank());
    }
}
