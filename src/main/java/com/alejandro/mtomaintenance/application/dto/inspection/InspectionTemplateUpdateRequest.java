package com.alejandro.mtomaintenance.application.dto.inspection;

import jakarta.validation.constraints.Size;

/**
 * Lo que cambia de una version de una plantilla en su sitio ({@code PATCH /inspection-templates/{id}},
 * {@code application/merge-patch+json}): su nombre y si es la activa de su tipo. Sus puntos no: cambiar
 * el checklist es crear la version siguiente.
 *
 * <pre>{@code
 * {"active": true, "lockVersion": 0}
 * }</pre>
 *
 * @param name        el nombre; no se vacia
 * @param active      {@code true}: pasa a ser la activa de su tipo, y la que lo era deja de serlo;
 *                    {@code false}: deja de serlo, y si lo era el tipo se queda sin plantilla
 * @param lockVersion el {@code lockVersion} leido: si cambio desde entonces, 409 {@code CON-001}. No se
 *                    llama {@code version}, que en una plantilla es su numero de version
 */
public record InspectionTemplateUpdateRequest(@Size(max = 120) String name, Boolean active, Long lockVersion) {
}
