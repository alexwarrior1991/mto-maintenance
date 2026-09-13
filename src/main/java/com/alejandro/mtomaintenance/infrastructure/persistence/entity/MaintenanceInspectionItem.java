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

/** Punto de una inspeccion, copiado de la plantilla del tipo de activo. */
@Audited
@Entity
@Table(name = "maintenance_inspection_item", uniqueConstraints = @UniqueConstraint(name = "uq_maintenance_inspection_item_code", columnNames = {"inspection_id", "code"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class MaintenanceInspectionItem extends AbstractChecklistItem {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false, foreignKey = @ForeignKey(name = "fk_maintenance_inspection_item_inspection"))
    private MaintenanceInspection inspection;

    public static MaintenanceInspectionItem fromTemplate(MaintenanceInspection inspection, InspectionTemplateItem templateItem) {
        MaintenanceInspectionItem item = new MaintenanceInspectionItem();
        item.inspection = inspection;
        item.copyFrom(templateItem);
        return item;
    }
}
