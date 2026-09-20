package com.alejandro.mtomaintenance.application.dto.asset;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Aguja por la que un aislador de sección conecta con una vía: en el plano, {@code W31 1:9}.
 *
 * @param turnoutDenominator el {@code 9} de {@code 1:9}. El numerador siempre es 1, así que se
 *                           guarda sólo el denominador, que además se puede ordenar y comparar
 * @param turnoutRate        el mismo dato como está escrito en el plano ({@code "1:9"}), para no
 *                           obligar a cada consumidor a componer la cadena
 * @param enabled            si la aguja está en servicio, según {@code mto-configuration}. Una
 *                           aguja dada de baja sigue apareciendo, marcada: para el equipo no es lo
 *                           mismo que no exista a que no se pueda contar con ella
 */
public record CatenaryAssetSwitchResponse(
        UUID id,
        String code,
        BigDecimal kp,
        Integer turnoutDenominator,
        String turnoutRate,
        Long trackId,
        Boolean enabled
) {
}
