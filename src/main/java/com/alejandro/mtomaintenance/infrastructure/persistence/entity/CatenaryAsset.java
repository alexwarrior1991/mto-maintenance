package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
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
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

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

    @Size(max = 100)
    @Column(name = "source_service", length = 100)
    private String sourceService;

    @Size(max = 100)
    @Column(name = "source_entity_id", length = 100)
    @ToString.Include
    private String sourceEntityId;

    @Column(name = "source_sequence_number")
    private Long sourceSequenceNumber;

    @NotNull
    @Builder.Default
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
}
