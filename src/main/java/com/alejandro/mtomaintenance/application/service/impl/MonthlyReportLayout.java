package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.export.ReportDocument;
import com.alejandro.mtomaintenance.application.dto.export.ReportValue;
import com.alejandro.mtomaintenance.application.dto.report.MonthlyMaterialLineResponse;
import com.alejandro.mtomaintenance.application.dto.report.MonthlyReportResponse;

import java.time.Instant;
import java.util.List;

/**
 * El resumen mensual. Es el unico de los tres que va en vertical: tiene doce contadores y una tabla
 * de materiales corta, y en apaisado se quedaria medio folio en blanco.
 *
 * <p>Aqui los contadores van en la cabecera y no en los totales, que es donde los pone el parte.
 * No es una excepcion al modelo: cabecera y totales son la posicion, encima y debajo de la tabla, y
 * en el mensual los contadores <b>son</b> el informe, mientras que los materiales son un desglose
 * que no totaliza nada. Ponerlos debajo obligaria a pasar la tabla de materiales para llegar a la
 * cifra que se viene a buscar.</p>
 */
final class MonthlyReportLayout {

    private static final String REPORT = "monthly-report";
    private static final String SHEET = "Monthly report";

    private static final List<ReportDocument.Column> COLUMNS = List.of(
            new ReportDocument.Column("Material", 10),
            // El id de mto-stock no se imprime: son 36 caracteres de UUID que en papel no sirven
            // para nada, pero en la hoja son la unica forma de casar una linea con el almacen.
            new ReportDocument.Column("Material id", 0),
            new ReportDocument.Column("Unit", 4),
            new ReportDocument.Column("Consumed", 6));

    private MonthlyReportLayout() {
    }

    static ReportDocument of(MonthlyReportResponse report, Instant generatedAt) {
        List<List<ReportValue>> rows = report.materials().stream().map(MonthlyReportLayout::row).toList();
        String month = ReportLayouts.text(report.month());
        String executionPackage = ReportLayouts.text(report.executionPackageId());

        return new ReportDocument(
                ReportLayouts.fileBaseName(REPORT, month, executionPackage == null ? null : "ep" + executionPackage),
                SHEET,
                "Monthly report " + (month == null ? "" : month),
                ReportLayouts.subtitle(executionPackage == null ? null : "execution package " + executionPackage),
                ReportDocument.Layout.PORTRAIT,
                ReportLayouts.at(generatedAt),
                header(report, month, executionPackage),
                new ReportDocument.Table(COLUMNS, rows),
                List.of());
    }

    private static List<ReportDocument.Field> header(MonthlyReportResponse report, String month, String executionPackage) {
        return List.of(
                // El mes va como texto y no como fecha: un YearMonth escrito como 2026-01-01 invita a
                // leerlo como el 1 de enero, y el informe no es de ese dia.
                new ReportDocument.Field("Month", ReportValue.text(month)),
                new ReportDocument.Field("Execution package", ReportValue.text(executionPackage)),
                new ReportDocument.Field("Shifts planned", ReportValue.count(report.shiftsPlanned())),
                new ReportDocument.Field("Shifts closed", ReportValue.count(report.shiftsClosed())),
                new ReportDocument.Field("Shifts cancelled", ReportValue.count(report.shiftsCancelled())),
                new ReportDocument.Field("Net work minutes", ReportValue.count(report.netWorkMinutes())),
                new ReportDocument.Field("Average net minutes per shift", ReportValue.decimal(report.averageNetMinutesPerShift(), 1)),
                new ReportDocument.Field("Orders completed", ReportValue.count(report.ordersCompleted())),
                new ReportDocument.Field("Tasks completed", ReportValue.count(report.tasksCompleted())),
                new ReportDocument.Field("Profiles checked", ReportValue.count(report.profilesChecked())),
                new ReportDocument.Field("Km covered", ReportValue.decimal(report.coveredKm(), 3)),
                new ReportDocument.Field("Defects detected", ReportValue.count(report.defectsDetected())),
                new ReportDocument.Field("Defects resolved", ReportValue.count(report.defectsResolved())),
                new ReportDocument.Field("Corrective orders created", ReportValue.count(report.correctiveOrdersCreated())));
    }

    private static List<ReportValue> row(MonthlyMaterialLineResponse material) {
        return List.of(
                ReportValue.text(material.materialCode()),
                ReportValue.text(ReportLayouts.text(material.materialId())),
                ReportValue.text(material.unit()),
                ReportValue.decimal(material.consumedQuantity(), 3));
    }
}
