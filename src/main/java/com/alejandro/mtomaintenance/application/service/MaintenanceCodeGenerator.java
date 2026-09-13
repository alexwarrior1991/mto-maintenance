package com.alejandro.mtomaintenance.application.service;

/** Codigos legibles y unicos: MO-000001, SH-000001, INS-000001, DEF-000001 (secuencias de BD). */
public interface MaintenanceCodeGenerator {

    String nextOrderCode();

    String nextShiftCode();

    String nextInspectionCode();

    String nextDefectCode();
}
