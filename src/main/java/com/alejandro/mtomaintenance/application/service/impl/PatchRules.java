package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.exception.ValidationException;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Lo comun a los PUT y PATCH de los recursos editables. Un PUT es un {@link MergePatch} que no vacia
 * nada ({@link MergePatch#of}), asi que los dos pasan por el mismo codigo.
 */
final class PatchRules {

    private PatchRules() {
    }

    /** 400 si el parche vacia algo que el recurso no admite vaciar: un campo obligatorio, o uno que no se edita. */
    static void requireClearable(MergePatch<?> patch, Set<String> clearable) {
        List<String> refused = patch.cleared().stream().filter(field -> !clearable.contains(field)).sorted().toList();
        if (!refused.isEmpty()) {
            throw new ValidationException("These fields cannot be emptied: " + String.join(", ", refused));
        }
    }

    /** Un texto obligatorio en blanco: 400 aqui, y no un 500 al confirmar contra el {@code @NotBlank} de la entidad. */
    static String requireText(String value, String field) {
        if (value.isBlank()) {
            throw new ValidationException(field + " cannot be blank");
        }
        return value.trim();
    }

    /** Aplica el valor si viene, o vacia el campo si el parche lo pide; si no, no toca nada. */
    static <T> void set(MergePatch<?> patch, String field, T value, Consumer<T> setter) {
        if (value != null || patch.clears(field)) {
            setter.accept(value);
        }
    }
}
