package com.alejandro.mtomaintenance.application.service.impl;

import static com.alejandro.mtomaintenance.application.service.impl.DomainGuard.domain;
import com.alejandro.mtomaintenance.application.dto.common.MergePatch;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageRequest;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageResponse;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageUpdateRequest;
import com.alejandro.mtomaintenance.application.exception.MaterialUsageException;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.exception.StaleVersionException;
import com.alejandro.mtomaintenance.application.exception.StockRejectedException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.mapper.MaterialUsageMapper;
import com.alejandro.mtomaintenance.application.service.MaintenanceMaterialUsageService;
import com.alejandro.mtomaintenance.domain.model.Quantity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockSyncStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceMaterialUsageRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class MaintenanceMaterialUsageServiceImpl implements MaintenanceMaterialUsageService {

    private final MaintenanceMaterialUsageRepository repository;
    private final MaintenanceTaskRepository taskRepository;
    private final MaterialUsageMapper mapper;
    private final MaintenanceLookups lookups;
    private final MaterialLineFactory lineFactory;
    private final MaterialStockSynchronizer stock;

    @Override
    @Transactional(readOnly = true)
    public List<MaterialUsageResponse> findByOrder(UUID orderId) {
        lookups.order(orderId);
        return repository.findByOrderIdOrderByCreatedAtAsc(orderId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public MaterialUsageResponse register(UUID orderId, MaterialUsageRequest request) {
        MaintenanceOrder order = lookups.order(orderId);
        if (order.isTerminal()) {
            throw new MaterialUsageException("Order " + order.getCode() + " is " + order.getStatus() + " and does not accept materials");
        }
        MaintenanceTask task = request.taskId() == null ? null : taskRepository.findByIdAndOrderId(request.taskId(), orderId)
                .orElseThrow(() -> new NotFoundException("Maintenance task", request.taskId()));

        MaintenanceMaterialUsage usage = lineFactory.buildLine(order, task, request.materialId(), request.materialCode(),
                request.warehouseId(), request.plannedQuantity(), request.unit(), Boolean.TRUE.equals(request.allowOverConsumption()));

        MaintenanceMaterialUsage saved = lineFactory.saveLine(usage);
        if (order.getStatus() != MaintenanceOrderStatus.DRAFT) {
            // Con la orden ya planificada, el material se reserva al momento.
            stock.reserve(saved);
            saved = repository.save(saved);
        }
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MaterialUsageResponse update(UUID orderId, UUID usageId, MaterialUsageUpdateRequest request) {
        return patch(orderId, usageId, MergePatch.of(request));
    }

    @Override
    @Transactional
    public MaterialUsageResponse patch(UUID orderId, UUID usageId, MergePatch<MaterialUsageUpdateRequest> patch) {
        MaintenanceMaterialUsage usage = line(orderId, usageId);
        MaterialUsageUpdateRequest request = patch.values();
        StaleVersionException.check("Material line " + usage.getMaterialCode(), usage.getVersion(), request.version());
        // Cantidades y permiso son siempre un valor: una linea no tiene nada que vaciar.
        PatchRules.requireClearable(patch, Set.of());
        MaintenanceOrder order = usage.getOrder();
        if (order.isTerminal()) {
            throw new MaterialUsageException("Order " + order.getCode() + " is " + order.getStatus() + " and its materials are frozen");
        }
        if (usage.getStockSyncStatus() == StockSyncStatus.CONSUMED) {
            // Lo que diga despues no llegaria a stock: completar solo liquida lo que no esta consumido.
            throw new MaterialUsageException("Material " + usage.getMaterialCode() + " was already consumed in stock and the line cannot change");
        }
        if (usage.isInDoubt() && (changes(request.plannedQuantity(), usage.getPlannedQuantity())
                || changes(request.consumedQuantity(), usage.getConsumedQuantity()))) {
            // Es lo que viaja en la peticion en duda, que se repite tal cual: con otra cantidad stock no
            // la reconoceria (409 IDEM-001), y lo que quiza ya hizo se quedaria alli sin nadie que lo sepa.
            throw new MaterialUsageException("Material " + usage.getMaterialCode() + " has a "
                    + usage.getStockRequestInDoubt().name().toLowerCase(Locale.ROOT)
                    + " sent to stock without an answer yet; sync the line before changing its quantities");
        }
        if (request.allowOverConsumption() != null) {
            usage.setAllowOverConsumption(request.allowOverConsumption());
        }
        if (request.plannedQuantity() != null) {
            // La misma cantidad no es un cambio: un formulario reenvia la linea entera para apuntar lo consumido.
            if (usage.getStockReservationId() != null && changes(request.plannedQuantity(), usage.getPlannedQuantity())) {
                throw new MaterialUsageException("The planned quantity of a reserved line cannot change; remove it and register it again");
            }
            BigDecimal planned = domain(() -> new Quantity(request.plannedQuantity(), usage.getUnit())).amount();
            if (planned.compareTo(usage.getConsumedQuantity()) < 0 && !usage.getAllowOverConsumption()) {
                // La misma regla que al subir lo consumido; sin esto la rompia el CHECK de la base, con un 500.
                throw new MaterialUsageException("Planned quantity " + planned + " is below the " + usage.getConsumedQuantity() + " "
                        + usage.getUnit() + " already consumed, and over-consumption is not allowed for this line");
            }
            usage.setPlannedQuantity(planned);
        }
        if (request.consumedQuantity() != null) {
            Quantity consumed = domain(() -> new Quantity(request.consumedQuantity(), usage.getUnit()));
            if (consumed.amount().compareTo(usage.getPlannedQuantity()) > 0 && !usage.getAllowOverConsumption()) {
                throw new MaterialUsageException("Consumed quantity " + consumed.amount() + " exceeds the planned "
                        + usage.getPlannedQuantity() + " " + usage.getUnit() + " and over-consumption is not allowed for this line");
            }
            usage.setConsumedQuantity(consumed.amount());
        }
        if (!usage.getAllowOverConsumption() && usage.getConsumedQuantity().compareTo(usage.getPlannedQuantity()) > 0) {
            // Solo llega quitando el permiso a una linea que ya consumio de mas: las cantidades se comprueban
            // arriba. Sin esto la rechazaba el CHECK de la base, con un 409 generico.
            throw new MaterialUsageException("Material " + usage.getMaterialCode() + " already consumed " + usage.getConsumedQuantity()
                    + " " + usage.getUnit() + ", more than the planned " + usage.getPlannedQuantity()
                    + "; over-consumption cannot be disallowed unless the consumed quantity goes back within the plan");
        }
        return mapper.toResponse(repository.save(usage));
    }

    /**
     * {@code noRollbackFor}: si stock falla o dice que no, la respuesta es el error, pero la linea
     * guarda lo que haya pasado (el paso que se hizo antes de fallar, y el motivo).
     */
    @Override
    @Transactional(noRollbackFor = {StockUnavailableException.class, StockRejectedException.class})
    public MaterialUsageResponse sync(UUID orderId, UUID usageId) {
        MaintenanceMaterialUsage usage = line(orderId, usageId);
        stock.syncNow(usage);
        return mapper.toResponse(repository.save(usage));
    }

    @Override
    @Transactional
    public void remove(UUID orderId, UUID usageId) {
        MaintenanceMaterialUsage usage = line(orderId, usageId);
        MaintenanceOrder order = usage.getOrder();
        if (order.isTerminal()) {
            throw new MaterialUsageException("Order " + order.getCode() + " is " + order.getStatus() + " and its materials are frozen");
        }
        if (usage.getStockSyncStatus() == StockSyncStatus.CONSUMED) {
            throw new MaterialUsageException("Material " + usage.getMaterialCode() + " was already consumed in stock; the line cannot be removed");
        }
        // Stock primero: si no responde, la excepcion deshace la transaccion y la linea sigue, porque
        // su reserva seguiria reteniendo material alli.
        stock.releaseNow(usage);
        order.getMaterials().remove(usage);
        repository.delete(usage);
    }

    private static boolean changes(BigDecimal requested, BigDecimal current) {
        return requested != null && requested.compareTo(current) != 0;
    }

    private MaintenanceMaterialUsage line(UUID orderId, UUID usageId) {
        lookups.order(orderId);
        return repository.findByIdAndOrderId(usageId, orderId)
                .orElseThrow(() -> new NotFoundException("Material usage", usageId));
    }
}
