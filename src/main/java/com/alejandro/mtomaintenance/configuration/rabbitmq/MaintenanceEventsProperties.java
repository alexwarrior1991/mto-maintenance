package com.alejandro.mtomaintenance.configuration.rabbitmq;

import com.alejandro.mtomaintenance.infrastructure.messaging.rabbitmq.MaintenanceRabbitMqNames;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Name of the exchange this service publishes its own events to, configurable per environment.
 *
 * <p>Como {@link MasterDataRabbitProperties}: el valor por defecto es el contrato
 * ({@code mto.maintenance.exchange}), y una cadena en blanco cuenta como no configurada en vez de
 * arrancar contra un exchange llamado {@code ""}. Solo el exchange: las claves de enrutado salen de
 * {@link MaintenanceRabbitMqNames} y la cola es de quien la consume.</p>
 */
@Validated
@ConfigurationProperties(prefix = "app.rabbitmq.events")
public record MaintenanceEventsProperties(@NotBlank String exchange) {

    public MaintenanceEventsProperties {
        exchange = exchange == null || exchange.isBlank() ? MaintenanceRabbitMqNames.MAINTENANCE_EXCHANGE : exchange;
    }
}
