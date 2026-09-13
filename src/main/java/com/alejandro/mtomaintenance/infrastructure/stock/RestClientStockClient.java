package com.alejandro.mtomaintenance.infrastructure.stock;

import com.alejandro.mtomaintenance.application.dto.stock.StockMaterial;
import com.alejandro.mtomaintenance.application.dto.stock.StockReservation;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.service.StockClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cliente de mto-stock sobre su API REST ({@code /api/v1/inventory}). Los DTOs que se leen son un
 * subconjunto de los de stock: solo las claves que este servicio usa, para que un campo nuevo alla
 * no rompa nada aqui.
 *
 * <p>Toda llamada pasa por el circuito 'stock': con stock caido, las llamadas fallan al instante en
 * vez de esperar un timeout cada una, y cualquier fallo -de red, de circuito o un rechazo de negocio
 * de stock- llega al servicio como {@link StockUnavailableException} con el motivo.</p>
 */
public class RestClientStockClient implements StockClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestClientStockClient.class);

    static final String MATERIALS = "/api/v1/inventory/materials";
    static final String PROJECTS = "/api/v1/inventory/projects";
    static final String RESERVATIONS = "/api/v1/inventory/reservations";
    static final String OUTPUTS = "/api/v1/inventory/movements/outputs";

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public RestClientStockClient(RestClient restClient, CircuitBreaker circuitBreaker) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<StockMaterial> findMaterialById(UUID materialId) {
        return call("find material " + materialId, () -> {
            try {
                MaterialPayload payload = restClient.get().uri(MATERIALS + "/{id}", materialId).retrieve().body(MaterialPayload.class);
                return Optional.ofNullable(payload).map(MaterialPayload::toMaterial);
            } catch (HttpClientErrorException.NotFound notFound) {
                return Optional.empty();
            }
        });
    }

    @Override
    public Optional<StockMaterial> findMaterialByCode(String code) {
        return call("find material by code " + code, () -> {
            PagePayload<MaterialPayload> page = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(MATERIALS).queryParam("code", code).queryParam("size", 50).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return page == null || page.content() == null ? Optional.<StockMaterial>empty() : page.content().stream()
                    .filter(material -> code.equalsIgnoreCase(material.code()))
                    .findFirst()
                    .map(MaterialPayload::toMaterial);
        });
    }

    @Override
    public Optional<UUID> findProjectIdByCode(String code) {
        // La lista de proyectos de stock no filtra por codigo: se pide una pagina y se busca aqui.
        return call("find project " + code, () -> {
            PagePayload<ProjectPayload> page = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(PROJECTS).queryParam("code", code).queryParam("size", 200).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return page == null || page.content() == null ? Optional.<UUID>empty() : page.content().stream()
                    .filter(project -> code.equalsIgnoreCase(project.code()))
                    .map(ProjectPayload::id)
                    .findFirst();
        });
    }

    @Override
    public StockReservation reserve(UUID materialId, UUID warehouseId, UUID projectId, BigDecimal quantity) {
        return call("reserve " + quantity + " of " + materialId, () -> {
            ReservationPayload payload = restClient.post()
                    .uri(RESERVATIONS)
                    .body(Map.of("materialId", materialId, "warehouseId", warehouseId, "projectId", projectId,
                            "quantity", quantity, "reservedAt", Instant.now()))
                    .retrieve()
                    .body(ReservationPayload.class);
            if (payload == null) {
                throw new StockUnavailableException("mto-stock returned an empty reservation");
            }
            return payload.toReservation();
        });
    }

    @Override
    public void consume(UUID reservationId) {
        call("consume reservation " + reservationId, () -> {
            restClient.post().uri(RESERVATIONS + "/{id}/consume", reservationId).retrieve().toBodilessEntity();
            return null;
        });
    }

    @Override
    public void release(UUID reservationId) {
        call("release reservation " + reservationId, () -> {
            restClient.post().uri(RESERVATIONS + "/{id}/release", reservationId).retrieve().toBodilessEntity();
            return null;
        });
    }

    @Override
    public void output(UUID materialId, UUID warehouseId, UUID projectId, BigDecimal quantity, String externalReference, String notes) {
        call("output " + quantity + " of " + materialId, () -> {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("materialId", materialId);
            body.put("warehouseId", warehouseId);
            body.put("projectId", projectId);
            body.put("quantity", quantity);
            body.put("occurredAt", Instant.now());
            body.put("externalReference", externalReference);
            body.put("notes", notes);
            restClient.post().uri(OUTPUTS).body(body).retrieve().toBodilessEntity();
            return null;
        });
    }

    private <T> T call(String operation, Supplier<T> action) {
        try {
            return circuitBreaker.run(action, throwable -> {
                throw translate(operation, throwable);
            });
        } catch (StockUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw translate(operation, exception);
        }
    }

    private static StockUnavailableException translate(String operation, Throwable throwable) {
        if (throwable instanceof StockUnavailableException unavailable) {
            return unavailable;
        }
        if (throwable instanceof RestClientResponseException response) {
            HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
            String body = response.getResponseBodyAsString();
            LOGGER.warn("mto-stock rejected '{}': status={}, body={}", operation, response.getStatusCode(), body);
            return new StockUnavailableException("mto-stock answered " + (status == null ? response.getStatusCode() : status)
                    + " to '" + operation + "'" + (body == null || body.isBlank() ? "" : ": " + body), response);
        }
        LOGGER.warn("mto-stock call '{}' failed: {}", operation, throwable.toString());
        return new StockUnavailableException("mto-stock is unavailable for '" + operation + "': " + throwable.getMessage(), throwable);
    }

    record MaterialPayload(UUID id, String code, String name, String unitOfMeasure, Boolean active) {
        StockMaterial toMaterial() {
            return new StockMaterial(id, code, name, unitOfMeasure, active);
        }
    }

    record ProjectPayload(UUID id, String code, String name, Boolean active) {
    }

    record ReservationPayload(UUID id, Reference material, Reference warehouse, Reference project, BigDecimal quantity, String status) {
        StockReservation toReservation() {
            return new StockReservation(id, material == null ? null : material.id(), warehouse == null ? null : warehouse.id(),
                    project == null ? null : project.id(), quantity, status);
        }
    }

    record Reference(UUID id, String code) {
    }

    record PagePayload<T>(List<T> content) {
    }
}
