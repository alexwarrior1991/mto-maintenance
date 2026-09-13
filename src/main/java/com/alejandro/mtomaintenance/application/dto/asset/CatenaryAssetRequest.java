package com.alejandro.mtomaintenance.application.dto.asset;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Alta de un tramo de via (TRACK_SECTION). El resto de tipos nace de los eventos de mto-configuration. */
public record CatenaryAssetRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        String description,
        Long executionPackageId,
        @NotNull Long trackId,
        Long stationId,
        @NotNull @Digits(integer = 9, fraction = 3) BigDecimal startKp,
        @NotNull @Digits(integer = 9, fraction = 3) BigDecimal endKp,
        @NotNull TrackKind trackKind,
        @Positive Integer preventiveIntervalDays
) {
    @AssertTrue(message = "startKp must be before endKp")
    public boolean hasValidRange() {
        return startKp == null || endKp == null || startKp.compareTo(endKp) < 0;
    }
}
