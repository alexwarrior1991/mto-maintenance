package com.alejandro.mtomaintenance.infrastructure.persistence.specification;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
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

    /** Turnos que recorren la via: 'trackIds' es una coleccion, asi que se hace join y no equals. */
    public static Specification<MaintenanceShift> worksOnTrack(Long trackId) {
        if (trackId == null) {
            return SpecificationUtils.alwaysTrue();
        }
        return (root, query, builder) -> {
            query.distinct(true);
            return builder.equal(root.join("trackIds"), trackId);
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
