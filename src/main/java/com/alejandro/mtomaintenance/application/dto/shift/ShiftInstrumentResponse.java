package com.alejandro.mtomaintenance.application.dto.shift;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un instrumento de un turno, tal como se sabe de el en mto-resources ({@code V22}).
 *
 * <p>Ejemplo, dentro de {@code MaintenanceShiftResponse.instruments} (por codigo):</p>
 * <pre>{@code
 * {"id": "…", "code": "EP-07", "name": "Earthing pole 7", "kind": "EARTHING_POLE", "calibrationExpiresOn": "2027-01-10"}
 * }</pre>
 *
 * @param calibrationExpiresOn hasta cuando vale su calibracion; {@code null} si no tiene una que caduque
 */
public record ShiftInstrumentResponse(UUID id, String code, String name, String kind, LocalDate calibrationExpiresOn) {
}
