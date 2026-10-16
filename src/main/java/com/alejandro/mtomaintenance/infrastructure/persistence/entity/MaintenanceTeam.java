package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
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
import org.hibernate.annotations.Immutable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Equipo de mantenimiento. Catalogo pequeno: no se audita con Envers.
 *
 * <p>Uno <b>sincronizado</b> ({@code sourceService = mto-resources}) es de mto-resources: su jefe, su
 * supervisor, sus linemen y su vehiculo por defecto los escribe con SQL nativo
 * {@code ResourcesSnapshotRepository}, y por eso esas columnas no las escribe JPA. Uno local, de antes,
 * se edita aqui hasta que se da de alta alli con su codigo y se adopta.</p>
 */
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

    /** {@code mto-resources} en un equipo sincronizado; {@code null} en uno local. */
    @Column(name = "source_service", length = 100, insertable = false, updatable = false)
    private String sourceService;

    @Column(name = "source_entity_id", insertable = false, updatable = false)
    private UUID sourceEntityId;

    @Column(name = "source_sequence_number", insertable = false, updatable = false)
    private Long sourceSequenceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_person_id", insertable = false, updatable = false)
    private CrewPerson leader;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_person_id", insertable = false, updatable = false)
    private CrewPerson supervisor;

    /** Id (mto-resources) de su vehiculo por defecto; sin clave ajena: puede llegar antes que el vehiculo. */
    @Column(name = "default_vehicle_id", insertable = false, updatable = false)
    private UUID defaultVehicleId;

    /** Sus linemen, tal como los cuenta mto-resources; el jefe va aparte. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "maintenance_team_member",
            joinColumns = @JoinColumn(name = "team_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id"))
    @Immutable
    @Builder.Default
    private Set<CrewPerson> members = new LinkedHashSet<>();

    /** Ids (mto-configuration) de los paquetes de ejecucion que atiende el equipo. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "maintenance_team_execution_package",
            joinColumns = @JoinColumn(name = "team_id", foreignKey = @ForeignKey(name = "fk_maintenance_team_execution_package_team"))
    )
    @Column(name = "execution_package_id", nullable = false)
    @Builder.Default
    private Set<Long> executionPackageIds = new LinkedHashSet<>();

    public boolean isSynchronized() {
        return sourceService != null;
    }
}
