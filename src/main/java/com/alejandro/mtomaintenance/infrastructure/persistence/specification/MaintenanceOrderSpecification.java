package com.alejandro.mtomaintenance.infrastructure.persistence.specification;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class MaintenanceOrderSpecification {

    private MaintenanceOrderSpecification() {
    }

    public static Specification<MaintenanceOrder> statusEquals(MaintenanceOrderStatus status) {
        return SpecificationUtils.equalsEnum("status", status);
    }

    public static Specification<MaintenanceOrder> typeEquals(MaintenanceOrderType type) {
        return SpecificationUtils.equalsEnum("type", type);
    }

    public static Specification<MaintenanceOrder> priorityEquals(MaintenancePriority priority) {
        return SpecificationUtils.equalsEnum("priority", priority);
    }

    public static Specification<MaintenanceOrder> assetIdEquals(UUID assetId) {
        return SpecificationUtils.associationIdEquals("asset", assetId);
    }

    public static Specification<MaintenanceOrder> assetTypeEquals(CatenaryAssetType assetType) {
        return SpecificationUtils.associationAttributeEquals("asset", "type", assetType);
    }

    public static Specification<MaintenanceOrder> trackIdEquals(Long trackId) {
        return SpecificationUtils.equalsLong("trackId", trackId);
    }

    public static Specification<MaintenanceOrder> stationIdEquals(Long stationId) {
        return SpecificationUtils.equalsLong("stationId", stationId);
    }

    public static Specification<MaintenanceOrder> executionPackageIdEquals(Long executionPackageId) {
        return SpecificationUtils.equalsLong("executionPackageId", executionPackageId);
    }

    public static Specification<MaintenanceOrder> teamIdEquals(UUID teamId) {
        return SpecificationUtils.associationIdEquals("team", teamId);
    }

    public static Specification<MaintenanceOrder> assignedUserEquals(String assignedUser) {
        return SpecificationUtils.equalsIgnoreCase("assignedUser", assignedUser);
    }

    public static Specification<MaintenanceOrder> codeContains(String code) {
        return SpecificationUtils.containsIgnoreCase("code", code);
    }

    public static Specification<MaintenanceOrder> plannedBetween(LocalDate from, LocalDate to) {
        return SpecificationUtils.between("plannedDate", from, to);
    }

    public static Specification<MaintenanceOrder> actualStartBetween(Instant from, Instant to) {
        return SpecificationUtils.between("actualStartDate", from, to);
    }
}
