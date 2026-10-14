package com.alejandro.mtomaintenance.infrastructure.persistence.specification;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MaintenanceShiftSpecification {

    private MaintenanceShiftSpecification() {
    }

    public static Specification<MaintenanceShift> dateBetween(LocalDate from, LocalDate to) {
        return SpecificationUtils.between("shiftDate", from, to);
    }

    public static Specification<MaintenanceShift> teamIdEquals(UUID teamId) {
        return SpecificationUtils.associationIdEquals("team", teamId);
    }

    /**
     * Turnos que recorren alguna de las vias: 'trackIds' es una coleccion, asi que se hace join y no
     * equals, y {@code distinct} para que un turno que recorre dos de ellas salga una vez. Varias vias
     * son las dos de un aislador o un seccionador que las une: se trabaja desde un turno de
     * cualquiera. Sin vias (o solo nulos) no filtra.
     */
    public static Specification<MaintenanceShift> worksOnAnyTrack(Collection<Long> trackIds) {
        List<Long> tracks = trackIds == null ? List.of() : trackIds.stream().filter(Objects::nonNull).distinct().toList();
        if (tracks.isEmpty()) {
            return SpecificationUtils.alwaysTrue();
        }
        return (root, query, builder) -> {
            query.distinct(true);
            return root.join("trackIds").in(tracks);
        };
    }

    public static Specification<MaintenanceShift> executionPackageIdEquals(Long executionPackageId) {
        return SpecificationUtils.equalsLong("executionPackageId", executionPackageId);
    }

    public static Specification<MaintenanceShift> statusEquals(ShiftStatus status) {
        return SpecificationUtils.equalsEnum("status", status);
    }

    public static Specification<MaintenanceShift> possessionTypeEquals(PossessionType possessionType) {
        return SpecificationUtils.equalsEnum("possessionType", possessionType);
    }
}
