package com.alejandro.mtomaintenance.application.dto.inspection;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CheckItemResult;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record CheckItemUpdateRequest(
        @Digits(integer = 9, fraction = 3) BigDecimal measuredValue,
        Boolean adjusted,
        @Digits(integer = 9, fraction = 3) BigDecimal valueAfterAdjustment,
        CheckItemResult itemResult,
        String notes
) {
}
