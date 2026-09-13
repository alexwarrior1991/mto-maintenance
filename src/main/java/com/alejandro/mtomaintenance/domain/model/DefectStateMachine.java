package com.alejandro.mtomaintenance.domain.model;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;

/** Tabla de transiciones de un defecto. */
public final class DefectStateMachine {

    private DefectStateMachine() {
    }

    public static boolean canLinkOrder(DefectStatus from) {
        return from == DefectStatus.OPEN || from == DefectStatus.IN_PROGRESS;
    }

    public static boolean canResolve(DefectStatus from) {
        return from == DefectStatus.OPEN || from == DefectStatus.IN_PROGRESS;
    }

    public static boolean canClose(DefectStatus from) {
        return from == DefectStatus.RESOLVED;
    }

    public static boolean canDiscard(DefectStatus from) {
        return from == DefectStatus.OPEN;
    }

    public static boolean isTerminal(DefectStatus status) {
        return status == DefectStatus.CLOSED || status == DefectStatus.DISCARDED;
    }
}
