package com.alejandro.mtomaintenance.application.dto.task;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Lo generado, o con {@code dryRun} lo que se generaria: entonces {@code createdTasks},
 * {@code totalTasks} y la estimacion cuentan las tareas que se crearian, y {@code assets} trae cada
 * activo con lo que pasaria con el.
 *
 * @param skippedProfiles         los perfiles que ya tenian tarea en la orden
 * @param skippedAssets           todos los activos que ya tenian tarea en la orden: con solo perfiles
 *                                es lo mismo que {@code skippedProfiles}
 * @param createdByAssetType      lo creado por tipo de activo, con cada tipo pedido aunque sea cero
 * @param skippedInOtherOrders    los que tienen una tarea abierta en otra orden preventiva abierta
 * @param otherOrderCodes         esas ordenes
 * @param skippedOtherPackage     los de otro EP que la orden, que van en otra orden
 * @param skippedWithoutTaskTypes los que el plan deja sin tipo de tarea (un seccionador sin motor) sin
 *                                tipos elegidos
 * @param assets                  con {@code dryRun}, cada activo en el orden en que se generaria; si no,
 *                                vacio
 */
public record GeneratePreventiveTasksResponse(
        int createdTasks,
        int skippedProfiles,
        int totalTasks,
        BigDecimal estimatedMinutes,
        int estimatedShifts,
        int skippedAssets,
        Map<CatenaryAssetType, Integer> createdByAssetType,
        int skippedInOtherOrders,
        List<String> otherOrderCodes,
        int skippedOtherPackage,
        int skippedWithoutTaskTypes,
        boolean dryRun,
        List<GenerationPreviewLine> assets
) {
}
