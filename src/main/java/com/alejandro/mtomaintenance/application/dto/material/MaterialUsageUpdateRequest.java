package com.alejandro.mtomaintenance.application.dto.material;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record MaterialUsageUpdateRequest(
        @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal plannedQuantity,
        @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal consumedQuantity,
        Boolean allowOverConsumption
) {
}
