package com.alejandro.mtomaintenance.configuration.rabbitmq;

import com.alejandro.mtomaintenance.infrastructure.messaging.rabbitmq.ResourcesRabbitMqNames;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Nombres del canal de {@code mto-resources} (personas, equipos, vehiculos e instrumentos), configurables por
 * entorno como los de datos maestros ({@link MasterDataRabbitProperties}): por defecto, el contrato real;
 * una cadena en blanco vale el valor por defecto. El interruptor del consumidor,
 * {@code app.rabbitmq.resources.listener-enabled}, lo lee el {@code @ConditionalOnProperty} de
 * {@link RabbitMqConfiguration}.
 */
@Validated
@ConfigurationProperties(prefix = "app.rabbitmq.resources")
public record ResourcesRabbitProperties(
        @NotBlank String exchange,
        @NotBlank String queue,
        @NotEmpty List<String> routingKeys,
        @NotBlank String deadLetterExchange,
        @NotBlank String deadLetterQueue,
        @NotBlank String deadLetterRoutingKey
) {

    public ResourcesRabbitProperties {
        exchange = defaultIfBlank(exchange, ResourcesRabbitMqNames.RESOURCES_EXCHANGE);
        queue = defaultIfBlank(queue, ResourcesRabbitMqNames.MAINTENANCE_RESOURCES_QUEUE);
        routingKeys = routingKeys == null || routingKeys.stream().allMatch(key -> key == null || key.isBlank())
                ? ResourcesRabbitMqNames.RESOURCES_ROUTING_KEYS
                : routingKeys.stream().filter(key -> key != null && !key.isBlank()).toList();
        deadLetterExchange = defaultIfBlank(deadLetterExchange, ResourcesRabbitMqNames.MAINTENANCE_RESOURCES_DEAD_LETTER_EXCHANGE);
        deadLetterQueue = defaultIfBlank(deadLetterQueue, ResourcesRabbitMqNames.MAINTENANCE_RESOURCES_DEAD_LETTER_QUEUE);
        deadLetterRoutingKey = defaultIfBlank(deadLetterRoutingKey, ResourcesRabbitMqNames.MAINTENANCE_RESOURCES_DEAD_LETTER_ROUTING_KEY);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
