package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.exception.ShiftException;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;

/**
 * Ventanas de ejecucion del plan OCS: entre semana solo se corta una via principal entre dos
 * seccionadores (posesion parcial) y quedan prohibidos los grupos 3 y 5 y RP-12; las vias desviadas
 * y esos trabajos esperan a la posesion total de fin de semana o festivo.
 */
final class ShiftRules {

    private ShiftRules() {
    }

    static void requireSameTrack(MaintenanceShift shift, MaintenanceTask task) {
        Long taskTrack = task.getAsset() != null && task.getAsset().getTrackId() != null
                ? task.getAsset().getTrackId()
                : task.getOrder().getTrackId();
        if (taskTrack != null && !taskTrack.equals(shift.getTrackId())) {
            throw new ShiftException("Shift " + shift.getCode() + " works on track " + shift.getTrackId()
                    + " but the task belongs to track " + taskTrack);
        }
    }

    static void requireCompatiblePossession(MaintenanceShift shift, MaintenanceTask task) {
        if (shift.getPossessionType() != PossessionType.PARTIAL) {
            return;
        }
        if (task.requiresFullPossession()) {
            throw new ShiftException("Shift " + shift.getCode() + " has partial possession; the task includes work that "
                    + "needs full track possession (groups 3 and 5, neutral sections)");
        }
        CatenaryAsset orderAsset = task.getOrder().getAsset();
        if (orderAsset != null && orderAsset.getTrackKind() == TrackKind.DIVERTED) {
            throw new ShiftException("Shift " + shift.getCode() + " has partial possession; diverted tracks are only "
                    + "worked with full possession");
        }
    }

    static void requireInProgress(MaintenanceShift shift) {
        if (!shift.isInProgress()) {
            throw new ShiftException("Shift " + shift.getCode() + " is " + shift.getStatus() + ", tasks can only be worked in a shift in progress");
        }
    }
}
