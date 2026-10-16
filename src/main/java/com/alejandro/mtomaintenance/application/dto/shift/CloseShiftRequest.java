package com.alejandro.mtomaintenance.application.dto.shift;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.List;

/**
 * Sin actualEnd se toma el instante actual; sin netWorkMinutes se calcula desde el corte de tension (o el inicio).
 * {@code parkings} dice donde se ha podido dejar cada vehiculo del turno (no hay base fija); sin el, vale
 * el {@code parkingPlace} del turno. mto-resources lo lee del evento {@code shift.closed}.
 *
 * <p>Ejemplo, {@code POST /shifts/{id}/close}:</p>
 * <pre>{@code
 * {"actualEnd": "2026-01-28T02:30:00Z", "voltageCutoffAt": "2026-01-27T21:40:00Z", "netWorkMinutes": 290,
 *  "observations": "Corte de tension retrasado por trafico",
 *  "parkings": [{"vehicleId": "…(DR-02)", "trackId": 31, "kp": 4.250}]}
 * }</pre>
 */
public record CloseShiftRequest(Instant actualEnd, Instant voltageCutoffAt, @PositiveOrZero Integer netWorkMinutes, String observations,
                                List<@Valid @NotNull VehicleParkingRequest> parkings) {

    /** Sin aparcamientos: vale el {@code parkingPlace} del turno, como antes. */
    public CloseShiftRequest(Instant actualEnd, Instant voltageCutoffAt, Integer netWorkMinutes, String observations) {
        this(actualEnd, voltageCutoffAt, netWorkMinutes, observations, null);
    }
}
