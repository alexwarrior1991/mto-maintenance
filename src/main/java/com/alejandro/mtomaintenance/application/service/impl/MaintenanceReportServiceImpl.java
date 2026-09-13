package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.report.MonthlyMaterialLineResponse;
import com.alejandro.mtomaintenance.application.dto.report.MonthlyReportResponse;
import com.alejandro.mtomaintenance.application.dto.report.ProgressReportResponse;
import com.alejandro.mtomaintenance.application.dto.report.ProgressRowResponse;
import com.alejandro.mtomaintenance.application.service.MaintenanceReportService;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Avance e informe mensual derivados de lo que ya esta guardado. Ningun dato se duplica para
 * informar: si algo no cuadra con el informe, la fuente es la orden, la tarea o el turno.
 */
@Service
@RequiredArgsConstructor
class MaintenanceReportServiceImpl implements MaintenanceReportService {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private final MaintenanceReportRepository repository;

    @Override
    @Transactional(readOnly = true)
    public ProgressReportResponse progress(Long executionPackageId, Long trackId, CatenaryAssetType assetType, Instant from, Instant to) {
        List<CatenaryAsset> assets = repository.findReportableAssets(executionPackageId, trackId, assetType);
        Set<UUID> worked = repository.findAssetIdsWorkedBetween(from, to);

        // Agrupado por (EP, via, tipo). Un perfil cuenta como revisado si tuvo una tarea completada o
        // una inspeccion en el rango; sin rango, si alguna vez lo tuvo (lastPreventiveCompletedAt).
        Map<String, List<CatenaryAsset>> groups = new LinkedHashMap<>();
        for (CatenaryAsset asset : assets) {
            groups.computeIfAbsent(asset.getExecutionPackageId() + "|" + asset.getTrackId() + "|" + asset.getType(), key -> new ArrayList<>()).add(asset);
        }

        List<ProgressRowResponse> rows = new ArrayList<>();
        long total = 0;
        long checked = 0;
        BigDecimal coveredKm = BigDecimal.ZERO;
        BigDecimal totalKm = BigDecimal.ZERO;
        for (List<CatenaryAsset> group : groups.values()) {
            CatenaryAsset first = group.getFirst();
            long groupChecked = group.stream().filter(asset -> isChecked(asset, worked, from, to)).count();
            BigDecimal groupTotalKm = BigDecimal.ZERO;
            BigDecimal groupCoveredKm = BigDecimal.ZERO;
            if (first.getType() == CatenaryAssetType.PROFILE) {
                // Cada perfil "cubre" el vano hasta el siguiente; el ultimo no cubre nada.
                for (int index = 0; index < group.size() - 1; index++) {
                    CatenaryAsset profile = group.get(index);
                    CatenaryAsset next = group.get(index + 1);
                    if (profile.getStartKp() == null || next.getStartKp() == null) {
                        continue;
                    }
                    BigDecimal span = next.getStartKp().subtract(profile.getStartKp()).divide(THOUSAND, 3, RoundingMode.HALF_UP);
                    groupTotalKm = groupTotalKm.add(span);
                    if (isChecked(profile, worked, from, to)) {
                        groupCoveredKm = groupCoveredKm.add(span);
                    }
                }
            }
            rows.add(new ProgressRowResponse(first.getExecutionPackageId(), first.getTrackId(), first.getType(),
                    group.size(), groupChecked, ratio(groupChecked, group.size()), groupCoveredKm, groupTotalKm));
            total += group.size();
            checked += groupChecked;
            coveredKm = coveredKm.add(groupCoveredKm);
            totalKm = totalKm.add(groupTotalKm);
        }
        return new ProgressReportResponse(from, to, total, checked, ratio(checked, total), coveredKm, totalKm, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyReportResponse monthly(YearMonth month, Long executionPackageId) {
        Instant from = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<MaintenanceShift> shifts = repository.findShiftsBetween(month.atDay(1), month.atEndOfMonth(), executionPackageId);
        int closed = (int) shifts.stream().filter(shift -> shift.getStatus() == ShiftStatus.CLOSED).count();
        int cancelled = (int) shifts.stream().filter(shift -> shift.getStatus() == ShiftStatus.CANCELLED).count();
        int netMinutes = shifts.stream().filter(shift -> shift.getNetWorkMinutes() != null).mapToInt(MaintenanceShift::getNetWorkMinutes).sum();
        BigDecimal coveredKm = shifts.stream()
                .filter(shift -> shift.getStatus() == ShiftStatus.CLOSED && shift.getStartKp() != null && shift.getEndKp() != null)
                .map(shift -> shift.getEndKp().subtract(shift.getStartKp()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(THOUSAND, 3, RoundingMode.HALF_UP);

        List<MaintenanceOrder> completedOrders = repository.findOrdersCompletedBetween(from, to, executionPackageId);
        List<MaintenanceTask> completedTasks = repository.findTasksCompletedBetween(from, to, executionPackageId);
        long profilesChecked = completedTasks.stream()
                .filter(task -> task.getAsset() != null && task.getAsset().getType() == CatenaryAssetType.PROFILE)
                .map(task -> task.getAsset().getId())
                .distinct()
                .count();
        int correctiveCreated = (int) repository.findOrdersCreatedBetween(from, to, executionPackageId).stream()
                .filter(order -> order.getType() == MaintenanceOrderType.CORRECTIVE || order.getType() == MaintenanceOrderType.URGENT)
                .count();

        Map<UUID, MonthlyMaterialLineResponse> materials = new LinkedHashMap<>();
        for (MaintenanceMaterialUsage usage : repository.findMaterialsOfOrders(completedOrders.stream().map(MaintenanceOrder::getId).toList())) {
            materials.merge(usage.getMaterialId(),
                    new MonthlyMaterialLineResponse(usage.getMaterialId(), usage.getMaterialCode(), usage.getUnit(), usage.getConsumedQuantity()),
                    (left, right) -> new MonthlyMaterialLineResponse(left.materialId(), left.materialCode(), left.unit(),
                            left.consumedQuantity().add(right.consumedQuantity())));
        }

        return new MonthlyReportResponse(
                month,
                executionPackageId,
                shifts.size(),
                closed,
                cancelled,
                netMinutes,
                closed == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(netMinutes).divide(BigDecimal.valueOf(closed), 1, RoundingMode.HALF_UP),
                completedOrders.size(),
                completedTasks.size(),
                profilesChecked,
                coveredKm,
                (int) repository.countDefectsDetectedBetween(from, to, executionPackageId),
                (int) repository.countDefectsResolvedBetween(from, to, executionPackageId),
                correctiveCreated,
                new ArrayList<>(materials.values())
        );
    }

    private static boolean isChecked(CatenaryAsset asset, Set<UUID> worked, Instant from, Instant to) {
        if (worked.contains(asset.getId())) {
            return true;
        }
        Instant last = asset.getLastPreventiveCompletedAt();
        if (last == null) {
            return false;
        }
        return (from == null || !last.isBefore(from)) && (to == null || !last.isAfter(to));
    }

    private static BigDecimal ratio(long part, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }
}
