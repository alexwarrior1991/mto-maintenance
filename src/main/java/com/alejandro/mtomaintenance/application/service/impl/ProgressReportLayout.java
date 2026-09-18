package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.export.ReportDocument;
import com.alejandro.mtomaintenance.application.dto.export.ReportValue;
import com.alejandro.mtomaintenance.application.dto.report.ProgressReportResponse;
import com.alejandro.mtomaintenance.application.dto.report.ProgressRowResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * El avance por paquete de ejecucion, via y tipo de activo: el cuaderno de avance que se entregaba
 * antes. Todas las columnas se imprimen; son ocho y sobra ancho en un A4 apaisado.
 *
 * <p>El ambito que va en el nombre del fichero y en el subtitulo se deduce de las filas, no de los
 * filtros: {@code ProgressReportResponse} no los lleva, y meterlos en la firma de la exportacion
 * seria colar la consulta dentro del escritor de ficheros. Cuando todas las filas comparten paquete
 * o via, eso <b>es</b> el ambito; cuando no, el nombre se queda en la fecha y no miente.</p>
 */
final class ProgressReportLayout {

    private static final String REPORT = "progress-report";
    private static final String SHEET = "Progress";

    private static final List<ReportDocument.Column> COLUMNS = List.of(
            new ReportDocument.Column("Execution package", 6),
            new ReportDocument.Column("Track", 5),
            new ReportDocument.Column("Asset type", 9),
            new ReportDocument.Column("Assets", 6),
            new ReportDocument.Column("Checked", 6),
            new ReportDocument.Column("Completion", 7),
            new ReportDocument.Column("Km covered", 8),
            new ReportDocument.Column("Km total", 8));

    private ProgressReportLayout() {
    }

    static ReportDocument of(ProgressReportResponse report, Instant generatedAt) {
        List<List<ReportValue>> rows = report.rows().stream().map(ProgressReportLayout::row).toList();
        String executionPackage = single(report, ProgressRowResponse::executionPackageId);
        String track = single(report, ProgressRowResponse::trackId);
        String assetType = single(report, row -> row.assetType() == null ? null : row.assetType().name());

        return new ReportDocument(
                ReportLayouts.fileBaseName(REPORT,
                        ReportLayouts.text(keyDate(report, generatedAt)),
                        executionPackage == null ? null : "ep" + executionPackage,
                        track == null ? null : "track" + track),
                SHEET,
                "Progress report",
                ReportLayouts.subtitle(
                        executionPackage == null ? null : "execution package " + executionPackage,
                        track == null ? null : "track " + track,
                        assetType,
                        period(report)),
                ReportDocument.Layout.LANDSCAPE,
                ReportLayouts.at(generatedAt),
                List.of(
                        new ReportDocument.Field("From", ReportLayouts.timestamp(report.from())),
                        new ReportDocument.Field("To", ReportLayouts.timestamp(report.to()))),
                new ReportDocument.Table(COLUMNS, rows),
                List.of(
                        new ReportDocument.Field("Assets", ReportValue.count(report.totalAssets())),
                        new ReportDocument.Field("Checked", ReportValue.count(report.checkedAssets())),
                        new ReportDocument.Field("Completion", ReportValue.ratio(report.completionRatio())),
                        new ReportDocument.Field("Km covered", ReportValue.decimal(report.coveredKm(), 3)),
                        new ReportDocument.Field("Km total", ReportValue.decimal(report.totalKm(), 3))));
    }

    private static List<ReportValue> row(ProgressRowResponse row) {
        return List.of(
                ReportValue.count(row.executionPackageId()),
                ReportValue.count(row.trackId()),
                ReportValue.text(row.assetType()),
                ReportValue.count(row.totalAssets()),
                ReportValue.count(row.checkedAssets()),
                ReportValue.ratio(row.completionRatio()),
                ReportValue.decimal(row.coveredKm(), 3),
                ReportValue.decimal(row.totalKm(), 3));
    }

    /** Sin rango pedido el informe es "todo lo que hay": la fecha que lo identifica es la de hoy. */
    private static LocalDate keyDate(ProgressReportResponse report, Instant generatedAt) {
        LocalDate to = ReportLayouts.day(report.to());
        LocalDate from = ReportLayouts.day(report.from());
        return to != null ? to : from != null ? from : ReportLayouts.day(generatedAt);
    }

    private static String period(ProgressReportResponse report) {
        LocalDate from = ReportLayouts.day(report.from());
        LocalDate to = ReportLayouts.day(report.to());
        if (from == null && to == null) {
            return "all time";
        }
        return (from == null ? "start" : from.toString()) + " to " + (to == null ? "today" : to.toString());
    }

    /** El valor comun a todas las filas, o nada si hay mas de uno: un nombre de fichero no puede mentir. */
    private static String single(ProgressReportResponse report, Function<ProgressRowResponse, Object> field) {
        List<String> distinct = report.rows().stream()
                .map(field)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .distinct()
                .toList();
        return distinct.size() == 1 ? distinct.getFirst() : null;
    }
}
