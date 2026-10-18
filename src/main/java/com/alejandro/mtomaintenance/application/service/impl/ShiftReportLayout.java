package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.asset.CatenaryAssetSummaryResponse;
import com.alejandro.mtomaintenance.application.dto.crew.CrewPersonSummary;
import com.alejandro.mtomaintenance.application.dto.export.ReportDocument;
import com.alejandro.mtomaintenance.application.dto.export.ReportValue;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportInspectionResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportRowResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftInstrumentResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftVehicleResponse;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamSummaryResponse;

import java.math.RoundingMode;
import java.util.ArrayList;
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
            // "Asset" y no "Profile": una fila puede ser un aislador de seccion generado con RG-03.
            // El JSON sigue llamandolos profileCode/profileName, que es lo que leen los clientes.
            new ReportDocument.Column("Asset", 8),
            new ReportDocument.Column("Asset name", 0),
            new ReportDocument.Column("Kp", 6),
            new ReportDocument.Column("Sectioning", 5),
            // Las agujas del aislador de seccion, con su tangente ('W31 1:9'). Peso 0: en una hoja
            // de calculo cabe, pero en un A4 quitaria ancho a los trabajos realizados y a los
            // defectos, que son las columnas que hay que leer de noche y sobre el terreno. Solo la
            // lleva un SECTION_INSULATOR; en el resto de filas sale vacia.
            new ReportDocument.Column("Switches", 0),
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

    private static final String INSPECTIONS = "Inspections";

    /**
     * Las inspecciones registradas en el turno, en su anexo. Se imprimen todas menos el nombre largo
     * del activo y el tipo, que el codigo ya dice ({@code SIN-}, {@code PRF-}).
     */
    private static final List<ReportDocument.Column> INSPECTION_COLUMNS = List.of(
            new ReportDocument.Column("Code", 6),
            new ReportDocument.Column("Date", 5),
            new ReportDocument.Column("Asset", 7),
            new ReportDocument.Column("Asset name", 0),
            new ReportDocument.Column("Type", 0),
            new ReportDocument.Column("Track", 3),
            new ReportDocument.Column("Kp", 5),
            new ReportDocument.Column("Kind", 4),
            new ReportDocument.Column("Result", 6),
            new ReportDocument.Column("Inspector", 7),
            new ReportDocument.Column("Findings", 20),
            new ReportDocument.Column("Defect", 6),
            new ReportDocument.Column("Corrective order", 6));

    private static final String CREW = "Crew";

    /** Quien fue esa noche: el jefe, el supervisor y los linemen, de mto-resources. */
    private static final List<ReportDocument.Column> CREW_COLUMNS = List.of(
            new ReportDocument.Column("Role", 5),
            new ReportDocument.Column("Code", 5),
            new ReportDocument.Column("Name", 12),
            new ReportDocument.Column("Username", 8),
            new ReportDocument.Column("Category", 7));

    private static final String VEHICLES = "Vehicles";

    /** En que fueron y donde se dejo cada uno al cerrar: no hay base fija. */
    private static final List<ReportDocument.Column> VEHICLE_COLUMNS = List.of(
            new ReportDocument.Column("Code", 5),
            new ReportDocument.Column("Name", 10),
            new ReportDocument.Column("Mobility", 6),
            new ReportDocument.Column("Category", 8),
            new ReportDocument.Column("Parking", 16));

    private static final String INSTRUMENTS = "Instruments";

    /**
     * Con que instrumentos de mto-resources se trabajo ({@code V23}) y hasta cuando valia su calibracion:
     * ninguno caducado a la fecha del turno, o no habria entrado. "Measurement equipment", el texto de la
     * cabecera, se queda para los turnos de antes, que no los llevan.
     */
    private static final List<ReportDocument.Column> INSTRUMENT_COLUMNS = List.of(
            new ReportDocument.Column("Code", 5),
            new ReportDocument.Column("Name", 12),
            new ReportDocument.Column("Kind", 7),
            new ReportDocument.Column("Calibrated until", 6));

    private ShiftReportLayout() {
    }

    static ReportDocument of(ShiftReportResponse report, ReportTime time) {
        MaintenanceShiftResponse shift = report.shift();
        List<List<ReportValue>> rows = report.rows().stream().map(each -> row(each, time)).toList();
        List<List<ReportValue>> inspections = report.inspections().stream().map(ShiftReportLayout::inspection).toList();

        return new ReportDocument(
                ReportLayouts.fileBaseName(REPORT, ReportLayouts.text(shift.shiftDate()), shift.code()),
                SHEET,
                shift.code() == null ? "Daily shift report" : "Daily shift report " + shift.code(),
                subtitle(shift),
                ReportDocument.Layout.LANDSCAPE,
                time.generated(),
                header(shift, time),
                new ReportDocument.Table(COLUMNS, rows),
                totals(report),
                List.of(new ReportDocument.Appendix(INSPECTIONS, new ReportDocument.Table(INSPECTION_COLUMNS, inspections)),
                        new ReportDocument.Appendix(CREW, new ReportDocument.Table(CREW_COLUMNS, crew(shift))),
                        new ReportDocument.Appendix(VEHICLES, new ReportDocument.Table(VEHICLE_COLUMNS, vehicles(shift))),
                        new ReportDocument.Appendix(INSTRUMENTS, new ReportDocument.Table(INSTRUMENT_COLUMNS, instruments(shift)))));
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

    private static List<ReportDocument.Field> header(MaintenanceShiftResponse shift, ReportTime time) {
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
                new ReportDocument.Field("Planned start", time.timestamp(shift.plannedStart())),
                new ReportDocument.Field("Planned end", time.timestamp(shift.plannedEnd())),
                new ReportDocument.Field("Actual start", time.timestamp(shift.actualStart())),
                new ReportDocument.Field("Actual end", time.timestamp(shift.actualEnd())),
                new ReportDocument.Field("Voltage cut-off", time.timestamp(shift.voltageCutoffAt())),
                new ReportDocument.Field("Net work minutes", ReportValue.count(shift.netWorkMinutes())),
                new ReportDocument.Field("Blocking disconnectors", disconnectors(shift)),
                new ReportDocument.Field("Earthing points", ReportValue.text(shift.earthingPoints())),
                new ReportDocument.Field("Parking place", ReportValue.text(shift.parkingPlace())),
                new ReportDocument.Field("Team leader", ReportValue.text(name(shift.teamLeader()))),
                new ReportDocument.Field("Supervisor", ReportValue.text(name(shift.supervisor()))),
                new ReportDocument.Field("Linemen", ReportValue.count(shift.linemen() == null ? null : shift.linemen().size())),
                new ReportDocument.Field("Vehicles", ReportValue.textList(shift.vehicles() == null ? List.of()
                        : shift.vehicles().stream().map(ShiftVehicleResponse::code).toList())),
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
                new ReportDocument.Field("Defects resolved", ReportValue.count(report.defectsResolved())),
                new ReportDocument.Field("Inspections recorded", ReportValue.count(report.inspectionsRecorded())));
    }

    private static List<ReportValue> inspection(ShiftReportInspectionResponse inspection) {
        return List.of(
                ReportValue.text(inspection.code()),
                ReportValue.date(inspection.inspectionDate()),
                ReportValue.text(inspection.assetCode()),
                ReportValue.text(inspection.assetName()),
                ReportValue.text(inspection.assetType()),
                ReportValue.count(inspection.trackId()),
                ReportValue.decimal(inspection.kp(), 3),
                ReportValue.text(inspection.inspectionKind()),
                ReportValue.text(inspection.result()),
                ReportValue.text(inspection.inspector()),
                ReportValue.text(inspection.detectedDefects()),
                ReportValue.text(inspection.generatedDefectCode()),
                ReportValue.text(inspection.generatedOrderCode()));
    }

    private static List<ReportValue> row(ShiftReportRowResponse row, ReportTime time) {
        return List.of(
                ReportValue.count(row.number()),
                ReportValue.text(row.orderCode()),
                ReportValue.count(row.executionPackageId()),
                ReportValue.count(row.trackId()),
                ReportValue.text(row.profileCode()),
                ReportValue.text(row.profileName()),
                ReportValue.decimal(row.kp(), 3),
                ReportValue.text(row.sectioning()),
                ReportValue.textList(row.switches()),
                ReportValue.textList(row.taskTypeCodes()),
                ReportValue.text(row.worksPerformed()),
                ReportValue.text(row.defectsFound()),
                ReportValue.textList(row.materials()),
                time.timestamp(row.startedAt()),
                time.timestamp(row.completedAt()),
                ReportValue.text(row.status()),
                ReportValue.flag(row.workComplete()),
                ReportValue.date(row.repairPlannedDate()),
                ReportValue.textList(row.photoRefs()));
    }

    private static List<List<ReportValue>> crew(MaintenanceShiftResponse shift) {
        List<List<ReportValue>> rows = new ArrayList<>();
        if (shift.teamLeader() != null) {
            rows.add(person("Team leader", shift.teamLeader()));
        }
        if (shift.supervisor() != null) {
            rows.add(person("Supervisor", shift.supervisor()));
        }
        if (shift.linemen() != null) {
            shift.linemen().forEach(lineman -> rows.add(person("Lineman", lineman)));
        }
        return rows;
    }

    private static List<ReportValue> person(String role, CrewPersonSummary person) {
        return List.of(ReportValue.text(role), ReportValue.text(person.code()), ReportValue.text(person.fullName()),
                ReportValue.text(person.username()), ReportValue.text(person.category()));
    }

    private static List<List<ReportValue>> vehicles(MaintenanceShiftResponse shift) {
        if (shift.vehicles() == null) {
            return List.of();
        }
        return shift.vehicles().stream().map(vehicle -> List.of(
                ReportValue.text(vehicle.code()),
                ReportValue.text(vehicle.name()),
                ReportValue.text(vehicle.mobility()),
                ReportValue.text(vehicle.category()),
                ReportValue.text(parking(vehicle.parking())))).toList();
    }

    private static List<List<ReportValue>> instruments(MaintenanceShiftResponse shift) {
        if (shift.instruments() == null) {
            return List.of();
        }
        return shift.instruments().stream().map(ShiftReportLayout::instrument).toList();
    }

    private static List<ReportValue> instrument(ShiftInstrumentResponse instrument) {
        return List.of(
                ReportValue.text(instrument.code()),
                ReportValue.text(instrument.name()),
                ReportValue.text(instrument.kind()),
                ReportValue.date(instrument.calibrationExpiresOn()));
    }

    /** {@code track 31, kp 4.250 — Siding north of Herzliya}; un sitio del catalogo va por su id de mto-resources. */
    private static String parking(ShiftVehicleResponse.Parking parking) {
        if (parking == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (parking.trackId() != null) {
            parts.add("track " + parking.trackId() + (parking.kp() == null ? "" : ", kp " + parking.kp().setScale(3, RoundingMode.HALF_UP).toPlainString()));
        }
        if (parking.siteId() != null) {
            parts.add("site " + parking.siteId());
        }
        if (parking.description() != null) {
            parts.add(parking.description());
        }
        return String.join(" — ", parts);
    }

    private static String name(CrewPersonSummary person) {
        return person == null ? null : person.fullName() + " (" + person.code() + ")";
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
