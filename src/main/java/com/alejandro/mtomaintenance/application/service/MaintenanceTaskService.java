package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.dto.inspection.CheckItemUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.task.CancelTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.CompleteTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksResponse;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskResponse;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.task.StartTaskRequest;

import java.util.List;
import java.util.UUID;

public interface MaintenanceTaskService {

    List<MaintenanceTaskResponse> findByOrder(UUID orderId);

    MaintenanceTaskResponse findById(UUID orderId, UUID taskId);

    MaintenanceTaskResponse create(UUID orderId, MaintenanceTaskRequest request);

    MaintenanceTaskResponse update(UUID orderId, UUID taskId, MaintenanceTaskUpdateRequest request);

    /** Como {@link #update}, y ademas vacia lo que el parche pone a null. */
    MaintenanceTaskResponse patch(UUID orderId, UUID taskId, MergePatch<MaintenanceTaskUpdateRequest> patch);

    /** Preventivo perfil a perfil: una tarea por PROFILE del tramo, ordenadas por kp. Idempotente. */
    GeneratePreventiveTasksResponse generatePreventiveTasks(UUID orderId, GeneratePreventiveTasksRequest request);

    /**
     * El ambito en el que las generaciones de una orden hacen fila: su EP ({@code EP-14}) o, sin EP, la
     * propia orden. Es el {@code scope} de la operacion de fondo cuando se pide {@code respond-async}, y
     * antes de darlo comprueba lo que ya se puede decir (que la orden genera y que esta en el ambito de
     * quien escribe): lo que va a fallar no se encola.
     */
    String generationScope(UUID orderId);

    MaintenanceTaskResponse start(UUID orderId, UUID taskId, StartTaskRequest request);

    MaintenanceTaskResponse complete(UUID orderId, UUID taskId, CompleteTaskRequest request);

    MaintenanceTaskResponse cancel(UUID orderId, UUID taskId, CancelTaskRequest request);

    MaintenanceTaskResponse updateCheckItem(UUID orderId, UUID taskId, UUID itemId, CheckItemUpdateRequest request);

    MaintenanceTaskResponse patchCheckItem(UUID orderId, UUID taskId, UUID itemId, MergePatch<CheckItemUpdateRequest> patch);

    /** Asigna una tarea a un turno comprobando via y ventana de posesion. */
    MaintenanceTaskResponse assignToShift(UUID shiftId, UUID taskId);

    /**
     * Saca del turno una tarea abierta: vuelve a su orden pendiente y sin turno, como lo no terminado al
     * cerrar. Una terminada o cancelada se queda, porque es lo que se hizo en el.
     */
    MaintenanceTaskResponse unassignFromShift(UUID shiftId, UUID taskId);

    /** Tareas del turno, opcionalmente solo las de un estado (COMPLETED = perfiles revisados). */
    List<MaintenanceTaskResponse> findByShift(UUID shiftId, MaintenanceTaskStatus status);
}
