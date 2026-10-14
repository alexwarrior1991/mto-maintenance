package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.domain.model.WorkloadEstimate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;

import java.util.Collection;

/** Carga estimada a partir de los tiempos estandar del catalogo de tipos de tarea. */
public interface WorkloadEstimator {

    WorkloadEstimate estimate(MaintenanceTask task);

    WorkloadEstimate estimate(Collection<MaintenanceTask> tasks);

    WorkloadEstimate estimate(MaintenanceOrder order);

    /** La de la orden con unas tareas mas que aun no existen: lo que daria generarlas ({@code dryRun}). */
    WorkloadEstimate estimate(MaintenanceOrder order, Collection<MaintenanceTask> planned);
}
