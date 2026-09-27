package com.alejandro.mtomaintenance.infrastructure.stock;

import com.alejandro.mtomaintenance.application.dto.stock.StockMaterial;
import com.alejandro.mtomaintenance.application.dto.stock.StockReservation;
import com.alejandro.mtomaintenance.application.exception.StockRejectedException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.configuration.stock.StockClientConfiguration;
import com.alejandro.mtomaintenance.configuration.stock.StockProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StockClientTest {

    private MockRestServiceServer server;
    private RestClientStockClient client;
    private CircuitBreakerRegistry registry;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        // El circuito de produccion, tal como lo configura StockClientConfiguration, con umbrales
        // bajos: dos fallos seguidos lo abren.
        registry = CircuitBreakerRegistry.ofDefaults();
        Resilience4JCircuitBreakerFactory factory = new Resilience4JCircuitBreakerFactory(registry, TimeLimiterRegistry.ofDefaults(), null);
        StockProperties properties = new StockProperties(true, "http://stock", "mto-stock",
                new StockProperties.CircuitBreaker(2, 2, 50, Duration.ofSeconds(30), Duration.ofSeconds(5)));
        new StockClientConfiguration().stockCircuitBreakerCustomizer(properties).customize(factory);
        client = new RestClientStockClient(builder.baseUrl("http://stock").build(), factory.create("stock"));
    }

    /** El estado del circuito que creo la primera llamada; pedirlo antes lo crearia con la configuracion por defecto. */
    private CircuitBreaker.State circuit() {
        return registry.find("stock").orElseThrow().getState();
    }

    @Test
    void materialsAreLookedUpByCodeThroughTheCatalogueSearch() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://stock/api/v1/inventory/materials?code=GA70&size=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"content":[{"id":"%s","code":"GA70","name":"Ga70 dropper","unitOfMeasure":"ud","active":true,"extra":1}],
                         "page":{"number":0}}""".formatted(id), MediaType.APPLICATION_JSON));

        Optional<StockMaterial> material = client.findMaterialByCode("GA70");

        assertTrue(material.isPresent());
        assertEquals(id, material.get().id());
        assertEquals("ud", material.get().unitOfMeasure());
        server.verify();
    }

    @Test
    void reservationsAreCreatedWithMaterialWarehouseProjectAndQuantity() {
        UUID materialId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        server.expect(requestTo("http://stock/api/v1/inventory/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "mto-maintenance:line-1:reserve:abc"))
                .andExpect(jsonPath("$.materialId").value(materialId.toString()))
                .andExpect(jsonPath("$.quantity").value(2.5))
                // Sin hora de este lado: cambiaria de un intento a otro, y stock no reconoceria el reintento.
                .andExpect(jsonPath("$.reservedAt").doesNotExist())
                .andRespond(withSuccess("""
                        {"id":"%s","material":{"id":"%s","code":"GA70"},"warehouse":{"id":"%s","code":"WH"},
                         "project":{"id":"%s","code":"EP-6"},"quantity":2.5,"status":"ACTIVE"}"""
                        .formatted(reservationId, materialId, warehouseId, projectId), MediaType.APPLICATION_JSON));

        StockReservation reservation = client.reserve(materialId, warehouseId, projectId, new BigDecimal("2.5"),
                "mto-maintenance:line-1:reserve:abc");

        assertEquals(reservationId, reservation.id());
        assertEquals(projectId, reservation.projectId());
        server.verify();
    }

    /**
     * La salida lleva su clave, y el mismo cuerpo en cada intento: stock solo reconoce el reintento
     * si llega igual, asi que nada de lo que viaja puede depender del momento en que se manda.
     */
    @Test
    void outputsCarryTheirIdempotencyKeyAndTheSameBodyOnEveryAttempt() {
        UUID materialId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        String body = """
                {"materialId":"%s","warehouseId":"%s","projectId":null,"quantity":1.5,
                 "externalReference":"MO-000001","notes":"Maintenance order MO-000001"}""".formatted(materialId, warehouseId);
        for (int attempt = 0; attempt < 2; attempt++) {
            server.expect(requestTo("http://stock/api/v1/inventory/movements/outputs"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Idempotency-Key", "mto-maintenance:line-1:output:abc"))
                    .andExpect(content().json(body, JsonCompareMode.STRICT))
                    .andRespond(withSuccess());
        }

        client.output(materialId, warehouseId, null, new BigDecimal("1.5"), "MO-000001", "Maintenance order MO-000001",
                "mto-maintenance:line-1:output:abc");
        client.output(materialId, warehouseId, null, new BigDecimal("1.5"), "MO-000001", "Maintenance order MO-000001",
                "mto-maintenance:line-1:output:abc");

        server.verify();
    }

    @Test
    void projectsAreFoundBySearchingTheirCodeAndPickingTheExactOne() {
        UUID ep6 = UUID.randomUUID();
        server.expect(requestTo("http://stock/api/v1/inventory/projects?search=EP-6&size=200&sort=code,asc"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"content":[{"id":"%s","code":"EP-6","name":"Package 6","active":true},
                                    {"id":"%s","code":"EP-60","name":"Package 60","active":true}],
                         "page":{"number":0}}""".formatted(ep6, UUID.randomUUID()), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://stock/api/v1/inventory/projects?search=EP-7&size=200&sort=code,asc"))
                .andRespond(withSuccess("""
                        {"content":[{"id":"%s","code":"EP-70","name":"Package 70","active":true}],"page":{"number":0}}"""
                        .formatted(UUID.randomUUID()), MediaType.APPLICATION_JSON));

        assertEquals(Optional.of(ep6), client.findProjectIdByCode("EP-6"));
        assertTrue(client.findProjectIdByCode("EP-7").isEmpty(), "EP-70 contains the text but is another package");
        server.verify();
    }

    @Test
    void aReservationIsReadAsStockHasItAndOneStockDoesNotKnowIsEmpty() {
        UUID reservationId = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        server.expect(requestTo("http://stock/api/v1/inventory/reservations/" + reservationId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"%s","material":{"id":"%s","code":"GA70"},"warehouse":{"id":"%s","code":"WH"},
                         "project":{"id":"%s","code":"EP-6"},"quantity":1.5,"status":"CONSUMED","consumedAt":"2026-09-26T10:00:00Z"}"""
                        .formatted(reservationId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://stock/api/v1/inventory/reservations/" + unknown))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorCode\":\"RES-404\",\"message\":\"Reservation not found\"}"));

        StockReservation reservation = client.findReservation(reservationId).orElseThrow();

        assertEquals(0, new BigDecimal("1.5").compareTo(reservation.quantity()));
        assertTrue(reservation.isConsumed());
        assertFalse(reservation.isActive());
        assertTrue(client.findReservation(unknown).isEmpty());
        server.verify();
    }

    @Test
    void aBusinessRejectionCarriesTheCodeOfStockAndDoesNotOpenTheCircuit() {
        UUID materialId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        server.expect(requestTo("http://stock/api/v1/inventory/reservations"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":409,\"errorCode\":\"STK-001\",\"message\":\"Insufficient available stock\",\"path\":\"/x\"}"));
        server.expect(requestTo("http://stock/api/v1/inventory/reservations/" + reservationId + "/consume"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorCode\":\"RES-001\",\"message\":\"Only active reservations can be changed\"}"));
        server.expect(requestTo("http://stock/api/v1/inventory/reservations/" + reservationId + "/release"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML).body("<html>not here</html>"));
        server.expect(requestTo("http://stock/api/v1/inventory/reservations/" + reservationId + "/release"))
                .andRespond(withSuccess());

        StockRejectedException shortage = assertThrows(StockRejectedException.class,
                () -> client.reserve(materialId, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("5"), "key-1"));
        assertEquals(409, shortage.getStatus());
        assertEquals("STK-001", shortage.getStockErrorCode());
        assertTrue(shortage.isInsufficientStock());
        assertTrue(shortage.getMessage().contains("409 STK-001: Insufficient available stock"), shortage.getMessage());

        StockRejectedException notActive = assertThrows(StockRejectedException.class, () -> client.consume(reservationId));
        assertEquals("RES-001", notActive.getStockErrorCode());
        assertFalse(notActive.isInsufficientStock());

        // Un cuerpo que no es el JSON de stock (un proxy, por ejemplo) sigue siendo un rechazo, sin codigo.
        StockRejectedException unreadable = assertThrows(StockRejectedException.class, () -> client.release(reservationId));
        assertNull(unreadable.getStockErrorCode());
        assertTrue(unreadable.getMessage().contains("<html>not here</html>"));

        // Tres rechazos seguidos con un circuito que abre con dos fallos: sigue cerrado, porque stock respondio.
        assertEquals(CircuitBreaker.State.CLOSED, circuit());
        client.release(reservationId);
        server.verify();
    }

    @Test
    void anOpenCircuitFailsFastWithoutCallingStock() {
        UUID reservationId = UUID.randomUUID();
        server.expect(requestTo("http://stock/api/v1/inventory/reservations/" + reservationId + "/release"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("http://stock/api/v1/inventory/reservations/" + reservationId + "/release"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(StockUnavailableException.class, () -> client.release(reservationId));
        assertThrows(StockUnavailableException.class, () -> client.release(reservationId));
        assertEquals(CircuitBreaker.State.OPEN, circuit());

        // Tercera llamada: el servidor simulado no espera nada mas, asi que si llegara fallaria el test.
        StockUnavailableException fastFailure = assertThrows(StockUnavailableException.class, () -> client.release(reservationId));
        assertTrue(fastFailure.getMessage().contains("unavailable"));
        server.verify();
    }

    @Test
    void theServiceAccountBeingRefusedIsAnOutageAndCountsForTheCircuit() {
        UUID reservationId = UUID.randomUUID();
        server.expect(requestTo("http://stock/api/v1/inventory/reservations/" + reservationId + "/release"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo("http://stock/api/v1/inventory/reservations/" + reservationId + "/release"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // 403 y 401 hablan de la cuenta de servicio, no de la reserva: se arreglan sin tocar la linea.
        assertInstanceOf(StockUnavailableException.class, assertThrows(RuntimeException.class, () -> client.release(reservationId)));
        assertInstanceOf(StockUnavailableException.class, assertThrows(RuntimeException.class, () -> client.release(reservationId)));
        assertEquals(CircuitBreaker.State.OPEN, circuit());
        server.verify();
    }

    /**
     * Las llamadas a stock salen del hilo del circuito o del reintento programado, nunca de una peticion
     * HTTP. El contexto real trae el gestor OAuth2 que registra Spring Security por defecto, que exige
     * una ({@code servletRequest cannot be null}): la cuenta de servicio tiene que autorizarse con el
     * suyo propio, y aqui se comprueba con ese contexto, no con el cliente montado a mano.
     */
    @Test
    void theServiceAccountTokenGoesOutWithoutAnHttpRequestInCourse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer stock = MockRestServiceServer.bindTo(builder).build();
        UUID materialId = UUID.randomUUID();
        stock.expect(requestTo("http://stock/api/v1/inventory/materials/" + materialId))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-account-token"))
                .andRespond(withSuccess("""
                        {"id":"%s","code":"GA70","name":"Ga70 dropper","unitOfMeasure":"ud","active":true}"""
                        .formatted(materialId), MediaType.APPLICATION_JSON));

        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OAuth2ClientAutoConfiguration.class,
                        OAuth2ClientWebSecurityAutoConfiguration.class))
                .withUserConfiguration(StockClientConfiguration.class, ResourceServerSecurity.class)
                .withBean(RestClient.Builder.class, () -> builder)
                .withBean(CircuitBreakerFactory.class, () -> new Resilience4JCircuitBreakerFactory(
                        CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(), null))
                .withPropertyValues(
                        "app.stock.enabled=true",
                        "app.stock.base-url=http://stock",
                        "app.stock.client-registration-id=mto-services",
                        "spring.security.oauth2.client.registration.mto-services.client-id=mto-maintenance-svc",
                        "spring.security.oauth2.client.registration.mto-services.client-secret=secret",
                        "spring.security.oauth2.client.registration.mto-services.authorization-grant-type=client_credentials",
                        "spring.security.oauth2.client.registration.mto-services.provider=keycloak",
                        "spring.security.oauth2.client.provider.keycloak.token-uri=http://auth/token")
                .run(context -> {
                    // Un token vigente de la cuenta de servicio, para no tener que pedirlo a Keycloak.
                    ClientRegistration registration = context.getBean(ClientRegistrationRepository.class)
                            .findByRegistrationId("mto-services");
                    OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                            "service-account-token", Instant.now(), Instant.now().plus(Duration.ofHours(1)));
                    context.getBean(OAuth2AuthorizedClientService.class).saveAuthorizedClient(
                            new OAuth2AuthorizedClient(registration, "mto-maintenance", token),
                            new TestingAuthenticationToken("mto-maintenance", null));

                    assertTrue(context.getBean(RestClientStockClient.class).findMaterialById(materialId).isPresent());
                    stock.verify();
                });
    }

    /** Como la del servicio: {@code @EnableWebSecurity} y su propia cadena, que es lo que trae el gestor por defecto. */
    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class ResourceServerSecurity {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated()).build();
        }
    }

    @Test
    void onlyA4xxOtherThanTheCredentialsTimeoutsAndThrottlingIsARejection() {
        assertTrue(RestClientStockClient.isRejection(HttpClientErrorException.create(
                HttpStatus.CONFLICT, "Conflict", null, null, null)));
        assertTrue(RestClientStockClient.isRejection(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null, null, null)));
        for (HttpStatus status : new HttpStatus[] {HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.REQUEST_TIMEOUT,
                HttpStatus.TOO_MANY_REQUESTS}) {
            assertFalse(RestClientStockClient.isRejection(HttpClientErrorException.create(
                    status, status.getReasonPhrase(), null, null, null)), status.toString());
        }
        assertFalse(RestClientStockClient.isRejection(HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", null, null, null)));
        assertFalse(RestClientStockClient.isRejection(new SocketTimeoutException("read timed out")));
    }

    @Test
    void anUnknownMaterialIdIsEmptyRatherThanAnError() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://stock/api/v1/inventory/materials/" + id)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertTrue(client.findMaterialById(id).isEmpty());
    }
}
