package com.alejandro.mtomaintenance.application.dto.task;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Material usado en el perfil: se registra como linea de material de la orden atada a la tarea. */
public record TaskMaterialRequest(
        UUID materialId,
        @Size(max = 64) String materialCode,
        @NotNull UUID warehouseId,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal quantity,
        @Size(max = 32) String unit
) {
}
