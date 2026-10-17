package com.alejandro.mtomaintenance.infrastructure.export;

import com.alejandro.mtomaintenance.application.dto.export.ExportedReport;
import com.alejandro.mtomaintenance.application.dto.export.ReportDocument;
import com.alejandro.mtomaintenance.application.dto.export.ReportFormat;
import com.alejandro.mtomaintenance.application.dto.export.ReportMediaTypes;
import com.alejandro.mtomaintenance.application.dto.export.ReportValue;
import com.alejandro.mtomaintenance.application.dto.shift.MaintenanceShiftResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftInstrumentResponse;
import com.alejandro.mtomaintenance.application.dto.shift.ShiftReportResponse;
import com.alejandro.mtomaintenance.application.service.ReportExportService;
import com.alejandro.mtomaintenance.application.service.ReportExporter;
import com.alejandro.mtomaintenance.configuration.reports.ReportProperties;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.PaneInformation;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.util.ClassUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La capa de exportacion, probada contra el fichero de verdad: el .xlsx se vuelve a abrir con POI y
 * el PDF con el lector de OpenPDF. Es lo unico que demuestra lo que importa aqui, que es que una
 * fecha llegue como fecha y no como texto; un test que solo mirase que el array no esta vacio dejaria
 * pasar un libro entero escrito con cadenas.
 */
