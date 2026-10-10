package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.export.ReportValue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * El momento en que se genera un informe y la zona en la que se leen sus instantes.
 *
 * <p>Es el unico sitio donde un instante pasa a fecha y hora de un informe, y por eso los
 * exportadores reciben fechas ya situadas y no eligen zona: si eligieran, el .xlsx y el PDF del
 * mismo turno podrian no coincidir. La zona sale de {@code app.reports.time-zone}, la misma que usa
 * {@code MaintenanceReportServiceImpl} para cortar los meses.</p>
 */
record ReportTime(Instant generatedAt, ZoneId zone) {

    ReportTime {
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(zone, "zone");
    }

    LocalDateTime at(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, zone);
    }

    ReportValue timestamp(Instant instant) {
        return ReportValue.timestamp(at(instant));
    }

    LocalDate day(Instant instant) {
        return instant == null ? null : LocalDate.ofInstant(instant, zone);
    }

    LocalDateTime generated() {
        return at(generatedAt);
    }

    LocalDate today() {
        return day(generatedAt);
    }
}
