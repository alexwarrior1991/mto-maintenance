package com.alejandro.mtomaintenance.infrastructure.persistence.specification;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class CatenaryDefectSpecification {

    private CatenaryDefectSpecification() {
    }

    public static Specification<CatenaryDefect> severityEquals(DefectSeverity severity) {
        return SpecificationUtils.equalsEnum("severity", severity);
    }

    public static Specification<CatenaryDefect> statusEquals(DefectStatus status) {
        return SpecificationUtils.equalsEnum("status", status);
    }

    public static Specification<CatenaryDefect> assetIdEquals(UUID assetId) {
        return SpecificationUtils.associationIdEquals("asset", assetId);
    }

    public static Specification<CatenaryDefect> orderIdEquals(UUID orderId) {
        return SpecificationUtils.associationIdEquals("order", orderId);
    }

    public static Specification<CatenaryDefect> trackIdEquals(Long trackId) {
        return SpecificationUtils.equalsLong("trackId", trackId);
    }

    public static Specification<CatenaryDefect> stationIdEquals(Long stationId) {
        return SpecificationUtils.equalsLong("stationId", stationId);
    }

    public static Specification<CatenaryDefect> executionPackageIdEquals(Long executionPackageId) {
        return SpecificationUtils.equalsLong("executionPackageId", executionPackageId);
    }

    public static Specification<CatenaryDefect> detectedBetween(Instant from, Instant to) {
        return SpecificationUtils.between("detectedAt", from, to);
    }
}