class ReportExportLayerTest {

    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 1, 28, 5, 30);

    private final XlsxReportExporter xlsx = new XlsxReportExporter();
    private final PdfReportExporter pdf = new PdfReportExporter();

    @Test
    void theTwoExportersClaimTheirFormatAndNameTheFileAfterTheDocument() {
        assertEquals(ReportFormat.XLSX, xlsx.format());
        assertEquals(ReportFormat.PDF, pdf.format());

        ExportedReport workbook = xlsx.export(document(rows(1)));
        ExportedReport printable = pdf.export(document(rows(1)));

        assertEquals("shift-report-2026-01-27-SH-000001.xlsx", workbook.fileName());
        assertEquals(ReportMediaTypes.XLSX, workbook.contentType());
        assertEquals("shift-report-2026-01-27-SH-000001.pdf", printable.fileName());
        assertEquals(ReportMediaTypes.PDF, printable.contentType());
    }

    @Test
    void theWorkbookCarriesRealDatesAndNumbersUnderAFrozenAndFilteredHeader() throws IOException {
        byte[] bytes = xlsx.export(document(rows(3))).content();

        try (XSSFWorkbook workbook = (XSSFWorkbook) WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertEquals("Shift report", sheet.getSheetName());

            int headerRow = headerRowOf(sheet);
            assertEquals(List.of("#", "Profile", "Kp", "Works performed", "Completed", "Work complete", "Order code"),
                    headers(sheet, headerRow), "the workbook carries every column, printed or not");
            Row first = sheet.getRow(headerRow + 1);
            assertEquals(CellType.NUMERIC, first.getCell(0).getCellType());
            assertEquals(1d, first.getCell(0).getNumericCellValue());

            Cell kp = first.getCell(2);
            assertEquals(CellType.NUMERIC, kp.getCellType(), "the kp must stay a number, not become text");
            assertEquals(12847.990d, kp.getNumericCellValue(), 0.0005d);
            assertEquals("#,##0.000", kp.getCellStyle().getDataFormatString());

            Cell completed = first.getCell(4);
            assertEquals(CellType.NUMERIC, completed.getCellType());
            assertTrue(DateUtil.isCellDateFormatted(completed), "the timestamp must be a real date, not a string");
            assertEquals(LocalDateTime.of(2026, 1, 27, 22, 40), completed.getLocalDateTimeCellValue());

            assertEquals(CellType.BOOLEAN, first.getCell(5).getCellType());
            assertEquals(CellType.STRING, first.getCell(1).getCellType());
            assertEquals(3d, sheet.getRow(headerRow + 3).getCell(0).getNumericCellValue(), "one row per task");
            assertNull(sheet.getRow(headerRow + 4), "a blank row separates the table from the totals");

            PaneInformation pane = sheet.getPaneInformation();
            assertNotNull(pane, "the header has to stay in view when the rows scroll");
            assertTrue(pane.isFreezePane());
            assertEquals(headerRow + 1, pane.getHorizontalSplitPosition());

            assertEquals("A%d:G%d".formatted(headerRow + 1, headerRow + 4),
                    sheet.getCTWorksheet().getAutoFilter().getRef());
            assertTrue(sheet.getRow(headerRow).getCell(0).getCellStyle().getFont().getBold());
        }
    }

    @Test
    void anEmptyReportKeepsItsHeaderAndSaysSoInsteadOfLookingTruncated() throws IOException {
        ExportedReport report = xlsx.export(document(List.of()));

        try (XSSFWorkbook workbook = (XSSFWorkbook) WorkbookFactory.create(new ByteArrayInputStream(report.content()))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            int headerRow = headerRowOf(sheet);

            assertEquals("#", sheet.getRow(headerRow).getCell(0).getStringCellValue());
            // El filtro cubre solo la fila de encabezados: un rango cuya ultima fila fuera anterior a
            // la primera seria un error de POI, y es lo que sale de restar uno cuando no hay filas.
            assertEquals("A%d:G%d".formatted(headerRow + 1, headerRow + 1), sheet.getCTWorksheet().getAutoFilter().getRef());
            assertEquals("No rows for the requested filters.", sheet.getRow(headerRow + 1).getCell(0).getStringCellValue());
        }

        byte[] printable = pdf.export(document(List.of())).content();
        assertTrue(printable.length > 0);
        assertEquals("%PDF", new String(printable, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void aShiftWithNoRealTimesLeavesTheCellsBlankInsteadOfWritingTheWordNull() throws IOException {
        List<List<ReportValue>> unstarted = List.of(List.of(
                ReportValue.count(1), ReportValue.text((String) null), ReportValue.decimal(null, 3),
                ReportValue.text("  "), ReportValue.timestamp(null), ReportValue.flag(null),
                ReportValue.textList(List.of())));

        ExportedReport report = xlsx.export(document(unstarted));

        try (XSSFWorkbook workbook = (XSSFWorkbook) WorkbookFactory.create(new ByteArrayInputStream(report.content()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row row = sheet.getRow(headerRowOf(sheet) + 1);
            for (int column = 1; column < 7; column++) {
                assertEquals(CellType.BLANK, row.getCell(column).getCellType(), "column " + column);
            }
        }

        // En papel la ausencia se marca: una celda vacia no se distingue de una celda perdida.
        String printed = textOf(pdf.export(document(unstarted)).content(), 1);
        assertTrue(printed.contains("\u2014"), printed);
    }

    @Test
    void thePdfRepeatsItsHeaderAcrossPagesAndNumbersThemAgainstTheRealTotal() throws IOException {
        byte[] bytes = pdf.export(document(rows(140))).content();

        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));

        try (PdfReader reader = new PdfReader(bytes)) {
            assertTrue(reader.getNumberOfPages() > 1, "140 rows do not fit on one A4 page");

            // El hueco del total de paginas es un objeto aparte dentro de la pagina, asi que el
            // extractor lo devuelve separado del texto que lo precede: se comparan sin espacios.
            String firstPage = flattened(textOf(bytes, 1));
            String lastPage = textOf(bytes, reader.getNumberOfPages());
            assertTrue(firstPage.contains("Works performed"), firstPage);
            assertTrue(lastPage.contains("Works performed"), "the header must repeat on every page");
            assertTrue(firstPage.contains("Page 1 of " + reader.getNumberOfPages()), firstPage);
            assertTrue(firstPage.contains("Generated 28/01/2026 05:30"), firstPage);
        }
    }

    @Test
    void thePdfPrintsOnlyTheColumnsThatCarryWeight() throws IOException {
        String printed = textOf(pdf.export(document(rows(1))).content(), 1);

        assertTrue(printed.contains("Works performed"));
        assertFalse(printed.contains("Order code"), "a column with weight 0 belongs to the workbook only");
    }

    @Test
    void anAppendixGoesOnItsOwnSheetWithItsFilterAndAfterTheTotalsInThePdf() throws IOException {
        ReportDocument base = document(rows(2));
        ReportDocument.Table inspections = new ReportDocument.Table(
                List.of(new ReportDocument.Column("Code", 6), new ReportDocument.Column("Date", 5),
                        new ReportDocument.Column("Result", 6), new ReportDocument.Column("Asset name", 0)),
                List.of(List.of(ReportValue.text("INS-000040"), ReportValue.date(LocalDate.of(2026, 1, 28)), ReportValue.text("UNSAFE"),
                                ReportValue.text("SI-W41")),
                        List.of(ReportValue.text("INS-000041"), ReportValue.date(LocalDate.of(2026, 1, 28)), ReportValue.text("OK"),
                                ReportValue.text("12-2.27"))));
        // Mismo nombre que la hoja principal: Excel no admite dos hojas iguales, asi que la segunda se renombra.
        ReportDocument document = new ReportDocument(base.fileBaseName(), base.sheetName(), base.title(), base.subtitle(), base.layout(),
                base.generatedAt(), base.header(), base.table(), base.totals(),
                List.of(new ReportDocument.Appendix("Inspections", inspections),
                        new ReportDocument.Appendix("Shift report", new ReportDocument.Table(inspections.columns(), List.of()))));

        try (XSSFWorkbook workbook = (XSSFWorkbook) WorkbookFactory.create(new ByteArrayInputStream(xlsx.export(document).content()))) {
            assertEquals(3, workbook.getNumberOfSheets());
            XSSFSheet sheet = workbook.getSheet("Inspections");
            assertNotNull(sheet, "each appendix is its own sheet");
            assertEquals("Daily shift report SH-000001 — Inspections", sheet.getRow(0).getCell(0).getStringCellValue());
            int headerRow = 2;
            assertEquals(List.of("Code", "Date", "Result", "Asset name"), headers(sheet, headerRow), "the workbook carries every column");
            assertEquals("INS-000041", sheet.getRow(headerRow + 2).getCell(0).getStringCellValue());
            assertTrue(DateUtil.isCellDateFormatted(sheet.getRow(headerRow + 1).getCell(1)), "a date stays a date in an appendix too");
            assertEquals("A3:D5", sheet.getCTWorksheet().getAutoFilter().getRef());
            assertEquals(headerRow + 1, sheet.getPaneInformation().getHorizontalSplitPosition());

            XSSFSheet empty = workbook.getSheet("Shift report (2)");
            assertNotNull(empty, "a repeated sheet name gets a suffix instead of failing the export");
            assertEquals("No rows for the requested filters.", empty.getRow(headerRow + 1).getCell(0).getStringCellValue());
            assertEquals("A3:D3", empty.getCTWorksheet().getAutoFilter().getRef());
        }

        byte[] bytes = pdf.export(document).content();
        String printed;
        try (PdfReader reader = new PdfReader(bytes)) {
            StringBuilder all = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                all.append(textOf(bytes, page)).append('\n');
            }
            printed = all.toString();
        }
        // El extractor devuelve los parrafos antes que las tablas, asi que el orden se mira entre tablas:
        // las filas del anexo van detras de los totales.
        assertTrue(printed.contains("Inspections"), printed);
        assertTrue(printed.indexOf("INS-000040") > printed.indexOf("Tasks completed"), "the appendix comes after the totals: " + printed);
        assertFalse(printed.contains("SI-W41"), "a column with weight 0 is not printed in an appendix either");
    }

    /**
     * El parte de un turno de punta a punta, de su JSON al fichero: los instrumentos de mto-resources
     * ({@code V22}) en su anexo, con la calibracion como fecha de verdad en el .xlsx y despues de los
     * totales en el PDF.
     */
    @Test
    void theShiftReportCarriesItsInstrumentsInTheirOwnAppendixInBothFormats() throws IOException {
        ReportExportService exports = exportService();
        ShiftReportResponse report = shiftReportWith(List.of(
                new ShiftInstrumentResponse(UUID.randomUUID(), "EP-07", "Earthing pole 7", "EARTHING_POLE", LocalDate.of(2027, 1, 10)),
                new ShiftInstrumentResponse(UUID.randomUUID(), "VD-02", "Voltage detector 2", "VOLTAGE_DETECTOR", null)));

        byte[] workbookBytes = exports.exportShiftReport(report, ReportFormat.XLSX).content();
        try (XSSFWorkbook workbook = (XSSFWorkbook) WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
            XSSFSheet sheet = workbook.getSheet("Instruments");
            assertNotNull(sheet, "the instruments are an appendix of their own, after the crew and the vehicles");
            assertEquals(workbook.getSheetIndex("Vehicles") + 1, workbook.getSheetIndex(sheet));
            int headerRow = 2;
            assertEquals(List.of("Code", "Name", "Kind", "Calibrated until"), headers(sheet, headerRow));
            Row pole = sheet.getRow(headerRow + 1);
            assertEquals("EP-07", pole.getCell(0).getStringCellValue());
            assertEquals("EARTHING_POLE", pole.getCell(2).getStringCellValue());
            assertTrue(DateUtil.isCellDateFormatted(pole.getCell(3)), "the calibration date stays a date");
            assertEquals(LocalDate.of(2027, 1, 10), pole.getCell(3).getLocalDateTimeCellValue().toLocalDate());
            Row detector = sheet.getRow(headerRow + 2);
            assertEquals("VD-02", detector.getCell(0).getStringCellValue());
            Cell noCalibration = detector.getCell(3);
            assertTrue(noCalibration == null || noCalibration.getCellType() == CellType.BLANK, "without a calibration the cell is empty");
        }

        byte[] pdfBytes = exports.exportShiftReport(report, ReportFormat.PDF).content();
        StringBuilder all = new StringBuilder();
        try (PdfReader reader = new PdfReader(pdfBytes)) {
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                all.append(textOf(pdfBytes, page)).append('\n');
            }
        }
        String printed = all.toString();
        assertTrue(printed.contains("Instruments"), printed);
        assertTrue(printed.contains("Calibrated until"), printed);
        assertTrue(printed.contains("10/01/2027"), "the date is printed as a date: " + printed);
        assertTrue(printed.indexOf("VD-02") > printed.indexOf("Tasks completed"), "the appendix comes after the totals: " + printed);
    }

    @Test
    void anAppendixNeedsATitleAndColumns() {
        ReportDocument.Table table = new ReportDocument.Table(List.of(new ReportDocument.Column("Code", 1)), List.of());
        assertEquals("Inspections", new ReportDocument.Appendix("Inspections", table).title());
        assertThrows(IllegalArgumentException.class, () -> new ReportDocument.Appendix(" ", table));
        assertThrows(IllegalArgumentException.class,
                () -> new ReportDocument.Appendix("Inspections", new ReportDocument.Table(List.of(), List.of())));
        assertEquals(List.of(), document(rows(1)).appendices(), "a report without appendices has an empty list, not null");
    }

    /**
     * El servicio de exportacion con los dos exportadores de verdad. Su implementacion es package-private,
     * como todas las de {@code service.impl}: se construye por reflexion, como la construye Spring, para
     * probar por su interfaz publica la disposicion real del parte y no una copia de ella.
     */
    private ReportExportService exportService() {
        try {
            Class<?> type = ClassUtils.forName("com.alejandro.mtomaintenance.application.service.impl.ReportExportServiceImpl",
                    ReportExportLayerTest.class.getClassLoader());
            return (ReportExportService) BeanUtils.instantiateClass(type.getDeclaredConstructor(List.class, ReportProperties.class),
                    List.<ReportExporter>of(xlsx, pdf), new ReportProperties("UTC"));
        } catch (ReflectiveOperationException missing) {
            throw new AssertionError("the export service implementation is not where the layout expects it", missing);
        }
    }

    private static ShiftReportResponse shiftReportWith(List<ShiftInstrumentResponse> instruments) {
        MaintenanceShiftResponse shift = new MaintenanceShiftResponse(UUID.randomUUID(), "SH-000001", LocalDate.of(2026, 1, 27),
                null, null, null, PossessionType.FULL, null, null, null, null, null, null,
                List.of(), null, null, 6L, List.of(2L), null, null, null, null, null, List.of(), List.of(), instruments, null,
                ShiftStatus.PLANNED, null, null, null);
        return new ShiftReportResponse(shift, 0, 0, 0, 0, 0, List.of(), 0, List.of());
    }

    private static String flattened(String text) {
        return text.replaceAll("\\s+", " ");
    }

    private static String textOf(byte[] bytes, int page) throws IOException {
        try (PdfReader reader = new PdfReader(bytes)) {
            return new PdfTextExtractor(reader).getTextFromPage(page);
        }
    }

    /** La fila de encabezados es la primera cuya celda A dice "#": encima va la cabecera del turno. */
    private static int headerRowOf(Sheet sheet) {
        for (Row row : sheet) {
            Cell first = row.getCell(0);
            if (first != null && first.getCellType() == CellType.STRING && "#".equals(first.getStringCellValue())) {
                return row.getRowNum();
            }
        }
        throw new AssertionError("the sheet has no table header row");
    }

    private static List<String> headers(Sheet sheet, int headerRow) {
        List<String> headers = new ArrayList<>();
        sheet.getRow(headerRow).forEach(cell -> headers.add(cell.getStringCellValue()));
        return headers;
    }

    private static ReportDocument document(List<List<ReportValue>> rows) {
        return new ReportDocument(
                "shift-report-2026-01-27-SH-000001",
                "Shift report",
                "Daily shift report SH-000001",
                "Team A - Rishpon · 2026-01-27 · tracks 2",
                ReportDocument.Layout.LANDSCAPE,
                GENERATED_AT,
                List.of(
                        new ReportDocument.Field("Shift", ReportValue.text("SH-000001")),
                        new ReportDocument.Field("Date", ReportValue.date(LocalDate.of(2026, 1, 27))),
                        new ReportDocument.Field("Measurement equipment", ReportValue.text("Laser, dynamometer")),
                        new ReportDocument.Field("Actual end", ReportValue.EMPTY)),
                new ReportDocument.Table(
                        List.of(
                                new ReportDocument.Column("#", 3),
                                new ReportDocument.Column("Profile", 8),
                                new ReportDocument.Column("Kp", 6),
                                new ReportDocument.Column("Works performed", 18),
                                new ReportDocument.Column("Completed", 7),
                                new ReportDocument.Column("Work complete", 5),
                                new ReportDocument.Column("Order code", 0)),
                        rows),
                List.of(new ReportDocument.Field("Tasks completed", ReportValue.count(rows.size()))));
    }

    private static List<List<ReportValue>> rows(int count) {
        List<List<ReportValue>> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rows.add(List.of(
                    ReportValue.count(index + 1),
                    ReportValue.text("12-2.%02d".formatted(index)),
                    ReportValue.decimal(new BigDecimal("12847.990").add(BigDecimal.valueOf(index)), 3),
                    ReportValue.text("Insulators checked and cleaned, cantilever geometry measured and corrected"),
                    ReportValue.timestamp(LocalDateTime.of(2026, 1, 27, 22, 40)),
                    ReportValue.flag(true),
                    ReportValue.text("MO-000001")));
        }
        return rows;
    }
}
