package com.alejandro.mtomaintenance.application.service.impl;

import static com.alejandro.mtomaintenance.application.service.impl.DomainGuard.domain;
import com.alejandro.mtomaintenance.application.dto.stock.StockMaterial;
import com.alejandro.mtomaintenance.application.exception.MaterialUsageException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.application.service.StockClient;
import com.alejandro.mtomaintenance.domain.model.Quantity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTask;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceMaterialUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Crea lineas de material resolviendo el material contra stock cuando hay cliente; sin el, con lo que traiga la peticion. */
@Component
@RequiredArgsConstructor
class MaterialLineFactory {

    private final MaintenanceMaterialUsageRepository repository;
    private final StockClient stockClient;

    MaintenanceMaterialUsage buildLine(MaintenanceOrder order, MaintenanceTask task, UUID materialId, String materialCode,
                                       UUID warehouseId, BigDecimal plannedQuantity, String unit, boolean allowOverConsumption) {
        Optional<StockMaterial> material = lookupMaterial(materialId, materialCode);
        UUID resolvedId = material.map(StockMaterial::id).orElse(materialId);
        if (resolvedId == null) {
            throw new ValidationException("Material '" + materialCode + "' is unknown to stock and no materialId was given");
        }
        String resolvedUnit = unit != null && !unit.isBlank() ? unit.trim()
                : material.map(StockMaterial::unitOfMeasure).orElse(null);
        if (resolvedUnit == null) {
            throw new ValidationException("unit is required when the material cannot be resolved against stock");
        }
        Quantity quantity = domain(() -> new Quantity(plannedQuantity, resolvedUnit));

        return MaintenanceMaterialUsage.builder()
                .order(order)
                .task(task)
                .materialId(resolvedId)
                .materialCode(material.map(StockMaterial::code).orElse(materialCode))
                .materialDescriptionSnapshot(material.map(StockMaterial::name).orElse(null))
                .warehouseId(warehouseId)
                .plannedQuantity(quantity.amount())
                .unit(quantity.unit())
                .allowOverConsumption(allowOverConsumption)
                .build();
    }

    /** La unicidad de la linea la garantiza la restriccion de BD, no una comprobacion previa. */
    MaintenanceMaterialUsage saveLine(MaintenanceMaterialUsage usage) {
        try {
            MaintenanceMaterialUsage saved = repository.saveAndFlush(usage);
            usage.getOrder().getMaterials().add(saved);
            return saved;
        } catch (DataIntegrityViolationException exception) {
            throw new MaterialUsageException("Material " + usage.getMaterialCode() + " from warehouse " + usage.getWarehouseId()
                    + " is already registered on order " + usage.getOrder().getCode() + " (same task)");
        }
    }

    private Optional<StockMaterial> lookupMaterial(UUID materialId, String materialCode) {
        if (!stockClient.isEnabled()) {
            return Optional.empty();
        }
        try {
            if (materialId != null) {
                return stockClient.findMaterialById(materialId);
            }
            return stockClient.findMaterialByCode(materialCode.trim());
        } catch (StockUnavailableException exception) {
            // Sin stock a mano se registra con lo que trae la peticion; la reserva quedara FAILED y se
            // reintentara con /sync.
            return Optional.empty();
        }
    }
}
