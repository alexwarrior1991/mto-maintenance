package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.exception.ValidationException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Lo que se lee de un evento de mto-resources y {@link MasterDataPayload} no sabe leer: sus ids, sus
 * fechas de caducidad y las listas que se guardan como JSON en la instantanea.
 */
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

    /**
     * Una fecha ISO ({@code 2027-03-31}) o {@code null} si no viene. Una que no se entiende no se lee como
     * «no caduca», que dejaria entrar en un turno lo caducado: el evento falla y acaba en la DLQ.
     */
    static LocalDate dateOrNull(MasterDataPayload values, String key, String what) {
        String text = values.string(key);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException invalid) {
            throw new ValidationException("The " + what + " event of mto-resources has a " + key + " that is not a date: " + text);
        }
    }

    /**
     * La lista de objetos de {@code key}, cada uno reducido a lo que se guarda por {@code item} (que
     * devuelve {@code null} para saltarse uno que no sirve), como JSON para la columna {@code jsonb}.
     * {@code null} si la clave no viene o no es una lista: un evento de un mto-resources anterior deja lo
     * que habia, y eso lo decide la sentencia ({@code coalesce}).
     */
    static String jsonList(Map<String, Object> values, String key, Function<MasterDataPayload, Map<String, Object>> item) {
        if (!(values.get(key) instanceof List<?>)) {
            return null;
        }
        List<Map<String, Object>> kept = new ArrayList<>();
        for (MasterDataPayload each : MasterDataPayload.of(values).nestedList(key)) {
            Map<String, Object> normalized = item.apply(each);
            if (normalized != null) {
                kept.add(normalized);
            }
        }
        return JsonMapper.shared().writeValueAsString(kept);
    }
}
