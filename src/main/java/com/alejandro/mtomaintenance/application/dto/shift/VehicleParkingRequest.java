package com.alejandro.mtomaintenance.application.dto.shift;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Donde se ha podido dejar un vehiculo del turno al cerrarlo: un sitio del catalogo de mto-resources
 * ({@code parkingSiteId}), una via y un kp, o una descripcion. Uno de solo via lleva via o sitio (409
 * {@code SHF-001} si no): no se deja una dresina en un aparcamiento de carretera.
 *
 * <p>Ejemplo, dentro de {@code POST /shifts/{id}/close}:</p>
 * <pre>{@code
 * {"vehicleId": "…(DR-02)", "trackId": 31, "kp": 4.250, "description": "Siding north of Herzliya"}
 * }</pre>
 */
public record VehicleParkingRequest(
        @NotNull UUID vehicleId,
        UUID parkingSiteId,
        Long trackId,
        @Digits(integer = 9, fraction = 3) BigDecimal kp,
        @Size(max = 500) String description
) {
}
