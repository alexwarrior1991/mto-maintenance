package com.alejandro.mtomaintenance.configuration.stock;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Integracion con mto-stock ({@code app.stock.*}).
 *
 * @param enabled              con false el cliente es un NoOp y la aplicacion arranca sin stock ni cuenta de servicio
 * @param baseUrl              raiz de mto-stock (en local el 8080; en compose, el nombre del servicio)
 * @param clientRegistrationId registro de spring.security.oauth2.client con el que se pide el token de servicio
 * @param circuitBreaker       umbrales del circuito que aisla a este servicio de un stock caido
 */
@Validated
@ConfigurationProperties(prefix = "app.stock")
public record StockProperties(
        boolean enabled,
        @NotBlank String baseUrl,
        @NotBlank String clientRegistrationId,
        CircuitBreaker circuitBreaker
) {

    public StockProperties {
        if (circuitBreaker == null) {
            circuitBreaker = new CircuitBreaker(10, 5, 50, Duration.ofSeconds(30), Duration.ofSeconds(10));
        }
    }

    /**
     * @param slidingWindowSize     llamadas que se miran para calcular la tasa de fallo
     * @param minimumNumberOfCalls  por debajo de esto no se calcula la tasa (evita abrir por los dos primeros errores)
     * @param failureRateThreshold  porcentaje de fallos a partir del cual el circuito abre
     * @param waitDurationInOpenState tiempo que el circuito permanece abierto antes de probar de nuevo
     * @param timeout               tiempo maximo de una llamada antes de contarla como fallo
     */
    public record CircuitBreaker(
            int slidingWindowSize,
            int minimumNumberOfCalls,
            int failureRateThreshold,
            Duration waitDurationInOpenState,
            Duration timeout
    ) {
    }
}
