package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.exception.ShiftException;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;

/**
 * Ventanas de ejecucion del plan OCS: entre semana se corta la via principal entre dos
 * seccionadores (posesion parcial) y quedan prohibidos los grupos 3 y 5 y RP-12; las vias desviadas
 * y esos trabajos esperan a la posesion total de fin de semana o festivo. Un turno puede recorrer
 * varias vias en la misma noche: lo que se exige es que la via del perfil sea una de ellas.
 */
final class ShiftRules {

    private ShiftRules() {
    }

    /**
     * La tarea tiene que estar en una de las vias del turno. Un aislador de seccion o un seccionador
     * que une dos vias ({@code connectedTrackId}) esta en las dos, igual que en los informes por via:
     * se trabaja desde un turno que cubra cualquiera de ellas.
     */
    static void requireSameTrack(MaintenanceShift shift, MaintenanceTask task) {
        CatenaryAsset asset = task.getAsset();
        Long taskTrack = asset != null && asset.getTrackId() != null
                ? asset.getTrackId()
                : task.getOrder().getTrackId();
        Long connectedTrack = asset == null ? null : asset.getConnectedTrackId();
        if (taskTrack != null && !shift.worksOn(taskTrack) && (connectedTrack == null || !shift.worksOn(connectedTrack))) {
            throw new ShiftException("Shift " + shift.getCode() + " works on tracks " + shift.getTrackIds().stream().sorted().toList()
                    + " but the task belongs to track " + taskTrack
                    + (connectedTrack == null ? "" : " and connects with track " + connectedTrack));
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
