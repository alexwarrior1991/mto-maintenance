package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.service.WorkloadEstimator;
import com.alejandro.mtomaintenance.domain.model.KilometricRange;
import com.alejandro.mtomaintenance.domain.model.WorkloadEstimate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TaskUnit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tiempo estandar del plan OCS: {@code fixed + porUnidad * unidades}. Los tipos por unidad (UNIT,
 * PROFILE, SPAN, DEFECT) cuentan una vez por tarea; los tipos por kilometro (RG-08 "60 min + 3/km")
 * cuentan una vez por orden sobre la longitud del tramo, porque el hilo se recorre entero y no perfil
 * a perfil.
 */
@Service
class WorkloadEstimatorImpl implements WorkloadEstimator {

    @Override
    public WorkloadEstimate estimate(MaintenanceTask task) {
        return estimate(List.of(task), null);
    }

    @Override
    public WorkloadEstimate estimate(Collection<MaintenanceTask> tasks) {
        return estimate(tasks, null);
    }

    @Override
    public WorkloadEstimate estimate(MaintenanceOrder order) {
        KilometricRange range = order.getStartKp() != null && order.getEndKp() != null
                ? new KilometricRange(order.getStartKp(), order.getEndKp())
                : null;
        return estimate(order.getTasks(), range);
    }

    private static WorkloadEstimate estimate(Collection<MaintenanceTask> tasks, KilometricRange range) {
        BigDecimal minutes = BigDecimal.ZERO;
        Set<MaintenanceTaskType> perKilometre = new LinkedHashSet<>();

        for (MaintenanceTask task : tasks) {
            if (task.getStatus() == MaintenanceTaskStatus.CANCELLED) {
                continue;
            }
            for (MaintenanceTaskType type : task.getTaskTypes()) {
                if (type.getUnit() == TaskUnit.KM) {
                    perKilometre.add(type);
                } else {
                    minutes = minutes.add(type.getFixedMinutes()).add(type.getStandardMinutesPerUnit());
                }
            }
        }

        BigDecimal kilometres = range == null ? BigDecimal.ZERO : range.lengthKm();
        for (MaintenanceTaskType type : perKilometre) {
            minutes = minutes.add(type.getFixedMinutes()).add(type.getStandardMinutesPerUnit().multiply(kilometres));
        }

        return WorkloadEstimate.ofMinutes(minutes.setScale(2, java.math.RoundingMode.HALF_UP));
    }
}
