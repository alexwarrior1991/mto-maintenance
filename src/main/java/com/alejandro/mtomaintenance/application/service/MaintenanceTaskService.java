package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.inspection.CheckItemUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.task.CancelTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.CompleteTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksRequest;
import com.alejandro.mtomaintenance.application.dto.task.GeneratePreventiveTasksResponse;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskRequest;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskResponse;
import com.alejandro.mtomaintenance.application.dto.task.MaintenanceTaskUpdateRequest;
import com.alejandro.mtomaintenance.application.dto.task.StartTaskRequest;

import java.util.List;
import java.util.UUID;

public interface MaintenanceTaskService {

    List<MaintenanceTaskResponse> findByOrder(UUID orderId);

    MaintenanceTaskResponse findById(UUID orderId, UUID taskId);

    MaintenanceTaskResponse create(UUID orderId, MaintenanceTaskRequest request);

    MaintenanceTaskResponse update(UUID orderId, UUID taskId, MaintenanceTaskUpdateRequest request);

    /** Preventivo perfil a perfil: una tarea por PROFILE del tramo, ordenadas por kp. Idempotente. */
    GeneratePreventiveTasksResponse generatePreventiveTasks(UUID orderId, GeneratePreventiveTasksRequest request);

    MaintenanceTaskResponse start(UUID orderId, UUID taskId, StartTaskRequest request);

    MaintenanceTaskResponse complete(UUID orderId, UUID taskId, CompleteTaskRequest request);

    MaintenanceTaskResponse cancel(UUID orderId, UUID taskId, CancelTaskRequest request);

    MaintenanceTaskResponse updateCheckItem(UUID orderId, UUID taskId, UUID itemId, CheckItemUpdateRequest request);

    /** Asigna una tarea a un turno comprobando via y ventana de posesion. */
    MaintenanceTaskResponse assignToShift(UUID shiftId, UUID taskId);

    List<MaintenanceTaskResponse> findByShift(UUID shiftId);
}
