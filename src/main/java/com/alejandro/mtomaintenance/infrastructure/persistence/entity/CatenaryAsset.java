package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Activo mantenible: referencia ligera a la infraestructura, nunca una copia de ella.
 *
 * <p>Los que vienen de mto-configuration llevan {@code sourceService} y {@code sourceEntityId}; su
 * identidad y localizacion no se editan por API y se sincronizan con el upsert nativo de
 * {@code CatenaryAssetRepository} (que, por ser SQL nativo, no deja revision de Envers).</p>
 */
@Audited
@Entity
@Table(
        name = "catenary_asset",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_catenary_asset_code", columnNames = "code"),
                @UniqueConstraint(name = "uq_catenary_asset_source", columnNames = {"source_service", "source_entity_id"})
        },
        indexes = {
                @Index(name = "idx_catenary_asset_track_kp", columnList = "track_id, start_kp"),
                @Index(name = "idx_catenary_asset_type_enabled", columnList = "type, enabled"),
                @Index(name = "idx_catenary_asset_execution_package", columnList = "execution_package_id"),
                @Index(name = "idx_catenary_asset_station", columnList = "station_id")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class CatenaryAsset extends AuditableEntity {

    @NotBlank
    @Size(max = 64)
    @Column(name = "code", nullable = false, length = 64)
    @ToString.Include
    private String code;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    @ToString.Include
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false, columnDefinition = "catenary_asset_type")
    @ToString.Include
    private CatenaryAssetType type;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "execution_package_id")
    private Long executionPackageId;

    @Column(name = "track_id")
    private Long trackId;

    @Column(name = "station_id")
    private Long stationId;

    @Digits(integer = 9, fraction = 3)
    @Column(name = "start_kp", precision = 12, scale = 3)
    private BigDecimal startKp;

    @Digits(integer = 9, fraction = 3)
    @Column(name = "end_kp", precision = 12, scale = 3)
    private BigDecimal endKp;

    @Size(max = 100)
    @Column(name = "profile_source_id", length = 100)
    private String profileSourceId;

    @Size(max = 255)
    @Column(name = "sectioning")
    private String sectioning;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "track_kind", columnDefinition = "track_kind")
    private TrackKind trackKind;

    /**
     * Vía con la que conecta un aislador de sección. Sólo la llevan los {@code SECTION_INSULATOR},
     * y ni siquiera todos: uno en medio de una vía no conecta con ninguna otra.
     */
    @Column(name = "connected_track_id")
    private Long connectedTrackId;

    /** Dos vías que conectan por una aguja, o en medio de una sola. Sólo en un aislador. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "installation_type", columnDefinition = "section_insulator_installation")
    private SectionInsulatorInstallation installationType;

    /**
     * Agujas por las que el aislador conecta con la vía, en orden físico a lo largo de ella.
     *
     * <p><b>Sin cascade ni orphanRemoval, a propósito.</b> Esta colección es sólo de lectura: las
     * filas las escribe el manejador de datos maestros a través de
     * {@code CatenaryAssetSwitchRepository}, reemplazando el bloque entero, y el borrado en cadena
     * lo garantiza la clave ajena. Con cascade, un {@code save(asset)} de la API —que es lo que
     * hacen {@code update} y {@code disable}— podría tocar filas que no le pertenecen; sin él, no
     * hay forma de que eso ocurra.
     *
     * <p>{@code @NotAudited} porque {@code CatenaryAssetSwitch} no se audita —ver su javadoc— y
     * Envers no puede auditar una colección hacia una entidad que no lo está.
     */
    @NotAudited
    @OneToMany(mappedBy = "asset", fetch = FetchType.LAZY)
    @OrderBy("kp asc, code asc")
    @Builder.Default
    private List<CatenaryAssetSwitch> switches = new ArrayList<>();

    @Size(max = 100)
    @Column(name = "source_service", length = 100)
    private String sourceService;

    @Size(max = 100)
    @Column(name = "source_entity_id", length = 100)
    @ToString.Include
    private String sourceEntityId;

    @Column(name = "source_sequence_number")
    private Long sourceSequenceNumber;

    /** Lo ultimo que dijo mto-configuration; null en los activos propios. Solo lo escribe el upsert. */
    @Column(name = "enabled_at_source")
    private Boolean enabledAtSource;

    /** La decision de mantenimiento (DELETE, o PUT con enabled=false): ningun evento la toca. */
    @NotNull
    @Builder.Default
    @Setter(AccessLevel.NONE)
    @Column(name = "disabled_locally", nullable = false)
    private Boolean disabledLocally = false;

    /**
     * El valor efectivo, el que leen todas las consultas: {@code coalesce(enabledAtSource, true) and
     * not disabledLocally}. No se escribe suelto (un CHECK de la base lo impide): lo recalculan
     * {@link #disableLocally}, {@link #enableLocally} y el upsert de datos maestros.
     */
    @NotNull
    @Builder.Default
    @Setter(AccessLevel.NONE)
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Positive
    @Column(name = "preventive_interval_days")
    private Integer preventiveIntervalDays;

    @Column(name = "last_preventive_completed_at")
    private Instant lastPreventiveCompletedAt;

    /** Verdadero si el activo lo mantiene sincronizado un evento de datos maestros. */
    public boolean isFromMasterData() {
        return sourceService != null;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    /** Desactivado en mto-configuration: vuelve cuando el origen lo reactive, no antes. */
    public boolean isDisabledAtSource() {
        return Boolean.FALSE.equals(enabledAtSource);
    }

    public void disableLocally() {
        this.disabledLocally = true;
        this.enabled = false;
    }

    /** Quita la desactivacion de mantenimiento; si el origen lo tiene desactivado, sigue desactivado. */
    public void enableLocally() {
        this.disabledLocally = false;
        this.enabled = !isDisabledAtSource();
    }
}
