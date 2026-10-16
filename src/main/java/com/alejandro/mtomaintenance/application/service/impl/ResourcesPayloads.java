package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.exception.ValidationException;

import java.util.UUID;

/** Lo que se lee de un evento de mto-resources y {@link MasterDataPayload} no sabe leer: sus ids. */
final class ResourcesPayloads {

    private ResourcesPayloads() {
    }

    /** El id de la entidad del evento; sin el, el mensaje no tiene arreglo y va a la DLQ. */
    static UUID uuid(String value, String entity) {
        UUID id = uuidOrNull(value);
        if (id == null) {
            throw new ValidationException("The " + entity + " event of mto-resources has no valid entityId: " + value);
        }
        return id;
    }

    static UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    static Long longOrNull(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException invalid) {
                return null;
            }
        }
        return null;
    }
}
