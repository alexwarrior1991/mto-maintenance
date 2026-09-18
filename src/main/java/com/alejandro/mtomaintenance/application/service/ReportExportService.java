package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.export.ExportedReport;
import com.alejandro.mtomaintenance.application.dto.export.ReportFormat;
import com.alejandro.mtomaintenance.application.dto.report.MonthlyReportResponse;
import com.alejandro.mtomaintenance.application.dto.report.ProgressReportResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportResponse;

/**
 * Exportacion de los tres informes al fichero que se entrega al cliente.
 *
 * <p>Un metodo por informe en vez de uno generico: cada informe tiene su propia disposicion —que
 * columnas, que va en la cabecera, como se llama el fichero—, y con nombres distintos el compilador
 * comprueba que existe y la pila de llamadas dice cual fallo. Un informe nuevo añade su metodo y su
 * disposicion, y hereda los dos formatos sin tocarlos.</p>
 *
 * <p>Recibe el DTO ya calculado, no los filtros: la consulta la sigue haciendo el servicio de
 * siempre dentro de su transaccion de solo lectura, y aqui llega un objeto completamente
 * materializado. Escribir el fichero fuera de la transaccion no puede disparar ninguna carga
 * perezosa —{@code open-in-view} esta apagado en los tres perfiles— ni tener una transaccion abierta
 * mientras se comprime un libro de Excel.</p>
 */
public interface ReportExportService {

    ExportedReport exportShiftReport(ShiftReportResponse report, ReportFormat format);

    ExportedReport exportProgressReport(ProgressReportResponse report, ReportFormat format);

    ExportedReport exportMonthlyReport(MonthlyReportResponse report, ReportFormat format);
}
