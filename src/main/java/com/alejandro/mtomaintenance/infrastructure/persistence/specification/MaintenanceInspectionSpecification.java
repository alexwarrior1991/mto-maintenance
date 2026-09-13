package com.alejandro.mtomaintenance.infrastructure.persistence.specification;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspection;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

public final class MaintenanceInspectionSpecification {

    private MaintenanceInspectionSpecification() {
    }

    public static Specification<MaintenanceInspection> resultEquals(InspectionResult result) {
        return SpecificationUtils.equalsEnum("result", result);
    }

    public static Specification<MaintenanceInspection> assetIdEquals(UUID assetId) {
        return SpecificationUtils.associationIdEquals("asset", assetId);
    }

    public static Specification<MaintenanceInspection> assetTypeEquals(CatenaryAssetType assetType) {
        return SpecificationUtils.associationAttributeEquals("asset", "type", assetType);
    }

    public static Specification<MaintenanceInspection> trackIdEquals(Long trackId) {
        return SpecificationUtils.equalsLong("trackId", trackId);
    }

    public static Specification<MaintenanceInspection> stationIdEquals(Long stationId) {
        return SpecificationUtils.equalsLong("stationId", stationId);
    }

    public static Specification<MaintenanceInspection> executionPackageIdEquals(Long executionPackageId) {
        return SpecificationUtils.equalsLong("executionPackageId", executionPackageId);
    }

    public static Specification<MaintenanceInspection> inspectorEquals(String inspector) {
        return SpecificationUtils.equalsIgnoreCase("inspector", inspector);
    }

    public static Specification<MaintenanceInspection> originOrderIdEquals(UUID orderId) {
        return SpecificationUtils.associationIdEquals("originOrder", orderId);
    }

    public static Specification<MaintenanceInspection> dateBetween(LocalDate from, LocalDate to) {
        return SpecificationUtils.between("inspectionDate", from, to);
    }
}
