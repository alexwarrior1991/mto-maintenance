package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.service.WorkloadEstimator;
import com.alejandro.mtomaintenance.domain.model.KilometricRange;
import com.alejandro.mtomaintenance.domain.model.WorkloadEstimate;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTaskType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TaskUnit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Tiempo estandar del plan OCS: {@code fixed + porUnidad * unidades}. Los tipos por unidad (UNIT,
 * PROFILE, SPAN, DEFECT) cuentan una vez por tarea; los tipos por kilometro (RG-08 "60 min + 3/km")
 * cuentan una vez por via sobre la longitud que se recorre en ella, porque el hilo se recorre entero y
 * no perfil a perfil: en la via del tramo de la orden, el tramo; en otra via del EP (una orden puede
 * abarcar varias), del primer al ultimo kp de lo que lleva esos tipos en ella.
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
        return estimate(order, List.of());
    }

    @Override
    public WorkloadEstimate estimate(MaintenanceOrder order, Collection<MaintenanceTask> planned) {
        List<MaintenanceTask> tasks = new ArrayList<>(order.getTasks());
        tasks.addAll(planned);
        return estimate(tasks, order);
    }

    private static WorkloadEstimate estimate(Collection<MaintenanceTask> tasks, MaintenanceOrder order) {
        BigDecimal minutes = BigDecimal.ZERO;
        // Por via, los tipos por kilometro que se hacen en ella y lo que ocupan los activos que los llevan.
        Map<Long, Set<MaintenanceTaskType>> perKilometre = new LinkedHashMap<>();
        Map<Long, List<BigDecimal>> kpsByTrack = new HashMap<>();

        for (MaintenanceTask task : tasks) {
            if (task.getStatus() == MaintenanceTaskStatus.CANCELLED) {
                continue;
            }
            Long track = trackOf(task, order);
            for (MaintenanceTaskType type : task.getTaskTypes()) {
                if (type.getUnit() == TaskUnit.KM) {
                    perKilometre.computeIfAbsent(track, key -> new LinkedHashSet<>()).add(type);
                    CatenaryAsset asset = task.getAsset();
                    if (asset != null) {
                        List<BigDecimal> kps = kpsByTrack.computeIfAbsent(track, key -> new ArrayList<>());
                        Stream.of(asset.getStartKp(), asset.getEndKp()).filter(Objects::nonNull).forEach(kps::add);
                    }
                } else {
                    minutes = minutes.add(type.getFixedMinutes()).add(type.getStandardMinutesPerUnit());
                }
            }
        }

        for (Map.Entry<Long, Set<MaintenanceTaskType>> entry : perKilometre.entrySet()) {
            BigDecimal kilometres = lengthKm(entry.getKey(), kpsByTrack.getOrDefault(entry.getKey(), List.of()), order);
            for (MaintenanceTaskType type : entry.getValue()) {
                minutes = minutes.add(type.getFixedMinutes()).add(type.getStandardMinutesPerUnit().multiply(kilometres));
            }
        }

        return WorkloadEstimate.ofMinutes(minutes.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    /**
     * La via de una tarea es la de su activo; sin activo, o sin via, la de la orden. Sin orden todo va
     * junto, como siempre: no hay recorrido que medir.
     */
    private static Long trackOf(MaintenanceTask task, MaintenanceOrder order) {
        if (order == null) {
            return null;
        }
        CatenaryAsset asset = task.getAsset();
        if (asset != null && asset.getTrackId() != null) {
            return asset.getTrackId();
        }
        return order.getTrackId();
    }

    /**
     * En la via de la orden, su tramo (lo de siempre); en otra, lo que va del primer al ultimo kp de lo
     * que se hace en ella. Sin orden (una tarea suelta o una lista) no hay recorrido que medir.
     */
    private static BigDecimal lengthKm(Long track, List<BigDecimal> kps, MaintenanceOrder order) {
        if (order == null) {
            return BigDecimal.ZERO;
        }
        if (Objects.equals(track, order.getTrackId())) {
            return order.getStartKp() != null && order.getEndKp() != null
                    ? new KilometricRange(order.getStartKp(), order.getEndKp()).lengthKm()
                    : BigDecimal.ZERO;
        }
        if (kps.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new KilometricRange(Collections.min(kps), Collections.max(kps)).lengthKm();
    }
}
