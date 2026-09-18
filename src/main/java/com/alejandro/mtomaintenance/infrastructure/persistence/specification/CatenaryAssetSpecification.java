package com.alejandro.mtomaintenance.infrastructure.persistence.specification;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class CatenaryAssetSpecification {

    private CatenaryAssetSpecification() {
    }

    public static Specification<CatenaryAsset> typeEquals(CatenaryAssetType type) {
        return SpecificationUtils.equalsEnum("type", type);
    }

    public static Specification<CatenaryAsset> trackIdEquals(Long trackId) {
        return SpecificationUtils.equalsLong("trackId", trackId);
    }

    public static Specification<CatenaryAsset> stationIdEquals(Long stationId) {
        return SpecificationUtils.equalsLong("stationId", stationId);
    }

    public static Specification<CatenaryAsset> executionPackageIdEquals(Long executionPackageId) {
        return SpecificationUtils.equalsLong("executionPackageId", executionPackageId);
    }

    public static Specification<CatenaryAsset> enabledEquals(Boolean enabled) {
        return SpecificationUtils.equalsBoolean("enabled", enabled);
    }

    public static Specification<CatenaryAsset> codeContains(String code) {
        return SpecificationUtils.containsIgnoreCase("code", code);
    }

    /**
     * Nombre natural del activo: el {@code profileId} de un perfil ({@code 12-2.27}), el nombre del
     * seccionador ({@code HSA-NS5}). Es el identificador que usa el personal de campo, y no es unico:
     * en mto-configuration el {@code profileId} lo es por via, no globalmente, asi que la busqueda
     * unívoca es este filtro junto al de via.
     */
    public static Specification<CatenaryAsset> nameContains(String name) {
        return SpecificationUtils.containsIgnoreCase("name", name);
    }

    /** Activos cuyo kp inicial cae en [from, to]. */
    public static Specification<CatenaryAsset> kpBetween(BigDecimal from, BigDecimal to) {
        return SpecificationUtils.between("startKp", from, to);
    }

    /**
     * Activos con preventivo vencido antes de la fecha: sin intervalo no vencen nunca; sin ultima
     * ejecucion vencen ya. El calculo lo hace la funcion SQL preventive_due_at (V1) para que el filtro pagine bien.
     */
    public static Specification<CatenaryAsset> preventiveDueBefore(Instant before) {
        if (before == null) {
            return SpecificationUtils.alwaysTrue();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.isNotNull(root.get("preventiveIntervalDays")),
                criteriaBuilder.or(
                        criteriaBuilder.isNull(root.get("lastPreventiveCompletedAt")),
                        criteriaBuilder.lessThanOrEqualTo(
                                criteriaBuilder.function("preventive_due_at", Instant.class,
                                        root.get("lastPreventiveCompletedAt"), root.get("preventiveIntervalDays")),
                                before)
                )
        );
    }

    /** Version portable del filtro anterior: se evalua en memoria sobre un activo ya cargado. */
    public static boolean isPreventiveDueBefore(CatenaryAsset asset, Instant before) {
        if (asset.getPreventiveIntervalDays() == null) {
            return false;
        }
        if (asset.getLastPreventiveCompletedAt() == null) {
            return true;
        }
        return !asset.getLastPreventiveCompletedAt().plus(asset.getPreventiveIntervalDays(), ChronoUnit.DAYS).isAfter(before);
    }
}
