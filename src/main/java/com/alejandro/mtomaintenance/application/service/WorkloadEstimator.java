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
}
