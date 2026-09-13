package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.envers.Audited;

/** Punto a analizar / ajustar de una tarea de preventivo. */
@Audited
@Entity
@Table(name = "maintenance_task_check_item", uniqueConstraints = @UniqueConstraint(name = "uq_maintenance_task_check_item_code", columnNames = {"task_id", "code"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class MaintenanceTaskCheckItem extends AbstractChecklistItem {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, foreignKey = @ForeignKey(name = "fk_maintenance_task_check_item_task"))
    private MaintenanceTask task;

    public static MaintenanceTaskCheckItem fromTemplate(MaintenanceTask task, InspectionTemplateItem templateItem) {
        MaintenanceTaskCheckItem item = new MaintenanceTaskCheckItem();
        item.task = task;
        item.copyFrom(templateItem);
        return item;
    }
}
