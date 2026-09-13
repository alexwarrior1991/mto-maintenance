package com.alejandro.mtomaintenance.domain.model;

/** Comprobaciones de invariantes de los tipos de dominio, sin dependencias de framework. */
public final class DomainValidations {

    private DomainValidations() {
    }

    public static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
