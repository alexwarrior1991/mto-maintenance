package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.dto.inspection.CheckItemUpdateRequest;
import com.alejandro.mtomaintenance.application.exception.InspectionException;
import com.alejandro.mtomaintenance.application.exception.StaleVersionException;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.AbstractChecklistItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CheckItemResult;

import java.util.Set;

/** Reglas comunes a los puntos de una checklist, sean de inspeccion o de tarea. */
final class ChecklistRules {

    private ChecklistRules() {
    }

    /** Lo que un PATCH puede vaciar de un punto; {@code adjusted} es si o no, nunca nada. */
    static final Set<String> CLEARABLE = Set.of("measuredValue", "valueAfterAdjustment", "itemResult", "notes");

    static void apply(AbstractChecklistItem item, MergePatch<CheckItemUpdateRequest> patch) {
        CheckItemUpdateRequest request = patch.values();
        StaleVersionException.check("Check item " + item.getCode(), item.getVersion(), request.version());
        PatchRules.requireClearable(patch, CLEARABLE);
        PatchRules.set(patch, "measuredValue", request.measuredValue(), item::setMeasuredValue);
        if (request.adjusted() != null) {
            item.setAdjusted(request.adjusted());
        }
        if (request.valueAfterAdjustment() != null) {
            item.setValueAfterAdjustment(request.valueAfterAdjustment());
            item.setAdjusted(true);
        } else if (patch.clears("valueAfterAdjustment")) {
            item.setValueAfterAdjustment(null);
        }
        PatchRules.set(patch, "notes", request.notes(), item::setNotes);
        PatchRules.set(patch, "itemResult", request.itemResult(), item::setItemResult);
        // Una medida fuera de rango no puede quedar OK sin haberse ajustado: o se ajusta (y el valor
        // final entra en rango) o es un defecto.
        if (item.getItemResult() == CheckItemResult.OK && item.isOutOfRange()) {
            throw new InspectionException("Item " + item.getCode() + " is out of range (" + item.effectiveValue()
                    + " " + (item.getUnit() == null ? "" : item.getUnit()) + ") and cannot be OK unless adjusted into range");
        }
    }
}
