package com.alejandro.mtomaintenance.infrastructure.web.exception;

import com.alejandro.mtomaintenance.application.exception.AssetDisabledException;
import com.alejandro.mtomaintenance.application.exception.BusinessException;
import com.alejandro.mtomaintenance.application.exception.DuplicateCodeException;
import com.alejandro.mtomaintenance.application.exception.InspectionException;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.MaterialUsageException;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.exception.ShiftException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;

import java.util.Map;

/**
 * Codigo de error estable por tipo de excepcion, para que un cliente distinga la causa sin
 * analizar el mensaje. El prefijo sale del agregado en los 404 y 409 de codigo duplicado.
 */
final class BusinessErrorCodeResolver {

    private static final Map<String, String> AGGREGATE_PREFIXES = Map.ofEntries(
            Map.entry("CatenaryAsset", "AST"),
            Map.entry("Catenary asset", "AST"),
            Map.entry("MaintenanceOrder", "ORD"),
            Map.entry("Maintenance order", "ORD"),
            Map.entry("MaintenanceTask", "TSK"),
            Map.entry("Maintenance task", "TSK"),
            Map.entry("MaintenanceShift", "SHF"),
            Map.entry("Maintenance shift", "SHF"),
            Map.entry("MaintenanceTeam", "TEA"),
            Map.entry("Maintenance team", "TEA"),
            Map.entry("MaintenanceTaskType", "TTY"),
            Map.entry("Maintenance task type", "TTY"),
            Map.entry("MaintenanceInspection", "INS"),
            Map.entry("Maintenance inspection", "INS"),
            Map.entry("InspectionTemplate", "TPL"),
            Map.entry("Inspection template", "TPL"),
            Map.entry("CatenaryDefect", "DEF"),
            Map.entry("Catenary defect", "DEF"),
            Map.entry("MaintenanceMaterialUsage", "MAT"),
            Map.entry("Material usage", "MAT")
    );

    private BusinessErrorCodeResolver() {
    }

    static String resolve(BusinessException exception) {
        return switch (exception) {
            case NotFoundException notFound -> aggregateCode(notFound.getAggregate(), "404", "APP-404");
            case DuplicateCodeException duplicate -> aggregateCode(duplicate.getAggregate(), "409", "APP-409");
            case InvalidTransitionException ignored -> "TRN-001";
            case AssetDisabledException ignored -> "AST-001";
            case MaterialUsageException ignored -> "MAT-001";
            case InspectionException ignored -> "INS-001";
            case ShiftException ignored -> "SHF-001";
            case StockUnavailableException ignored -> "STK-503";
            case ValidationException ignored -> "VAL-001";
            default -> "BUS-001";
        };
    }

    private static String aggregateCode(String aggregate, String suffix, String fallback) {
        String prefix = AGGREGATE_PREFIXES.get(aggregate);
        return prefix == null ? fallback : "%s-%s".formatted(prefix, suffix);
    }
}
