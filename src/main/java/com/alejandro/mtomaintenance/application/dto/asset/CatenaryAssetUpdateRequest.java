package com.alejandro.mtomaintenance.application.dto.asset;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.SectionInsulatorInstallation;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Modificacion de un activo. En los que vienen de datos maestros solo se aplican description,
 * enabled y preventiveIntervalDays; el resto se acepta solo con el valor que ya tiene (un formulario
 * reenvia el activo entero) y un cambio es 409. {@code enabled=false}
 * desactiva aqui (y lo conserva aunque lleguen eventos); {@code true} quita esa desactivacion, y es
 * 409 si el activo esta desactivado en mto-configuration.
 */
public record CatenaryAssetUpdateRequest(
        @Size(max = 255) String name,
        String description,
        Boolean enabled,
        @Positive Integer preventiveIntervalDays,
        Long executionPackageId,
        Long trackId,
        Long stationId,
        @Digits(integer = 9, fraction = 3) BigDecimal startKp,
        @Digits(integer = 9, fraction = 3) BigDecimal endKp,
        TrackKind trackKind,
        Long connectedTrackId,
        SectionInsulatorInstallation installationType,
        Long version
) {
    /** Desactivar pide ademas maintenance-delete, como el DELETE: es la misma decision. */
    public boolean isDisabling() {
        return Boolean.FALSE.equals(enabled);
    }
}
