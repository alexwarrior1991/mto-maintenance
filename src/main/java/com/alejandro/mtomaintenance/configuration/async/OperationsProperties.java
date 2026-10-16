package com.alejandro.mtomaintenance.configuration.async;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Las operaciones de fondo ({@code app.operations.*}).
 *
 * @param defaultWait    lo que espera una peticion con {@code Prefer: respond-async} y sin {@code wait}
 * @param maxWait        el tope de {@code Prefer: wait=N}. Por debajo del {@code read-timeout} del gateway
 *                       (15 s): una espera mas larga la cortaria el gateway con un 504 y el cliente no
 *                       sabria la operacion
 * @param maxActive      operaciones vivas (en cola o en marcha) en todo el despliegue. Cada una en marcha
 *                       retiene una conexion de Hikari: el tope se elige con el pool a la vista
 * @param heartbeat      cada cuanto late una operacion viva
 * @param staleAfter     sin latido en este tiempo, una viva se da por abandonada
 * @param pollInitial    primera espera de una en cola antes de volver a mirar si es su turno
 * @param pollMax        la espera mas larga entre miradas (crece al doble desde {@code pollInitial})
 * @param retention      cuanto se guarda una terminada
 * @param purgeBatchSize filas que borra cada pasada de la purga
 * @param schedulingEnabled latido, abandono y purga programados; los tests lo apagan y los llaman a mano
 */
@ConfigurationProperties(prefix = "app.operations")
public record OperationsProperties(
        Duration defaultWait,
        Duration maxWait,
        int maxActive,
        Duration heartbeat,
        Duration staleAfter,
        Duration pollInitial,
        Duration pollMax,
        Duration retention,
        int purgeBatchSize,
        boolean schedulingEnabled
) {

    public OperationsProperties {
        defaultWait = defaultWait == null ? Duration.ofSeconds(5) : defaultWait;
        maxWait = maxWait == null ? Duration.ofSeconds(10) : maxWait;
        maxActive = maxActive <= 0 ? 8 : maxActive;
        heartbeat = heartbeat == null ? Duration.ofSeconds(5) : heartbeat;
        staleAfter = staleAfter == null ? Duration.ofMinutes(1) : staleAfter;
        pollInitial = pollInitial == null ? Duration.ofMillis(200) : pollInitial;
        pollMax = pollMax == null ? Duration.ofSeconds(2) : pollMax;
        retention = retention == null ? Duration.ofDays(7) : retention;
        purgeBatchSize = purgeBatchSize <= 0 ? 500 : purgeBatchSize;
    }

    /** {@code Prefer: wait=N} acotado a {@link #maxWait}; sin {@code wait}, {@link #defaultWait}. */
    public Duration effectiveWait(Duration requested) {
        if (requested == null) {
            return defaultWait;
        }
        if (requested.isNegative()) {
            return Duration.ZERO;
        }
        return requested.compareTo(maxWait) > 0 ? maxWait : requested;
    }
}
