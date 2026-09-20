package com.alejandro.mtomaintenance.application.dto.asset;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.SectionInsulatorInstallation;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Modificacion de un activo. En los que vienen de datos maestros solo se aplican description,
 * enabled y preventiveIntervalDays; el resto de campos se rechaza con 409.
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
        SectionInsulatorInstallation installationType
) {
    /**
     * Verdadero si la peticion toca algo que en un activo de datos maestros es de solo lectura.
     *
     * <p>Las agujas no aparecen aqui porque no aparecen en la peticion: las escribe el manejador de
     * datos maestros y no hay forma de mandarlas por API, asi que no hay nada que rechazar.
     */
    public boolean touchesIdentity() {
        return name != null || executionPackageId != null || trackId != null || stationId != null
                || startKp != null || endKp != null || trackKind != null
                || connectedTrackId != null || installationType != null;
    }
}
