package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.dto.export.ReportDocument;
import com.alejandro.mtomaintenance.application.dto.export.ReportValue;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportRowResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportResponse;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamSummaryResponse;

import java.time.Instant;
import java.util.List;

/**
 * El parte diario del turno: la cabecera del turno arriba, una fila por tarea trabajada y los
 * contadores al pie. Es el que sustituye a la hoja que se entregaba antes, asi que conserva su
 * forma: una fila por perfil, con lo hecho, lo encontrado y lo gastado.
 *
 * <p>Un turno que todavia no ha empezado se exporta igual. La ventana real, el corte de tension y
 * los minutos netos son nulos hasta que se cierra, y salen como celda vacia: un parte que reventara
 * antes de arrancar el turno seria inutil justo cuando se usa para preparar la noche.</p>
 *
 * <p>La mitad de las columnas no se imprimen (peso 0). En la hoja de calculo caben las dieciocho;
 * en un A4 apaisado, meter la orden, el paquete, la via y el nombre largo del perfil dejaria sin
 * ancho a "Works performed" y "Defects found", que son las dos que de verdad se leen.</p>
 */
final class ShiftReportLayout {

    private static final String REPORT = "shift-report";
    private static final String SHEET = "Shift report";

    private static final List<ReportDocument.Column> COLUMNS = List.of(
            new ReportDocument.Column("#", 3),
            new ReportDocument.Column("Order", 0),
            new ReportDocument.Column("Execution package", 0),
            new ReportDocument.Column("Track", 0),
            new ReportDocument.Column("Profile", 8),
            new ReportDocument.Column("Profile name", 0),
            new ReportDocument.Column("Kp", 6),
            new ReportDocument.Column("Sectioning", 5),
            new ReportDocument.Column("Task types", 8),
            new ReportDocument.Column("Works performed", 18),
            new ReportDocument.Column("Defects found", 14),
            new ReportDocument.Column("Materials", 10),
            new ReportDocument.Column("Started", 7),
            new ReportDocument.Column("Completed", 7),
            new ReportDocument.Column("Status", 7),
            new ReportDocument.Column("Work complete", 5),
            new ReportDocument.Column("Repair planned", 6),
            // Las referencias de foto no apuntan hoy a ningun almacen, pero son el unico rastro de
            // que la foto existe: se imprimen como texto. El dia que haya almacen de ficheros, el
            // valor ya viaja en el documento y solo cambia como lo pinta cada exportador.
            new ReportDocument.Column("Photos", 7));

    private ShiftReportLayout() {
    }

    static ReportDocument of(ShiftReportResponse report, Instant generatedAt) {
        MaintenanceShiftResponse shift = report.shift();
        List<List<ReportValue>> rows = report.rows().stream().map(ShiftReportLayout::row).toList();

        return new ReportDocument(
                ReportLayouts.fileBaseName(REPORT, ReportLayouts.text(shift.shiftDate()), shift.code()),
                SHEET,
                shift.code() == null ? "Daily shift report" : "Daily shift report " + shift.code(),
                subtitle(shift),
                ReportDocument.Layout.LANDSCAPE,
                ReportLayouts.at(generatedAt),
                header(shift),
                new ReportDocument.Table(COLUMNS, rows),
                totals(report));
    }

    private static String subtitle(MaintenanceShiftResponse shift) {
        MaintenanceTeamSummaryResponse team = shift.team();
        return ReportLayouts.subtitle(
                team == null ? null : team.name(),
                ReportLayouts.text(shift.shiftDate()),
                shift.trackIds() == null || shift.trackIds().isEmpty() ? null : "tracks " + join(shift.trackIds()),
                shift.possessionType() == null ? null : shift.possessionType().name() + " possession",
                ReportLayouts.text(shift.status()));
    }

