package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * Checklist de inspeccion por tipo de activo, versionada: la activa de su tipo (como mucho una,
 * {@code uq_inspection_template_active_asset_type}) es la que se copia a cada inspeccion o tarea nueva.
 *
 * <p>Una version no cambia en lo que se copia: su tipo de activo, su numero y sus puntos son los de su
 * alta, y cambiar el checklist es crear la version siguiente ({@code POST /inspection-templates}). En su
 * sitio solo cambian el nombre y si es la activa ({@code PATCH}), y por eso lleva bloqueo optimista. Es
 * un catalogo y no se audita: su historia son sus versiones, cada una con quien la creo y cuando.</p>
 *
 * <p>El bloqueo optimista va en {@code lock_version} y no en {@code version} como en las demas tablas:
 * aqui {@code version} es ya el numero de version de la plantilla ({@link #templateVersion}).</p>
 */
@Entity
@AttributeOverride(name = "version", column = @Column(name = "lock_version", nullable = false))
@Table(name = "inspection_template", uniqueConstraints = @UniqueConstraint(name = "uq_inspection_template_asset_type_version", columnNames = {"asset_type", "version"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class InspectionTemplate extends VersionedEntity {

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "asset_type", nullable = false, updatable = false, columnDefinition = "catenary_asset_type")
    @ToString.Include
    private CatenaryAssetType assetType;

    @NotNull
    @Builder.Default
    @Column(name = "version", nullable = false, updatable = false)
    @ToString.Include
    private Integer templateVersion = 1;

    @NotBlank
    @Size(max = 120)
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @NotNull
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex asc")
    @Builder.Default
    private List<InspectionTemplateItem> items = new ArrayList<>();
}
