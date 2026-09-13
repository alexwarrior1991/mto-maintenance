package com.alejandro.mtomaintenance.infrastructure.stock;

import com.alejandro.mtomaintenance.application.dto.stock.StockMaterial;
import com.alejandro.mtomaintenance.application.dto.stock.StockReservation;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StockClientTest {

    private MockRestServiceServer server;
    private RestClientStockClient client;
    private io.github.resilience4j.circuitbreaker.CircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        // Circuito real con umbrales bajos: dos fallos seguidos lo abren.
        breaker = io.github.resilience4j.circuitbreaker.CircuitBreaker.of("stock", CircuitBreakerConfig.custom()
                .slidingWindowSize(2).minimumNumberOfCalls(2).failureRateThreshold(50).build());
        CircuitBreaker springBreaker = new CircuitBreaker() {
            @Override
            public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
                try {
                    return breaker.executeSupplier(toRun);
                } catch (Throwable throwable) {
                    return fallback.apply(throwable);
                }
            }
        };
        client = new RestClientStockClient(builder.baseUrl("http://stock").build(), springBreaker);
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
                .andExpect(jsonPath("$.materialId").value(materialId.toString()))
                .andExpect(jsonPath("$.quantity").value(2.5))
                .andRespond(withSuccess("""
                        {"id":"%s","material":{"id":"%s","code":"GA70"},"warehouse":{"id":"%s","code":"WH"},
                         "project":{"id":"%s","code":"EP-6"},"quantity":2.5,"status":"ACTIVE"}"""
                        .formatted(reservationId, materialId, warehouseId, projectId), MediaType.APPLICATION_JSON));

        StockReservation reservation = client.reserve(materialId, warehouseId, projectId, new BigDecimal("2.5"));

        assertEquals(reservationId, reservation.id());
        assertEquals(projectId, reservation.projectId());
        server.verify();
    }

    @Test
    void aBusinessRejectionFromStockBecomesAStockUnavailableExceptionWithTheReason() {
        UUID reservationId = UUID.randomUUID();
        server.expect(requestTo("http://stock/api/v1/inventory/reservations/" + reservationId + "/consume"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorCode\":\"STK-001\",\"message\":\"insufficient stock\"}"));

        StockUnavailableException exception = assertThrows(StockUnavailableException.class, () -> client.consume(reservationId));

        assertTrue(exception.getMessage().contains("409"));
        assertTrue(exception.getMessage().contains("insufficient stock"));
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
        assertEquals(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN, breaker.getState());

        // Tercera llamada: el servidor simulado no espera nada mas, asi que si llegara fallaria el test.
        StockUnavailableException fastFailure = assertThrows(StockUnavailableException.class, () -> client.release(reservationId));
        assertTrue(fastFailure.getMessage().contains("unavailable"));
        server.verify();
    }

    @Test
    void anUnknownMaterialIdIsEmptyRatherThanAnError() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://stock/api/v1/inventory/materials/" + id)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertTrue(client.findMaterialById(id).isEmpty());
    }
}
