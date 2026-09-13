package com.alejandro.mtomaintenance.configuration.stock;

import com.alejandro.mtomaintenance.infrastructure.stock.RestClientStockClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia mto-stock, activo con {@code app.stock.enabled=true} (el valor por defecto).
 *
 * <p>La autenticacion es una cuenta de servicio (client_credentials) contra el mismo Keycloak: el
 * token representa a este servicio, no a una persona. Se usa el manager basado en
 * {@link OAuth2AuthorizedClientService} y no el ligado a la peticion HTTP porque las llamadas tambien
 * salen de hilos sin peticion en curso (el consumidor de RabbitMQ, por ejemplo). Sin registro de
 * cliente configurado, las llamadas salen sin token y stock las rechaza con 401: la linea de
 * material queda FAILED con ese motivo, que es lo que se quiere ver.</p>
 */
@Configuration
@EnableConfigurationProperties(StockProperties.class)
@ConditionalOnProperty(prefix = "app.stock", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StockClientConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockClientConfiguration.class);

    static final String STOCK_CIRCUIT_BREAKER = "stock";

    /** Principal nominal de la cuenta de servicio: solo identifica la autorizacion en el servicio de clientes. */
    static final String SERVICE_PRINCIPAL = "mto-maintenance";

    @Bean
    @ConditionalOnBean(ClientRegistrationRepository.class)
    @ConditionalOnMissingBean(OAuth2AuthorizedClientManager.class)
    public OAuth2AuthorizedClientManager stockAuthorizedClientManager(ClientRegistrationRepository clientRegistrations,
                                                                      OAuth2AuthorizedClientService authorizedClients) {
        return new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrations, authorizedClients);
    }

    @Bean
    public RestClient stockRestClient(RestClient.Builder builder, StockProperties properties,
                                      ObjectProvider<OAuth2AuthorizedClientManager> authorizedClientManager) {
        RestClient.Builder stockBuilder = builder.clone().baseUrl(properties.baseUrl());
        OAuth2AuthorizedClientManager manager = authorizedClientManager.getIfAvailable();
        if (manager == null) {
            LOGGER.warn("No OAuth2 client registration is configured: calls to mto-stock will go out without a bearer token");
        } else {
            stockBuilder.requestInterceptor((request, body, execution) -> {
                OAuth2AuthorizedClient client = manager.authorize(OAuth2AuthorizeRequest
                        .withClientRegistrationId(properties.clientRegistrationId())
                        .principal(SERVICE_PRINCIPAL)
                        .build());
                if (client != null) {
                    request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
                }
                return execution.execute(request, body);
            });
        }
        return stockBuilder.build();
    }

    /**
     * Umbrales del circuito 'stock'. Spring Cloud CircuitBreaker no lee las propiedades
     * resilience4j.* del starter de Boot (no esta en el classpath), asi que se configura aqui con
     * los valores de app.stock.circuit-breaker.
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> stockCircuitBreakerCustomizer(StockProperties properties) {
        StockProperties.CircuitBreaker settings = properties.circuitBreaker();
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(settings.slidingWindowSize())
                        .minimumNumberOfCalls(settings.minimumNumberOfCalls())
                        .failureRateThreshold(settings.failureRateThreshold())
                        .waitDurationInOpenState(settings.waitDurationInOpenState())
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(settings.timeout())
                        .build()), STOCK_CIRCUIT_BREAKER);
    }

    @Bean
    public RestClientStockClient stockClient(RestClient stockRestClient, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        return new RestClientStockClient(stockRestClient, circuitBreakerFactory.create(STOCK_CIRCUIT_BREAKER));
    }
}
