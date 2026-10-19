package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.exception.ShiftException;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TaskEquipment;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;

import java.util.List;

/**
 * Ventanas de ejecucion del plan OCS: entre semana se corta la via principal entre dos
 * seccionadores (posesion parcial) y quedan prohibidos los grupos 3 y 5 y RP-12; las vias desviadas
 * y esos trabajos esperan a la posesion total de fin de semana o festivo. Un turno puede recorrer
 * varias vias en la misma noche: lo que se exige es que la via del perfil sea una de ellas.
 */
final class ShiftRules {

    /** La clase de instrumento de mto-resources que pone a tierra: una pertiga de tierra. */
    static final String EARTHING_POLE = "EARTHING_POLE";

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
        if (!covers(shift, taskTrack, connectedTrack)) {
            throw new ShiftException("Shift " + shift.getCode() + " works on tracks " + tracksOf(shift)
                    + " but the task belongs to track " + taskTrack
                    + (connectedTrack == null ? "" : " and connects with track " + connectedTrack));
        }
    }

    /**
     * Una inspeccion registrada en un turno sale en su parte, asi que tiene que ser de esa noche y de
     * esas vias: un turno en curso o cerrado (la de madrugada se apunta a menudo al terminar) que
     * recorra la via de su activo, o la otra si el activo une dos. Uno planificado o cancelado no se
     * ha trabajado, y uno de otra via pondria la inspeccion en el parte de quien no la hizo.
     */
    static void requireRecordsInspectionsOf(MaintenanceShift shift, CatenaryAsset asset) {
        if (shift.getStatus() != ShiftStatus.IN_PROGRESS && shift.getStatus() != ShiftStatus.CLOSED) {
            throw new ShiftException("Shift " + shift.getCode() + " is " + shift.getStatus()
                    + "; an inspection is recorded in a shift in progress or closed");
        }
        if (!covers(shift, asset.getTrackId(), asset.getConnectedTrackId())) {
            throw new ShiftException("Shift " + shift.getCode() + " works on tracks " + tracksOf(shift)
                    + " but asset " + asset.getCode() + " is on track " + asset.getTrackId()
                    + (asset.getConnectedTrackId() == null ? "" : " and connects with track " + asset.getConnectedTrackId()));
        }
    }

    /** Sin via no hay nada que comprobar; con ella, vale la suya o la conectada. */
    private static boolean covers(MaintenanceShift shift, Long track, Long connectedTrack) {
        return track == null || shift.worksOn(track) || (connectedTrack != null && shift.worksOn(connectedTrack));
    }

    private static List<Long> tracksOf(MaintenanceShift shift) {
        return shift.getTrackIds().stream().sorted().toList();
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

    /**
     * El equipo que exigen los tipos de la tarea ({@link TaskEquipment}, {@code V24}) lo lleva el turno: la
     * plataforma aislada, un vehiculo con esa capacidad; la puesta a tierra, un vehiculo con ella o una
     * pertiga de tierra activa entre los instrumentos. Las capacidades llegan de mto-resources con el modelo
     * del vehiculo.
     */
    static void requireEquipment(MaintenanceShift shift, MaintenanceTask task) {
        List<String> missing = task.getTaskTypes().stream()
                .flatMap(type -> type.getRequiredEquipment().stream())
                .distinct()
                .sorted()
                .filter(equipment -> !carries(shift, equipment))
                .map(equipment -> switch (equipment) {
                    case INSULATED_PLATFORM -> "an insulated platform (a vehicle whose model has INSULATED_PLATFORM)";
                    case EARTHING -> "earthing (a vehicle whose model has EARTHING, or an EARTHING_POLE among its instruments)";
                })
                .toList();
        if (!missing.isEmpty()) {
            throw new ShiftException("Task " + task.getSequence() + " of order " + task.getOrder().getCode() + " needs "
                    + String.join(" and ", missing) + ", and shift " + shift.getCode() + " does not carry it");
        }
    }

    /** Si el turno lleva ese equipo. */
    static boolean carries(MaintenanceShift shift, TaskEquipment equipment) {
        boolean vehicle = shift.getVehicles().stream().anyMatch(shiftVehicle -> shiftVehicle.getVehicle().hasCapability(equipment.name()));
        if (vehicle || equipment != TaskEquipment.EARTHING) {
            return vehicle;
        }
        return shift.getInstruments().stream()
                .anyMatch(instrument -> EARTHING_POLE.equals(instrument.getKind()) && Boolean.TRUE.equals(instrument.getActive()));
    }

    static void requireInProgress(MaintenanceShift shift) {
        if (!shift.isInProgress()) {
            throw new ShiftException("Shift " + shift.getCode() + " is " + shift.getStatus() + ", tasks can only be worked in a shift in progress");
        }
    }
}