    private static List<ReportDocument.Field> header(MaintenanceShiftResponse shift) {
        MaintenanceTeamSummaryResponse team = shift.team();
        return List.of(
                new ReportDocument.Field("Shift", ReportValue.text(shift.code())),
                new ReportDocument.Field("Date", ReportValue.date(shift.shiftDate())),
                new ReportDocument.Field("Status", ReportValue.text(shift.status())),
                new ReportDocument.Field("Team", ReportValue.text(team == null ? null : team.name())),
                new ReportDocument.Field("Base", ReportValue.text(shift.baseName())),
                new ReportDocument.Field("Vehicle", ReportValue.text(shift.vehicle())),
                new ReportDocument.Field("Possession", ReportValue.text(shift.possessionType())),
                new ReportDocument.Field("Execution package", ReportValue.count(shift.executionPackageId())),
                new ReportDocument.Field("Tracks", ReportValue.text(join(shift.trackIds()))),
                new ReportDocument.Field("Start kp", ReportValue.decimal(shift.startKp(), 3)),
                new ReportDocument.Field("End kp", ReportValue.decimal(shift.endKp(), 3)),
                new ReportDocument.Field("Planned start", ReportLayouts.timestamp(shift.plannedStart())),
                new ReportDocument.Field("Planned end", ReportLayouts.timestamp(shift.plannedEnd())),
                new ReportDocument.Field("Actual start", ReportLayouts.timestamp(shift.actualStart())),
                new ReportDocument.Field("Actual end", ReportLayouts.timestamp(shift.actualEnd())),
                new ReportDocument.Field("Voltage cut-off", ReportLayouts.timestamp(shift.voltageCutoffAt())),
                new ReportDocument.Field("Net work minutes", ReportValue.count(shift.netWorkMinutes())),
                new ReportDocument.Field("Blocking disconnectors", disconnectors(shift)),
                new ReportDocument.Field("Earthing points", ReportValue.text(shift.earthingPoints())),
                new ReportDocument.Field("Parking place", ReportValue.text(shift.parkingPlace())),
                new ReportDocument.Field("Personnel", ReportValue.text(shift.personnel())),
                new ReportDocument.Field("Measurement equipment", ReportValue.text(shift.measurementEquipment())),
                new ReportDocument.Field("Observations", ReportValue.text(shift.observations())));
    }

    private static List<ReportDocument.Field> totals(ShiftReportResponse report) {
        return List.of(
                new ReportDocument.Field("Tasks completed", ReportValue.count(report.tasksCompleted())),
                new ReportDocument.Field("Tasks pending", ReportValue.count(report.tasksPending())),
                new ReportDocument.Field("Profiles reviewed", ReportValue.count(report.profilesReviewed())),
                new ReportDocument.Field("Defects found", ReportValue.count(report.defectsFound())),
                new ReportDocument.Field("Defects resolved", ReportValue.count(report.defectsResolved())));
    }

    private static List<ReportValue> row(ShiftReportRowResponse row) {
        return List.of(
                ReportValue.count(row.number()),
                ReportValue.text(row.orderCode()),
                ReportValue.count(row.executionPackageId()),
                ReportValue.count(row.trackId()),
                ReportValue.text(row.profileCode()),
                ReportValue.text(row.profileName()),
                ReportValue.decimal(row.kp(), 3),
                ReportValue.text(row.sectioning()),
                ReportValue.textList(row.taskTypeCodes()),
                ReportValue.text(row.worksPerformed()),
                ReportValue.text(row.defectsFound()),
                ReportValue.textList(row.materials()),
                ReportLayouts.timestamp(row.startedAt()),
                ReportLayouts.timestamp(row.completedAt()),
                ReportValue.text(row.status()),
                ReportValue.flag(row.workComplete()),
                ReportValue.date(row.repairPlannedDate()),
                ReportValue.textList(row.photoRefs()));
    }

    private static ReportValue disconnectors(MaintenanceShiftResponse shift) {
        List<CatenaryAssetSummaryResponse> blocking = shift.blockingDisconnectors();
        return blocking == null ? ReportValue.EMPTY
                : ReportValue.textList(blocking.stream().map(CatenaryAssetSummaryResponse::code).toList());
    }

    private static String join(List<Long> ids) {
        return ids == null || ids.isEmpty() ? null : ids.stream().map(String::valueOf).reduce((left, right) -> left + ", " + right).orElse(null);
    }
}
