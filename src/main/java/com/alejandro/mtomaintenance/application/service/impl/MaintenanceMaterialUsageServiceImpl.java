package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageRequest;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageResponse;
import com.alejandro.mtomaintenance.application.dto.material.MaterialUsageUpdateRequest;
import com.alejandro.mtomaintenance.application.exception.MaterialUsageException;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.mapper.MaterialUsageMapper;
import com.alejandro.mtomaintenance.application.service.MaintenanceMaterialUsageService;
import com.alejandro.mtomaintenance.domain.model.Quantity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceMaterialUsageRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
            if (order.getStockProjectId() == null) {
                stock.resolveProjectId(order).ifPresent(order::setStockProjectId);
            }
            stock.reserve(saved);
            saved = repository.save(saved);
        }
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MaterialUsageResponse update(UUID orderId, UUID usageId, MaterialUsageUpdateRequest request) {
        MaintenanceMaterialUsage usage = line(orderId, usageId);
        MaintenanceOrder order = usage.getOrder();
        if (order.isTerminal()) {
            throw new MaterialUsageException("Order " + order.getCode() + " is " + order.getStatus() + " and its materials are frozen");
        }
        if (request.allowOverConsumption() != null) {
            usage.setAllowOverConsumption(request.allowOverConsumption());
        }
        if (request.plannedQuantity() != null) {
            if (usage.getStockReservationId() != null) {
                throw new MaterialUsageException("The planned quantity of a reserved line cannot change; cancel and register it again");
            }
            usage.setPlannedQuantity(new Quantity(request.plannedQuantity(), usage.getUnit()).amount());
        }
        if (request.consumedQuantity() != null) {
            Quantity consumed = new Quantity(request.consumedQuantity(), usage.getUnit());
            if (consumed.amount().compareTo(usage.getPlannedQuantity()) > 0 && !usage.getAllowOverConsumption()) {
                throw new MaterialUsageException("Consumed quantity " + consumed.amount() + " exceeds the planned "
                        + usage.getPlannedQuantity() + " " + usage.getUnit() + " and over-consumption is not allowed for this line");
            }
            usage.setConsumedQuantity(consumed.amount());
        }
        return mapper.toResponse(repository.save(usage));
    }

    @Override
    @Transactional
    public MaterialUsageResponse sync(UUID orderId, UUID usageId) {
        MaintenanceMaterialUsage usage = line(orderId, usageId);
        stock.syncNow(usage);
        return mapper.toResponse(repository.save(usage));
    }

    private MaintenanceMaterialUsage line(UUID orderId, UUID usageId) {
        lookups.order(orderId);
        return repository.findByIdAndOrderId(usageId, orderId)
                .orElseThrow(() -> new NotFoundException("Material usage", usageId));
    }
}
