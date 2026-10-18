package com.alejandro.mtomaintenance.infrastructure.messaging.rabbitmq;

import java.util.List;

/**
 * Nombres AMQP del canal de {@code mto-resources}: su exchange y sus claves son contrato suyo; la cola,
 * su DLX y su DLQ son de este servicio, que es quien la consume. Los valores por defecto de
 * {@code app.rabbitmq.resources.*} salen de aqui.
 */
public final class ResourcesRabbitMqNames {

    public static final String RESOURCES_SOURCE_SERVICE = "mto-resources";

    public static final String RESOURCES_EXCHANGE = "mto.resources.exchange";

    /**
     * Solo lo que un turno necesita: personas, equipos, vehiculos e instrumentos ({@code V23}). Las
     * ubicaciones y los avisos de caducidad ({@code qualification.*}, {@code vehicle-document.*},
     * {@code calibration.*}) no llegan aqui: la fecha la mira el turno sobre la instantanea.
     */
    public static final List<String> RESOURCES_ROUTING_KEYS = List.of(
            "mto.resources.person.*", "mto.resources.team.*", "mto.resources.vehicle.*", "mto.resources.instrument.*");

    public static final String MAINTENANCE_RESOURCES_QUEUE = "mto.maintenance.resources.queue";

    public static final String MAINTENANCE_RESOURCES_DEAD_LETTER_EXCHANGE = MAINTENANCE_RESOURCES_QUEUE + ".dlx";

    public static final String MAINTENANCE_RESOURCES_DEAD_LETTER_QUEUE = MAINTENANCE_RESOURCES_QUEUE + ".dlq";

    public static final String MAINTENANCE_RESOURCES_DEAD_LETTER_ROUTING_KEY = MAINTENANCE_RESOURCES_QUEUE + ".dlq";

    private ResourcesRabbitMqNames() {
    }
}
