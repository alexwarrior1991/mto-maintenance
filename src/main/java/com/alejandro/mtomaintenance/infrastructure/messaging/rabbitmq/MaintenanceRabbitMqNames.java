package com.alejandro.mtomaintenance.infrastructure.messaging.rabbitmq;

import java.util.Locale;

/**
 * Nombres del exchange propio de este servicio y de lo que se enruta por el.
 *
 * <p>Es un exchange distinto de {@code mto.master-data.exchange} a proposito: aquel es el contrato
 * de los datos maestros de {@code mto-configuration}, y lo que este servicio cuenta de si mismo
 * (ordenes, defectos, inspecciones, turnos, material, activos, preventivos) sale por aqui. Solo se
 * declara el exchange: la cola es de quien la consume ({@code mto-notification}), que la bindea
 * con {@code mto.maintenance.#} o con lo que le interese.</p>
 *
 * <p>La clave de enrutado y el {@code eventType} son contrato: el consumidor deriva de la clave el
 * tipo de actividad ({@code maintenance.<entidad>.<evento>}), asi que el formato no es cosmetico.</p>
 */
public final class MaintenanceRabbitMqNames {

    public static final String MAINTENANCE_EXCHANGE = "mto.maintenance.exchange";

    public static final String MAINTENANCE_ROUTING_PREFIX = "mto.maintenance";
    public static final String MAINTENANCE_ROUTING_PATTERN = "mto.maintenance.#";

    private static final String EVENT_TYPE_PREFIX = "MAINTENANCE";

    private MaintenanceRabbitMqNames() {
    }

    /** {@code mto.maintenance.<entidad>.<evento>}: lo que decide a que colas llega el mensaje. */
    public static String routingKey(String entityName, String eventName) {
        return MAINTENANCE_ROUTING_PREFIX + "." + normalize(entityName) + "." + normalize(eventName);
    }

    /** {@code MAINTENANCE_<ENTIDAD>_<EVENTO>}: el {@code eventType} del sobre y de la cabecera. */
    public static String eventType(String entityName, String eventName) {
        return EVENT_TYPE_PREFIX + "_" + constant(entityName) + "_" + constant(eventName);
    }

    private static String normalize(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("_", "-")
                .replace(" ", "-");
    }

    private static String constant(String value) {
        return normalize(value)
                .toUpperCase(Locale.ROOT)
                .replace("-", "_");
    }
}
