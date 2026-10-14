package com.alejandro.mtomaintenance.application.dto.task;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Lo que se genera en un preventivo.
 *
 * <p>Sin {@code selections}, lo de siempre: los activos del tramo de la orden. Con ellas, lo que dice
 * cada una, en cualquier via del EP de la orden.</p>
 *
 * @param taskTypeCodes los tipos de todas las tareas generadas; sin codigos, los de cada activo: grupos
 *                      1, 2 y 4 en un perfil de via principal, {@code RG-03} en un aislador de seccion
 *                      y {@code RP-06} en un seccionador con motor
 * @param assetTypes    sin {@code selections}, que activos del tramo llevan tarea: {@code PROFILE},
 *                      {@code SECTION_INSULATOR} y {@code DISCONNECTOR}; sin ninguno, solo los perfiles
 * @param selections    que generar y donde, una parte cada una; no va junto con {@code assetTypes}
 * @param dryRun        si es verdadero no se crea nada: la respuesta dice que se crearia y que se saltaria
 */
public record GeneratePreventiveTasksRequest(
        List<@NotNull String> taskTypeCodes,
        Boolean withChecklist,
        List<@NotNull CatenaryAssetType> assetTypes,
        @Valid @Size(max = 20) List<@NotNull GenerationSelection> selections,
        Boolean dryRun
) {

    // Sin prefijo is/get, para que Jackson no lo lea como otra propiedad.
    public boolean previewOnly() {
        return Boolean.TRUE.equals(dryRun);
    }

    public boolean hasSelections() {
        return selections != null && !selections.isEmpty();
    }
}
