package com.alejandro.mtomaintenance.application.dto.shift;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Un vehiculo de un turno: lo que se sabe de el en mto-resources y donde se dejo al cerrar.
 *
 * <p>Ejemplo, dentro de {@code MaintenanceShiftResponse.vehicles}:</p>
 * <pre>{@code
 * {"vehicleId": "…", "code": "DR-02", "name": "Tower car 2", "mobility": "RAIL_ONLY", "category": "CATENARY_TOWER_CAR",
 *  "available": true, "parking": {"siteId": null, "trackId": 31, "kp": 4.250, "description": null}}
 * }</pre>
 */
public record ShiftVehicleResponse(UUID vehicleId, String code, String name, String mobility, String category, Boolean available,
                                   Parking parking) {

    /** {@code null} en la respuesta mientras el turno no se cierre con su aparcamiento. */
    public record Parking(UUID siteId, Long trackId, BigDecimal kp, String description) {
    }
}
