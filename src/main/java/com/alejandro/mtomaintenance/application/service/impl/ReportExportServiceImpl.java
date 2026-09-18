package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.export.ExportedReport;
import com.alejandro.mtomaintenance.application.dto.export.ReportDocument;
import com.alejandro.mtomaintenance.application.dto.export.ReportFormat;
import com.alejandro.mtomaintenance.application.dto.report.MonthlyReportResponse;
import com.alejandro.mtomaintenance.application.dto.report.ProgressReportResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportResponse;
import com.alejandro.mtomaintenance.application.service.ReportExportService;
import com.alejandro.mtomaintenance.application.service.ReportExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compone el documento de cada informe y se lo pasa al exportador de su formato.
 *
 * <p>No hay ningun {@code switch} por formato, ni aqui ni en los controladores: los exportadores se
 * indexan al arrancar, igual que {@code DispatchingMasterDataEventHandler} indexa los manejadores de
 * datos maestros por nombre de entidad. Añadir CSV es escribir un {@code @Component} mas y su
 * constante en {@link ReportFormat}.</p>
 *
 * <p>El indice se comprueba en el arranque y no en la primera peticion, que es la diferencia entre
 * un despliegue que no sale y un 500 la primera vez que alguien pida un formato. Se rechazan tres
 * cosas: dos exportadores para el mismo formato —cual gana dependeria del orden de escaneo del
 * classpath, o sea del arranque—, un exportador que diga producir JSON —que no es un fichero— y un
 * formato declarado en la enumeracion sin nadie que lo escriba, que es lo que deja
 * {@code ?format=csv} a medio añadir.</p>
 */
@Service
class ReportExportServiceImpl implements ReportExportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportExportServiceImpl.class);

    private final Map<ReportFormat, ReportExporter> exportersByFormat;

    ReportExportServiceImpl(List<ReportExporter> exporters) {
        this.exportersByFormat = indexByFormat(exporters);

        LOGGER.info("Report exporters ready: {} registered for {}", exportersByFormat.size(), exportersByFormat.keySet());
    }

    @Override
    public ExportedReport exportShiftReport(ShiftReportResponse report, ReportFormat format) {
        return write(ShiftReportLayout.of(report, Instant.now()), format);
    }

    @Override
    public ExportedReport exportProgressReport(ProgressReportResponse report, ReportFormat format) {
        return write(ProgressReportLayout.of(report, Instant.now()), format);
    }

    @Override
    public ExportedReport exportMonthlyReport(MonthlyReportResponse report, ReportFormat format) {
        return write(MonthlyReportLayout.of(report, Instant.now()), format);
    }

    private ExportedReport write(ReportDocument document, ReportFormat format) {
        ReportExporter exporter = exportersByFormat.get(format);

        if (exporter == null) {
            // El arranque garantiza que hay uno por cada formato que no sea JSON, asi que aqui solo
            // se llega pidiendo JSON, y eso es un error de programacion: el controlador tiene que
            // haber respondido el DTO sin pasar por aqui.
            throw new IllegalArgumentException(format + " is not a file format: the caller must answer the DTO instead");
        }

        return exporter.export(document);
    }

    private static Map<ReportFormat, ReportExporter> indexByFormat(List<ReportExporter> exporters) {
        Map<ReportFormat, ReportExporter> index = new LinkedHashMap<>();

        for (ReportExporter exporter : exporters) {
            ReportFormat format = exporter.format();

            if (format == null || format.isJson()) {
                throw new IllegalStateException(
                        exporter.getClass().getName() + " claims the format " + format + ", which is not a file format");
            }

            ReportExporter previous = index.put(format, exporter);

            if (previous != null) {
                throw new IllegalStateException(("Two exporters claim the report format %s: %s and %s. "
                        + "Which one ran would depend on the classpath scanning order.")
                        .formatted(format, previous.getClass().getName(), exporter.getClass().getName()));
            }
        }

        for (ReportFormat format : ReportFormat.values()) {
            if (!format.isJson() && !index.containsKey(format)) {
                throw new IllegalStateException(("No exporter writes the report format %s, which ?format already accepts. "
                        + "Add its ReportExporter or drop the constant.").formatted(format));
            }
        }

        return Map.copyOf(index);
    }
}
