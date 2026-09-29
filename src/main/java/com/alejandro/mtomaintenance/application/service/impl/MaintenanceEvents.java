package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.messaging.DomainEvent;
import com.alejandro.mtomaintenance.application.exception.StockRejectedException;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspection;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspectionItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTeam;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Los eventos que este servicio cuenta de si mismo: su entidad, su nombre y lo que viaja en
 * {@code values}. Es la definicion del contrato con {@code mto-notification} ({@code docs/06-messaging.md}),
 * y por eso vive en un solo sitio y es publica: {@code MessagingContractExamplesTest} la usa para
 * comprobar que cada ejemplo versionado en {@code docs/messaging/examples} sigue siendo lo que se
 * publica.
 *
 * <p>En el contrato solo se anaden claves. Lo que viaja es lo que hace falta para avisar y para
 * enlazar (codigos, ids, estados, quien), nunca un texto largo ni nada que huela a secreto
 * ({@link DomainEvent} lo rechaza). Un valor nulo viaja como nulo: para el consumidor «sin equipo»
 * es informacion.</p>
 */
public final class MaintenanceEvents {

    public static final String ORDER = "order";
    public static final String DEFECT = "defect";
    public static final String INSPECTION = "inspection";
    public static final String SHIFT = "shift";
    public static final String MATERIAL = "material";
    public static final String ASSET = "asset";
    public static final String PREVENTIVE = "preventive";

    public static final String CREATED = "created";
    public static final String STATUS_CHANGED = "status-changed";
    public static final String REASSIGNED = "reassigned";
    public static final String ITEM_FAILED = "item-failed";
    public static final String DEFECT_CREATED = "defect-created";
    public static final String CORRECTIVE_ORDER_CREATED = "corrective-order-created";
    public static final String STARTED = "started";
    public static final String CLOSED = "closed";
    public static final String REJECTED = "rejected";
    public static final String FAILED = "failed";
    public static final String IN_DOUBT = "in-doubt";
    public static final String DISABLED = "disabled";
    public static final String DUE_SOON = "due-soon";

    private MaintenanceEvents() {
    }

    // ------------------------------------------------------------------------------- orders

    /** La orden acaba de crearse, en DRAFT (o directamente IN_PROGRESS si es URGENT y se arranca). */
    public static DomainEvent orderCreated(MaintenanceOrder order, String comment) {
        Map<String, Object> values = order(order);
        values.put("comment", comment);
        return new DomainEvent(ORDER, id(order.getId()), CREATED, values);
    }

    /**
     * La orden cambio de estado. {@code assignedUser} viaja siempre: con {@code to = ASSIGNED} es a
     * quien hay que avisar.
     */
    public static DomainEvent orderStatusChanged(MaintenanceOrder order, String from, String to, String comment) {
        Map<String, Object> values = order(order);
        values.put("from", from);
        values.put("to", to);
        values.put("comment", comment);
        return new DomainEvent(ORDER, id(order.getId()), STATUS_CHANGED, values);
    }

    /** Reasignada sin cambiar de estado (a media ejecucion): equipo o persona nuevos. */
    public static DomainEvent orderReassigned(MaintenanceOrder order, String comment) {
        Map<String, Object> values = order(order);
        values.put("comment", comment);
        return new DomainEvent(ORDER, id(order.getId()), REASSIGNED, values);
    }

