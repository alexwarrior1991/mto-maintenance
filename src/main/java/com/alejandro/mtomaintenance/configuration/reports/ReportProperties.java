package com.alejandro.mtomaintenance.configuration.reports;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * Los informes ({@code app.reports.*}).
 *
 * <p>{@code timeZone} es la zona en la que se leen los instantes de un informe, y vale para las dos
 * mitades a la vez: lo que se imprime en el .xlsx y el PDF (la hora de inicio de un turno, la de
 * cada perfil) y los cortes que cuentan (el mes del informe mensual, el dia de una inspeccion en el
 * de avance). Por eso es una sola propiedad y la leen los dos sitios: con dos, se podria mover una
 * mitad sin la otra y un perfil completado a la 01:30 saldria en un dia distinto del contador que
 * lo cuenta, en el mismo fichero y sin que nada fallara.</p>
 *
 * <p>Se valida al arrancar: una zona mal escrita para el despliegue en vez de imprimir horas en otra.
 * Los instantes del JSON no cambian: siguen siendo UTC con su {@code Z}.</p>
 *
 * @param timeZone identificador IANA ({@code Asia/Jerusalem} por defecto, donde trabajan los equipos)
 */
@ConfigurationProperties(prefix = "app.reports")
public record ReportProperties(String timeZone) {

    public static final String DEFAULT_TIME_ZONE = "Asia/Jerusalem";

    public ReportProperties {
        timeZone = timeZone == null || timeZone.isBlank() ? DEFAULT_TIME_ZONE : timeZone.trim();
        try {
            ZoneId.of(timeZone);
        } catch (DateTimeException invalid) {
            throw new IllegalArgumentException("app.reports.time-zone '" + timeZone
                    + "' is not a time zone: use an IANA id such as Asia/Jerusalem or Europe/Madrid", invalid);
        }
    }

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }
}
