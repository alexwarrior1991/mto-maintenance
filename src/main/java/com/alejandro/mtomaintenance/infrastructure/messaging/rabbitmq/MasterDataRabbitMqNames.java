package com.alejandro.mtomaintenance.infrastructure.messaging.rabbitmq;

/**
 * Nombres AMQP del canal de datos maestros.
 *
 * <p>El exchange y el patron de enrutado son contrato de {@code mto-configuration}; la cola, su DLX
 * y su DLQ son de este servicio, que es quien la consume. Los valores por defecto de
 * {@code app.rabbitmq.master-data.*} salen de aqui.</p>
 */
public final class MasterDataRabbitMqNames {

    public static final String MASTER_DATA_EXCHANGE = "mto.master-data.exchange";

    public static final String MASTER_DATA_ROUTING_PATTERN = "mto.master-data.#";

    public static final String MAINTENANCE_MASTER_DATA_QUEUE = "mto.maintenance.master-data.queue";

    public static final String MAINTENANCE_MASTER_DATA_DEAD_LETTER_EXCHANGE = MAINTENANCE_MASTER_DATA_QUEUE + ".dlx";

    public static final String MAINTENANCE_MASTER_DATA_DEAD_LETTER_QUEUE = MAINTENANCE_MASTER_DATA_QUEUE + ".dlq";

    public static final String MAINTENANCE_MASTER_DATA_DEAD_LETTER_ROUTING_KEY = MAINTENANCE_MASTER_DATA_QUEUE + ".dlq";

    public static final String ARG_DEAD_LETTER_EXCHANGE = "x-dead-letter-exchange";

    public static final String ARG_DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";

    private MasterDataRabbitMqNames() {
    }
}