    private static Map<String, Object> order(MaintenanceOrder order) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", order.getCode());
        values.put("title", order.getTitle());
        values.put("type", name(order.getType()));
        values.put("priority", name(order.getPriority()));
        values.put("status", name(order.getStatus()));
        asset(values, order.getAsset());
        values.put("executionPackageId", order.getExecutionPackageId());
        values.put("trackId", order.getTrackId());
        values.put("stationId", order.getStationId());
        values.put("startKp", order.getStartKp());
        values.put("endKp", order.getEndKp());
        values.put("plannedDate", order.getPlannedDate());
        values.put("actualStartDate", order.getActualStartDate());
        values.put("actualEndDate", order.getActualEndDate());
        team(values, order.getTeam());
        values.put("assignedUser", order.getAssignedUser());
        values.put("originInspectionId", order.getOriginInspection() == null ? null : order.getOriginInspection().getId());
        values.put("originDefectId", order.getOriginDefect() == null ? null : order.getOriginDefect().getId());
        values.put("stockProjectId", order.getStockProjectId());
        return values;
    }

    // ------------------------------------------------------------------------------ defects

    public static DomainEvent defectCreated(CatenaryDefect defect, String comment) {
        Map<String, Object> values = defect(defect);
        values.put("comment", comment);
        return new DomainEvent(DEFECT, id(defect.getId()), CREATED, values);
    }

    public static DomainEvent defectStatusChanged(CatenaryDefect defect, String from, String to, String comment) {
        Map<String, Object> values = defect(defect);
        values.put("from", from);
        values.put("to", to);
        values.put("comment", comment);
        return new DomainEvent(DEFECT, id(defect.getId()), STATUS_CHANGED, values);
    }

    private static Map<String, Object> defect(CatenaryDefect defect) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", defect.getCode());
        values.put("severity", name(defect.getSeverity()));
        values.put("status", name(defect.getStatus()));
        values.put("description", defect.getDescription());
        asset(values, defect.getAsset());
        values.put("executionPackageId", defect.getExecutionPackageId());
        values.put("trackId", defect.getTrackId());
        values.put("stationId", defect.getStationId());
        values.put("startKp", defect.getStartKp());
        values.put("endKp", defect.getEndKp());
        values.put("inspectionId", defect.getInspection() == null ? null : defect.getInspection().getId());
        values.put("inspectionCode", defect.getInspection() == null ? null : defect.getInspection().getCode());
        values.put("orderId", defect.getOrder() == null ? null : defect.getOrder().getId());
        values.put("orderCode", defect.getOrder() == null ? null : defect.getOrder().getCode());
        values.put("foundInTaskId", defect.getFoundInTask() == null ? null : defect.getFoundInTask().getId());
        values.put("resolvedInShiftCode", defect.getResolvedInShift() == null ? null : defect.getResolvedInShift().getCode());
        values.put("detectedAt", defect.getDetectedAt());
        values.put("repairPlannedDate", defect.getRepairPlannedDate());
        values.put("resolvedAt", defect.getResolvedAt());
        return values;
    }

    // -------------------------------------------------------------------------- inspections

    public static DomainEvent inspectionCreated(MaintenanceInspection inspection) {
        Map<String, Object> values = inspection(inspection);
        values.put("itemCount", inspection.getItems().size());
        return new DomainEvent(INSPECTION, id(inspection.getId()), CREATED, values);
    }

    /** Un punto de la checklist acaba de quedar en DEFECT. */
    public static DomainEvent inspectionItemFailed(MaintenanceInspection inspection, MaintenanceInspectionItem item) {
        Map<String, Object> values = inspection(inspection);
        values.put("itemId", item.getId());
        values.put("itemCode", item.getCode());
        values.put("itemLabel", item.getLabel());
        values.put("itemResult", name(item.getItemResult()));
        values.put("measuredValue", item.getMeasuredValue());
        values.put("valueAfterAdjustment", item.getValueAfterAdjustment());
        values.put("unit", item.getUnit());
        values.put("minValue", item.getMinValue());
        values.put("maxValue", item.getMaxValue());
        values.put("notes", item.getNotes());
        return new DomainEvent(INSPECTION, id(inspection.getId()), ITEM_FAILED, values);
    }

    /** La inspeccion genero su defecto (el defecto publica ademas su propio {@code defect.created}). */
    public static DomainEvent inspectionDefectCreated(MaintenanceInspection inspection, CatenaryDefect defect) {
        Map<String, Object> values = inspection(inspection);
        values.put("defectId", defect.getId());
        values.put("defectCode", defect.getCode());
        values.put("severity", name(defect.getSeverity()));
        values.put("description", defect.getDescription());
        return new DomainEvent(INSPECTION, id(inspection.getId()), DEFECT_CREATED, values);
    }

    /** La inspeccion genero su orden correctiva (la orden publica ademas su propio {@code order.created}). */
    public static DomainEvent inspectionCorrectiveOrderCreated(MaintenanceInspection inspection, MaintenanceOrder order) {
        Map<String, Object> values = inspection(inspection);
        values.put("orderId", order.getId());
        values.put("orderCode", order.getCode());
        values.put("orderType", name(order.getType()));
        values.put("priority", name(order.getPriority()));
        values.put("defectId", order.getOriginDefect() == null ? null : order.getOriginDefect().getId());
        values.put("defectCode", order.getOriginDefect() == null ? null : order.getOriginDefect().getCode());
        return new DomainEvent(INSPECTION, id(inspection.getId()), CORRECTIVE_ORDER_CREATED, values);
    }

    private static Map<String, Object> inspection(MaintenanceInspection inspection) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", inspection.getCode());
        values.put("result", name(inspection.getResult()));
        values.put("inspectionKind", name(inspection.getInspectionKind()));
        values.put("inspectionDate", inspection.getInspectionDate());
        values.put("inspector", inspection.getInspector());
        asset(values, inspection.getAsset());
        values.put("executionPackageId", inspection.getExecutionPackageId());
        values.put("trackId", inspection.getTrackId());
        values.put("stationId", inspection.getStationId());
        values.put("kp", inspection.getKp());
        values.put("originOrderId", inspection.getOriginOrder() == null ? null : inspection.getOriginOrder().getId());
        values.put("originOrderCode", inspection.getOriginOrder() == null ? null : inspection.getOriginOrder().getCode());
        values.put("shiftId", inspection.getShift() == null ? null : inspection.getShift().getId());
        values.put("shiftCode", inspection.getShift() == null ? null : inspection.getShift().getCode());
        return values;
    }

    // ------------------------------------------------------------------------------- shifts

    public static DomainEvent shiftStarted(MaintenanceShift shift) {
        return new DomainEvent(SHIFT, id(shift.getId()), STARTED, shift(shift));
    }

    public static DomainEvent shiftClosed(MaintenanceShift shift) {
        return new DomainEvent(SHIFT, id(shift.getId()), CLOSED, shift(shift));
    }

    private static Map<String, Object> shift(MaintenanceShift shift) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", shift.getCode());
        values.put("status", name(shift.getStatus()));
        values.put("shiftDate", shift.getShiftDate());
        values.put("possessionType", name(shift.getPossessionType()));
        team(values, shift.getTeam());
        values.put("baseName", shift.getBaseName());
        values.put("vehicle", shift.getVehicle());
        values.put("trackIds", shift.getTrackIds() == null ? List.of() : shift.getTrackIds().stream().sorted().toList());
        values.put("executionPackageId", shift.getExecutionPackageId());
        values.put("startKp", shift.getStartKp());
        values.put("endKp", shift.getEndKp());
        values.put("plannedStart", shift.getPlannedStart());
        values.put("plannedEnd", shift.getPlannedEnd());
        values.put("actualStart", shift.getActualStart());
        values.put("actualEnd", shift.getActualEnd());
        values.put("voltageCutoffAt", shift.getVoltageCutoffAt());
        values.put("netWorkMinutes", shift.getNetWorkMinutes());
        return values;
    }

    // ---------------------------------------------------------------------------- materials

    /** mto-stock respondio que no ({@code stockErrorCode}: {@code STK-001} es sin existencias). */
    public static DomainEvent materialRejected(MaintenanceMaterialUsage usage, String step, StockRejectedException rejection) {
        Map<String, Object> values = material(usage, step, rejection.getMessage());
        values.put("stockErrorCode", rejection.getStockErrorCode());
        values.put("stockHttpStatus", rejection.getStatus());
        return new DomainEvent(MATERIAL, id(usage.getId()), REJECTED, values);
    }

    /** mto-stock no respondio, y la linea no tiene ninguna peticion en duda (o no llego a mandarla). */
    public static DomainEvent materialFailed(MaintenanceMaterialUsage usage, String step, RuntimeException failure) {
        return new DomainEvent(MATERIAL, id(usage.getId()), FAILED, material(usage, step, failure.getMessage()));
    }

    /**
     * mto-stock no respondio a una reserva o a una salida ya enviada ({@code request}): quiza la
     * aplico. El servicio la repite con su clave antes de hacer nada mas con la linea.
     */
    public static DomainEvent materialInDoubt(MaintenanceMaterialUsage usage, String step, RuntimeException failure) {
        return new DomainEvent(MATERIAL, id(usage.getId()), IN_DOUBT, material(usage, step, failure.getMessage()));
    }

    private static Map<String, Object> material(MaintenanceMaterialUsage usage, String step, String reason) {
        MaintenanceOrder order = usage.getOrder();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("orderId", order == null ? null : order.getId());
        values.put("orderCode", order == null ? null : order.getCode());
        values.put("orderStatus", order == null ? null : name(order.getStatus()));
        values.put("taskId", usage.getTask() == null ? null : usage.getTask().getId());
        values.put("materialId", usage.getMaterialId());
        values.put("materialCode", usage.getMaterialCode());
        values.put("materialDescription", usage.getMaterialDescriptionSnapshot());
        values.put("warehouseId", usage.getWarehouseId());
        values.put("plannedQuantity", usage.getPlannedQuantity());
        values.put("consumedQuantity", usage.getConsumedQuantity());
        values.put("unit", usage.getUnit());
        values.put("stockReservationId", usage.getStockReservationId());
        values.put("stockSyncStatus", name(usage.getStockSyncStatus()));
        values.put("request", name(usage.getStockRequestInDoubt()));
        values.put("step", step);
        values.put("reason", reason);
        return values;
    }

    // ------------------------------------------------------------------------------- assets

    /** Alguien desactivo el activo aqui (no el origen): sobrevive a lo que digan los datos maestros. */
    public static DomainEvent assetDisabled(CatenaryAsset asset) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", asset.getCode());
        values.put("name", asset.getName());
        values.put("type", name(asset.getType()));
        values.put("executionPackageId", asset.getExecutionPackageId());
        values.put("trackId", asset.getTrackId());
        values.put("stationId", asset.getStationId());
        values.put("startKp", asset.getStartKp());
        values.put("endKp", asset.getEndKp());
        values.put("sourceService", asset.getSourceService());
        values.put("sourceEntityId", asset.getSourceEntityId());
        values.put("enabledAtSource", asset.getEnabledAtSource());
        values.put("disabledLocally", asset.getDisabledLocally());
        values.put("preventiveIntervalDays", asset.getPreventiveIntervalDays());
        return new DomainEvent(ASSET, id(asset.getId()), DISABLED, values);
    }

    // --------------------------------------------------------------------------- preventives

    /** Un activo cuyo preventivo vence dentro del horizonte, o ya vencio ({@code dueAt} nulo: nunca se hizo). */
    public record DueAsset(UUID id, String code, String name, CatenaryAssetType type, Long trackId,
                           Long executionPackageId, Instant lastPreventiveCompletedAt, Instant dueAt, boolean overdue) {
    }

    /**
     * El aviso diario: cuantos activos vencen en los proximos {@code horizonDays} dias y una muestra
     * de ellos, los que vencen antes primero. La entidad es {@code preventive} y su id la fecha, la
     * misma con la que se calcula el {@code operationId}: la repeticion de un dia es un duplicado.
     */
    public static DomainEvent preventiveDueSoon(LocalDate date, int horizonDays, List<DueAsset> due, int sampleSize) {
        List<Map<String, Object>> sample = new ArrayList<>();
        due.stream().limit(sampleSize).forEach(asset -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("assetId", asset.id());
            entry.put("assetCode", asset.code());
            entry.put("assetName", asset.name());
            entry.put("assetType", name(asset.type()));
            entry.put("trackId", asset.trackId());
            entry.put("executionPackageId", asset.executionPackageId());
            entry.put("lastPreventiveCompletedAt", asset.lastPreventiveCompletedAt());
            entry.put("dueAt", asset.dueAt());
            entry.put("overdue", asset.overdue());
            sample.add(entry);
        });

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("date", date);
        values.put("horizonDays", horizonDays);
        values.put("count", due.size());
        values.put("overdueCount", (int) due.stream().filter(DueAsset::overdue).count());
        values.put("sampleSize", sampleSize);
        values.put("assets", sample);
        return new DomainEvent(PREVENTIVE, date.toString(), DUE_SOON, values);
    }

    // ------------------------------------------------------------------------------ helpers

    private static void asset(Map<String, Object> values, CatenaryAsset asset) {
        values.put("assetId", asset == null ? null : asset.getId());
        values.put("assetCode", asset == null ? null : asset.getCode());
        values.put("assetName", asset == null ? null : asset.getName());
        values.put("assetType", asset == null ? null : name(asset.getType()));
    }

    private static void team(Map<String, Object> values, MaintenanceTeam team) {
        values.put("teamId", team == null ? null : team.getId());
        values.put("teamCode", team == null ? null : team.getCode());
        values.put("teamName", team == null ? null : team.getName());
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /** Los ganchos corren despues de guardar: una entidad sin id aqui es un error de programacion. */
    private static String id(UUID id) {
        return Objects.requireNonNull(id, "the entity must be saved before publishing its event").toString();
    }
}
