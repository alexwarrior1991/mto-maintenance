package com.alejandro.mtomaintenance.infrastructure.persistence.repository;

import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceInspectionRepository extends JpaRepository<MaintenanceInspection, UUID>, JpaSpecificationExecutor<MaintenanceInspection> {

    Optional<MaintenanceInspection> findByCode(String code);

    boolean existsByOriginOrderId(UUID orderId);

    /**
     * Las inspecciones registradas en un turno, para su parte. Trae en la misma sentencia lo que el
     * parte lee de cada una (el activo, el defecto y la orden que genero): sin el grafo serian tres
     * consultas por inspeccion.
     */
    @EntityGraph(attributePaths = {"asset", "generatedDefect", "generatedOrder"})
    List<MaintenanceInspection> findByShiftIdOrderByInspectionDateAscCodeAsc(UUID shiftId);
}
