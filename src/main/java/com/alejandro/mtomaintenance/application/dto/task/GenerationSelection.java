package com.alejandro.mtomaintenance.application.dto.task;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Una parte de lo que genera un preventivo: los activos de un tipo en un alcance del EP de la orden.
 * Una orden es de un solo EP y puede abarcar varias de sus vias; lo de otro EP va en otra orden.
 *
 * <p>El alcance es uno de cuatro, o ninguno:</p>
 * <ul>
 *     <li>{@code sectionId}: un tramo ({@code TRACK_SECTION}) del EP, con su via y sus kp;</li>
 *     <li>{@code trackId}, con {@code fromKp}/{@code toKp} opcionales: lo de esa via o lo que conecta con ella;</li>
 *     <li>{@code assetIds}: activos concretos del tipo y del EP;</li>
 *     <li>ninguno: todo el EP de la orden.</li>
 * </ul>
 *
 * @param stationId     filtra cualquier alcance salvo {@code assetIds}
 * @param taskTypeCodes los tipos de lo que genera esta seleccion; sin ellos, los de la peticion, y sin
 *                      esos, los del plan para cada activo
 * @param withChecklist igual: el de la seleccion manda sobre el de la peticion
 */
public record GenerationSelection(
        @NotNull CatenaryAssetType assetType,
        UUID sectionId,
        Long trackId,
        @Digits(integer = 9, fraction = 3) BigDecimal fromKp,
        @Digits(integer = 9, fraction = 3) BigDecimal toKp,
        Long stationId,
        @Size(max = 500) List<@NotNull UUID> assetIds,
        List<@NotNull String> taskTypeCodes,
        Boolean withChecklist
) {
}
