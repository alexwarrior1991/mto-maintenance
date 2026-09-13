package com.alejandro.mtomaintenance.domain.model;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;

/** Tabla de transiciones de un turno. */
public final class ShiftStateMachine {

    private ShiftStateMachine() {
    }

    public static boolean canStart(ShiftStatus from) {
        return from == ShiftStatus.PLANNED;
    }

    public static boolean canClose(ShiftStatus from) {
        return from == ShiftStatus.IN_PROGRESS;
    }

    public static boolean canCancel(ShiftStatus from) {
        return from == ShiftStatus.PLANNED || from == ShiftStatus.IN_PROGRESS;
    }

    public static boolean allowsUpdate(ShiftStatus from) {
        return from == ShiftStatus.PLANNED || from == ShiftStatus.IN_PROGRESS;
    }
}
