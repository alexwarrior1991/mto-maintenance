package com.alejandro.mtomaintenance.application.dto.common;

/**
 * Response DTO describing pagination metadata for collection resources.
 *
 * <p>Ejemplo, el {@code page} de {@code GET /orders?page=0&size=20} con una sola orden:</p>
 * <pre>{@code
 * {"number": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true}
 * }</pre>
 */
public record PageMetadataResponse(
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}