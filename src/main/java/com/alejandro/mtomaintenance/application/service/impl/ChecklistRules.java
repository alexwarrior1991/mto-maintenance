package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.inspection.CheckItemUpdateRequest;
import com.alejandro.mtomaintenance.application.exception.InspectionException;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.AbstractChecklistItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CheckItemResult;

/** Reglas comunes a los puntos de una checklist, sean de inspeccion o de tarea. */
final class ChecklistRules {

    private ChecklistRules() {
    }

    static void apply(AbstractChecklistItem item, CheckItemUpdateRequest request) {
        if (request.measuredValue() != null) {
            item.setMeasuredValue(request.measuredValue());
        }
        if (request.adjusted() != null) {
            item.setAdjusted(request.adjusted());
        }
        if (request.valueAfterAdjustment() != null) {
            item.setValueAfterAdjustment(request.valueAfterAdjustment());
            item.setAdjusted(true);
        }
        if (request.notes() != null) {
            item.setNotes(request.notes());
        }
        if (request.itemResult() != null) {
            item.setItemResult(request.itemResult());
        }
        // Una medida fuera de rango no puede quedar OK sin haberse ajustado: o se ajusta (y el valor
        // final entra en rango) o es un defecto.
        if (item.getItemResult() == CheckItemResult.OK && item.isOutOfRange()) {
            throw new InspectionException("Item " + item.getCode() + " is out of range (" + item.effectiveValue()
                    + " " + (item.getUnit() == null ? "" : item.getUnit()) + ") and cannot be OK unless adjusted into range");
        }
    }
}
