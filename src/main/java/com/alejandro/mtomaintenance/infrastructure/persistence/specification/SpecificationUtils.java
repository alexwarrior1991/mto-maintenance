package com.alejandro.mtomaintenance.infrastructure.persistence.specification;

import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/**
 * Small helper methods for composing null-safe Spring Data JPA Specifications.
 */
public final class SpecificationUtils {

    private SpecificationUtils() {
    }

    public static <T> Specification<T> alwaysTrue() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
    }

    public static <T> Specification<T> equalsBoolean(String attribute, Boolean value) {
        if (value == null) {
            return alwaysTrue();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(attribute), value);
    }

    public static <T> Specification<T> equalsEnum(String attribute, Enum<?> value) {
        if (value == null) {
            return alwaysTrue();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(attribute), value);
    }

    public static <T> Specification<T> associationIdEquals(String association, UUID id) {
        if (id == null) {
            return alwaysTrue();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(association).get("id"), id);
    }

    public static <T> Specification<T> containsIgnoreCase(String attribute, String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue == null) {
            return alwaysTrue();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(
                criteriaBuilder.lower(root.get(attribute)),
                "%" + normalizedValue.toLowerCase() + "%"
        );
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static <T> Specification<T> equalsLong(String attribute, Long value) {
        if (value == null) {
            return alwaysTrue();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(attribute), value);
    }

    public static <T> Specification<T> equalsUuid(String attribute, UUID value) {
        if (value == null) {
            return alwaysTrue();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(attribute), value);
    }

    public static <T> Specification<T> equalsIgnoreCase(String attribute, String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue == null) {
            return alwaysTrue();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                criteriaBuilder.lower(root.get(attribute)), normalizedValue.toLowerCase());
    }

    /** Rango inclusivo sobre una columna comparable; cualquiera de los dos extremos puede faltar. */
    public static <T, V extends Comparable<? super V>> Specification<T> between(String attribute, V from, V to) {
        if (from == null && to == null) {
            return alwaysTrue();
        }
        return (root, query, criteriaBuilder) -> {
            Path<V> path = root.get(attribute);
            if (from != null && to != null) {
                return criteriaBuilder.between(path, from, to);
            }
            return from != null
                    ? criteriaBuilder.greaterThanOrEqualTo(path, from)
                    : criteriaBuilder.lessThanOrEqualTo(path, to);
        };
    }

    /** Atributo de una asociacion a-uno, por ejemplo {@code asset.type}. */
    public static <T> Specification<T> associationAttributeEquals(String association, String attribute, Object value) {
        if (value == null) {
            return alwaysTrue();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(association).get(attribute), value);
    }
}
