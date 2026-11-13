package com.alejandro.mtomaintenance.application.dto.inspection;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Un punto de una version nueva de una plantilla ({@code InspectionTemplateRequest.items}). Su orden es
 * su posicion en la lista: {@code orderIndex} no se manda.
 *
 * <pre>{@code
 * {"code": "CW_HEIGHT", "label": "Contact wire height at the pole", "unit": "mm", "minValue": 5000, "maxValue": 5500,
 *  "requiresMeasure": true}
 * }</pre>
 *
 * @param code            unico en la plantilla; mayusculas, cifras y {@code _ . -}, empezando por letra o cifra
 * @param label           lo que se mira o se mide
 * @param unit            la unidad de la medida, si es de medida
 * @param minValue        minimo admitido, {@code numeric(12,3)}; con {@code maxValue}, no mayor que el
 * @param maxValue        maximo admitido, {@code numeric(12,3)}
 * @param requiresMeasure el punto no se da por bueno sin medida; sin el, {@code false}
 */
public record InspectionTemplateItemRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = CODE_PATTERN, message = "must be upper case letters, digits, '_', '.' or '-', starting with a letter or a digit") String code,
        @NotBlank @Size(max = 255) String label,
        @Size(max = 32) String unit,
        @Digits(integer = 9, fraction = 3) BigDecimal minValue,
        @Digits(integer = 9, fraction = 3) BigDecimal maxValue,
        Boolean requiresMeasure
) {

    /** Como los codigos sembrados ({@code CW_HEIGHT}): sin espacios ni minusculas, para que dos no se distingan solo por eso. */
    public static final String CODE_PATTERN = "[A-Z0-9][A-Z0-9_.-]*";
}
