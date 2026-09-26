package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.NotAudited;

/**
 * Una entidad que se edita por la API y que por tanto pueden editar dos personas a la vez: lleva
 * bloqueo optimista.
 *
 * <p>Hibernate sube {@code version} en cada escritura y rechaza la que llega con una version vieja
 * ({@code OptimisticLockException}, 409 {@code CON-001}). Eso cubre dos transacciones que se cruzan,
 * pero no a quien leyo hace diez minutos y escribe ahora: esa comprobacion la hace el servicio con la
 * {@code version} que trae la peticion, si la trae. El SQL nativo de datos maestros la sube a mano,
 * porque Hibernate no lo ve.</p>
 *
 * <p>{@code @NotAudited} explicito por la misma razon que los campos de {@link AuditableEntity}: la
 * version de una fila no es historia, y Envers no la guarda (su valor por defecto,
 * {@code do_not_audit_optimistic_locking_field}, tampoco).</p>
 */
@Getter
@Setter
@MappedSuperclass
public abstract class VersionedEntity extends AuditableEntity {

    @Version
    @NotAudited
    @Setter(AccessLevel.NONE)
    @Column(name = "version", nullable = false)
    private Long version;
}
