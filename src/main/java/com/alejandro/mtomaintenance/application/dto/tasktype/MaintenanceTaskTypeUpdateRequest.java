package com.alejandro.mtomaintenance.application.dto.tasktype;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TaskEquipment;

import java.util.Set;

/**
 * Lo unico que cambia de un tipo de tarea ({@code PATCH /task-types/{code}}, {@code application/merge-patch+json}):
 * el equipo que exige al turno. El conjunto entero sustituye al anterior; {@code null} lo vacia.
 *
 * <pre>{@code
 * {"requiredEquipment": ["EARTHING", "INSULATED_PLATFORM"], "version": 0}
 * }</pre>
 *
 * @param requiredEquipment el equipo que exige ({@code INSULATED_PLATFORM}, {@code EARTHING})
 * @param version           la leida: si cambio desde entonces, 409 {@code CON-001}
 */
public record MaintenanceTaskTypeUpdateRequest(Set<TaskEquipment> requiredEquipment, Long version) {
}
