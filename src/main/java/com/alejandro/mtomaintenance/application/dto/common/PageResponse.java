package com.alejandro.mtomaintenance.application.dto.common;

import java.util.List;

/**
 * Generic response DTO for paginated API results.
 *
 * <p>Ejemplo, lo que devuelve {@code GET /orders?page=0&size=20}:</p>
 * <pre>{@code
 * {"content": [{"id": "…", "code": "MO-000001", "status": "PLANNED", …}],
 *  "page": {"number": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true}}
 * }</pre>
 */
public record PageResponse<T>(
        List<T> content,
        PageMetadataResponse page
) {
}