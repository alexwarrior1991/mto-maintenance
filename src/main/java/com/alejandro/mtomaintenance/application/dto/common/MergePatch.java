package com.alejandro.mtomaintenance.application.dto.common;

import java.util.Set;

/**
 * Un cuerpo {@code application/merge-patch+json} (RFC 7396): {@code values} es el record de la
 * modificacion leido y validado como en el PUT, y {@code cleared}, los campos que venian a
 * {@code null}. Ausente es no tocar, como en el PUT; {@code null} es vaciar, que el PUT no sabe decir.
 * Cada servicio vacia solo lo que admite: vaciar un campo obligatorio es 400.
 */
public record MergePatch<T>(T values, Set<String> cleared) {

    /** El tipo de contenido del RFC 7396; un PATCH con otro responde 415. */
    public static final String MEDIA_TYPE = "application/merge-patch+json";

    public MergePatch {
        cleared = Set.copyOf(cleared);
    }

    /** Un PUT es un parche que no vacia nada. */
    public static <T> MergePatch<T> of(T values) {
        return new MergePatch<>(values, Set.of());
    }

    public boolean clears(String field) {
        return cleared.contains(field);
    }
}
