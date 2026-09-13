package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.exception.ValidationException;

import java.util.function.Supplier;

/**
 * Convierte la {@link IllegalArgumentException} con la que los tipos de dominio rechazan un valor en
 * la {@link ValidationException} (400) de la API. El dominio no conoce las excepciones de la
 * aplicacion, y un invariante roto por una peticion es un error del cliente, no un 500.
 */
final class DomainGuard {

    private DomainGuard() {
    }

    static <T> T domain(Supplier<T> constructor) {
        try {
            return constructor.get();
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(exception.getMessage());
        }
    }
}
