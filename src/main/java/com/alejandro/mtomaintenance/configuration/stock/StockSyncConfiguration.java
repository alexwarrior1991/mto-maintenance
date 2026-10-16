package com.alejandro.mtomaintenance.configuration.stock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registra {@link StockSyncProperties} siempre, este stock encendido o no: el sincronizador de lineas
 * existe en los dos casos (con el cliente NoOp no hace nada), a diferencia de {@code StockProperties},
 * que solo existe con el cliente HTTP.
 */
@Configuration
@EnableConfigurationProperties(StockSyncProperties.class)
public class StockSyncConfiguration {
}
