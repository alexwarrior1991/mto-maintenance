package com.alejandro.mtomaintenance.domain.model;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;

import java.util.EnumSet;
import java.util.Set;

import static com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus.ASSIGNED;
import static com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus.DRAFT;
import static com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus.IN_PROGRESS;
import static com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus.PLANNED;

/**
 * Tabla de transiciones de una orden. Es la unica fuente de verdad: el servicio pregunta aqui y no
 * repite la tabla en cada metodo.
 */
public final class OrderStateMachine {

    private OrderStateMachine() {
    }

    public static boolean canPlan(MaintenanceOrderStatus from) {
        return from == DRAFT;
    }

    /** ASSIGNED admite reasignar; IN_PROGRESS reasigna sin cambiar de estado. */
    public static boolean canAssign(MaintenanceOrderStatus from) {
        return from == PLANNED || from == ASSIGNED || from == IN_PROGRESS;
    }

    /** URGENT es el correctivo de emergencia: arranca sin planificar. */
    public static boolean canStart(MaintenanceOrderStatus from, MaintenanceOrderType type) {
        return from == PLANNED || from == ASSIGNED || (from == DRAFT && type == MaintenanceOrderType.URGENT);
    }

    public static boolean canComplete(MaintenanceOrderStatus from) {
        return from == IN_PROGRESS;
    }

    public static boolean canCancel(MaintenanceOrderStatus from) {
        return CANCELLABLE.contains(from);
    }

    /** En DRAFT y PLANNED se edita todo; despues solo descripcion, prioridad y notas de cierre. */
    public static boolean allowsFullUpdate(MaintenanceOrderStatus from) {
        return from == DRAFT || from == PLANNED;
    }

    private static final Set<MaintenanceOrderStatus> CANCELLABLE = EnumSet.of(DRAFT, PLANNED, ASSIGNED, IN_PROGRESS);
}
