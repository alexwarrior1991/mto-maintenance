package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.service.MaintenanceCodeGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

/**
 * Codigos con secuencia de base de datos: unicos aunque dos peticiones creen a la vez, sin bloqueo ni
 * MAX. Se pide dentro de la transaccion de negocio; una secuencia no se deshace con el rollback, asi
 * que puede haber huecos, que es exactamente lo que se acepta a cambio de no serializar las altas.
 */
@Service
class MaintenanceCodeGeneratorImpl implements MaintenanceCodeGenerator {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public String nextOrderCode() {
        return "MO-" + next("maintenance_order_code_seq");
    }

    @Override
    public String nextShiftCode() {
        return "SH-" + next("maintenance_shift_code_seq");
    }

    @Override
    public String nextInspectionCode() {
        return "INS-" + next("maintenance_inspection_code_seq");
    }

    @Override
    public String nextDefectCode() {
        return "DEF-" + next("catenary_defect_code_seq");
    }

    private String next(String sequence) {
        Number value = (Number) entityManager.createNativeQuery("select nextval('" + sequence + "')").getSingleResult();
        return String.format("%06d", value.longValue());
    }
}
