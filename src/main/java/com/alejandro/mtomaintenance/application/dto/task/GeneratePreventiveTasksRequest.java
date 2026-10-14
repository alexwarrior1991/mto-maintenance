package com.alejandro.mtomaintenance.application.dto.task;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Lo que se genera en un tramo.
 *
 * @param taskTypeCodes los tipos de todas las tareas generadas; sin codigos, los de cada tipo de
 *                      activo: grupos 1, 2 y 4 en un perfil de via principal, {@code RG-03} en un
 *                      aislador de seccion
 * @param assetTypes    que activos del tramo llevan tarea: {@code PROFILE} y {@code SECTION_INSULATOR};
 *                      sin ninguno, solo los perfiles, que es lo de siempre
 */
public record GeneratePreventiveTasksRequest(
        List<@NotNull String> taskTypeCodes,
        Boolean withChecklist,
        List<@NotNull CatenaryAssetType> assetTypes
) {
}
