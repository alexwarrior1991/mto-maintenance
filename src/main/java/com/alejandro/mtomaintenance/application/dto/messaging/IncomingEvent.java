package com.alejandro.mtomaintenance.application.dto.messaging;

import java.util.Map;

/**
 * El {@code data} de un evento propio de otro servicio del dominio, tal como llega: la misma forma que
 * {@link DomainEvent}, pero tolerante. No rechaza nada al construirse, porque lo que se lee no se
 * elige: lo comprueba el consumidor, que manda a la DLQ lo que no trae entidad ni evento.
 *
 * <p>Ejemplo, el {@code data} de {@code team.updated} de {@code mto-resources}:</p>
 * <pre>{@code
 * {"entityName": "team", "entityId": "…", "eventName": "updated",
 *  "values": {"code": "A", "discipline": "MAINTENANCE", "leader": {"personId": "…", "username": "equipo.jefe"}, "members": […], …}}
 * }</pre>
 */
public record IncomingEvent(
        String entityName,
        String entityId,
        String eventName,
        Map<String, Object> values
) {

    public IncomingEvent {
        values = values == null ? Map.of() : values;
    }
}
