package com.alejandro.mtomaintenance.application.dto.history;

import java.time.Instant;
import java.util.UUID;

public record StatusHistoryResponse(
        UUID id,
        String previousStatus,
        String newStatus,
        Instant changedAt,
        String changedBy,
        String comment
) {
}
