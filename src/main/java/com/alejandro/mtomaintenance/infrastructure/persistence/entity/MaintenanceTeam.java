package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
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

import java.util.LinkedHashSet;
import java.util.Set;

/** Equipo de mantenimiento con vehiculo y base propios. Catalogo pequeno: no se audita con Envers. */
@Entity
@Table(name = "maintenance_team", uniqueConstraints = @UniqueConstraint(name = "uq_maintenance_team_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class MaintenanceTeam extends AuditableEntity {

    @NotBlank
    @Size(max = 16)
    @Column(name = "code", nullable = false, length = 16)
    @ToString.Include
    private String code;

    @NotBlank
    @Size(max = 120)
    @Column(name = "name", nullable = false, length = 120)
    @ToString.Include
    private String name;

    @Size(max = 120)
    @Column(name = "base_name", length = 120)
    private String baseName;

    @Size(max = 120)
    @Column(name = "vehicle", length = 120)
    private String vehicle;

    @NotNull
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /** Ids (mto-configuration) de los paquetes de ejecucion que atiende el equipo. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "maintenance_team_execution_package",
            joinColumns = @JoinColumn(name = "team_id", foreignKey = @ForeignKey(name = "fk_maintenance_team_execution_package_team"))
    )
    @Column(name = "execution_package_id", nullable = false)
    @Builder.Default
    private Set<Long> executionPackageIds = new LinkedHashSet<>();
}
