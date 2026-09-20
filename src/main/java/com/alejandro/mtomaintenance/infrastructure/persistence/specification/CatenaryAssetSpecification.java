package com.alejandro.mtomaintenance.infrastructure.persistence.specification;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetSwitch;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

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

    /** Aisladores que conectan con esta via, por su via secundaria. */
    public static Specification<CatenaryAsset> connectedTrackIdEquals(Long connectedTrackId) {
        return SpecificationUtils.equalsLong("connectedTrackId", connectedTrackId);
    }

    /**
     * Aisladores con una aguja de ese codigo: {@code ?switchCode=W31}.
     *
     * <p>Se resuelve con un {@code exists} y no con un {@code join}, que es lo que importa: un join
     * a la coleccion devolveria el activo una vez por aguja y la pagina traeria duplicados y menos
     * elementos de los pedidos. Con {@code exists} la consulta sigue siendo una fila por activo.
     */
    public static Specification<CatenaryAsset> hasSwitchCode(String switchCode) {
        String normalized = SpecificationUtils.normalize(switchCode);

        if (normalized == null) {
            return SpecificationUtils.alwaysTrue();
        }

        return (root, query, criteriaBuilder) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<CatenaryAssetSwitch> switchRoot = subquery.from(CatenaryAssetSwitch.class);
            subquery.select(switchRoot.get("id")).where(
                    criteriaBuilder.equal(switchRoot.get("asset").get("id"), root.get("id")),
                    criteriaBuilder.like(criteriaBuilder.upper(switchRoot.get("code")),
                            "%" + normalized.toUpperCase(Locale.ROOT) + "%"));
            return criteriaBuilder.exists(subquery);
        };
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
