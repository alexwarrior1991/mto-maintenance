package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Aguja por la que un aislador de sección conecta con una vía.
 *
 * <p>Es el dato que el equipo necesita para plantarse en el sitio: sobre qué aguja está el
 * aislador, en qué punto kilométrico y con qué tangente de desvío. En el plano se rotula
 * {@code W31 1:9}.
 *
 * <p><b>No es un activo mantenible.</b> La aguja es parte del aislador, no algo sobre lo que se
 * abre una orden, así que cuelga de {@code CatenaryAsset} y no amplía {@code CatenaryAssetType}.
 *
 * <p><b>No se audita, a propósito.</b> Estas filas se escriben <b>únicamente</b> desde los eventos
 * de datos maestros, igual que {@code InboxMessage}: una gemela {@code _aud} se quedaría vacía y se
 * leería como «nunca cambió», que es peor que no tenerla. El historial del aislador vive en
 * {@code mto-configuration}, que es quien posee el dato.
 */
@Entity
@Table(
        name = "catenary_asset_switch",
        uniqueConstraints = @UniqueConstraint(name = "uq_catenary_asset_switch",
                columnNames = {"asset_id", "code"}),
        indexes = {
                @Index(name = "idx_catenary_asset_switch_asset", columnList = "asset_id"),
                @Index(name = "idx_catenary_asset_switch_code", columnList = "code")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class CatenaryAssetSwitch extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private CatenaryAsset asset;

    /** Identificador de la aguja en el plano: {@code W31}. */
    @NotBlank
    @Size(max = 40)
    @Column(name = "code", nullable = false, length = 40)
    @ToString.Include
    private String code;

    /** Punto kilométrico de la aguja, en metros. El plano escribe {@code 110+176}. */
    @Digits(integer = 9, fraction = 3)
    @Column(name = "kp", precision = 12, scale = 3)
    private BigDecimal kp;

    /**
     * El {@code 9} de {@code 1:9}: la tangente del desvío, con el numerador implícito.
     *
     * <p>Se guarda el entero y no el literal porque es lo único que varía y porque así se puede
     * ordenar y comparar: un {@code 1:12} es más tendido que un {@code 1:9}.
     */
    @Positive
    @Column(name = "turnout_denominator")
    private Integer turnoutDenominator;

    /** Vía a la que llega esta conexión. Id de {@code mto-configuration}, luego {@code bigint}. */
    @Column(name = "track_id")
    private Long trackId;

    /**
     * Si la aguja está en servicio, tal y como lo dice {@code mto-configuration}.
     *
     * <p>Una aguja dada de baja llega igual en el evento —la colección del origen sólo filtra los
     * borrados lógicos, no las deshabilitadas—, así que se guarda en lugar de descartarla: para el
     * equipo no es lo mismo «esa aguja no existe» que «esa aguja está fuera de servicio», y el
     * parte de turno la marca. Es el mismo criterio que {@code CatenaryAsset.enabled}.
     */
    @NotNull
    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    /** La tangente tal y como está escrita en el plano, para no componerla en cada consumidor. */
    public String turnoutRate() {
        return turnoutDenominator == null ? null : "1:" + turnoutDenominator;
    }
}
