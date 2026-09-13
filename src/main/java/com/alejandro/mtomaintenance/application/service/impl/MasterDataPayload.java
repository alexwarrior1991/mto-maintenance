package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.MasterDataChangedEvent;
import com.alejandro.mtomaintenance.application.exception.ValidationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Lectura tolerante del payload de un evento de datos maestros. Una clave ausente o con un tipo
 * inesperado se lee como {@code null}: el emisor puede anadir o quitar claves sin avisar, y un
 * evento legitimo no debe ir a la DLQ por un campo que aqui no se usa.
 */
final class MasterDataPayload {

    private static final int MAX_SOURCE_ENTITY_ID_LENGTH = 100;

    private final Map<String, Object> values;

    private MasterDataPayload(Map<String, Object> values) {
        this.values = values == null ? Map.of() : values;
    }

    static MasterDataPayload of(MasterDataChangedEvent event) {
        return new MasterDataPayload(event.values());
    }

    static MasterDataPayload of(Map<String, Object> values) {
        return new MasterDataPayload(values);
    }

    /** El entityId del sobre, obligatorio: sin el no hay activo que sincronizar. */
    static String sourceEntityId(MasterDataChangedEvent event, String entityName) {
        String entityId = event.entityId();
        if (entityId == null || entityId.isBlank()) {
            throw new ValidationException(entityName + " event has no entityId, so there is no asset to synchronize");
        }
        if (entityId.length() > MAX_SOURCE_ENTITY_ID_LENGTH) {
            throw new ValidationException(entityName + " entityId is longer than " + MAX_SOURCE_ENTITY_ID_LENGTH + " characters");
        }
        return entityId.trim();
    }

    String string(String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString().trim();
    }

    Long longValue(String key) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    BigDecimal decimal(String key) {
        Object value = values.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** Un booleano ausente se lee como el valor por defecto que se pase. */
    boolean bool(String key, boolean defaultValue) {
        Object value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        return Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    MasterDataPayload nested(String key) {
        Object value = values.get(key);
        return value instanceof Map<?, ?> map ? new MasterDataPayload((Map<String, Object>) map) : new MasterDataPayload(Map.of());
    }

    boolean has(String key) {
        return values.get(key) != null;
    }

    /** Codigos de una lista de objetos {id, code}, unidos por espacio: el snapshot de seccionamiento. */
    @SuppressWarnings("unchecked")
    String joinedCodes(String key) {
        Object value = values.get(key);
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        String joined = list.stream()
                .filter(Map.class::isInstance)
                .map(item -> ((Map<String, Object>) item).get("code"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.joining(" "));
        return joined.isBlank() ? null : joined;
    }
}
