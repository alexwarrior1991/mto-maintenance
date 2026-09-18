package com.alejandro.mtomaintenance.infrastructure.export;

import com.alejandro.mtomaintenance.application.dto.export.ExportedReport;
import com.alejandro.mtomaintenance.application.dto.export.ReportDocument;
import com.alejandro.mtomaintenance.application.dto.export.ReportFormat;
import com.alejandro.mtomaintenance.application.dto.export.ReportMediaTypes;
import com.alejandro.mtomaintenance.application.dto.export.ReportValue;
import com.alejandro.mtomaintenance.application.service.ReportExporter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Escribe el informe como un .xlsx con POI. Es el unico sitio del servicio que importa
 * {@code org.apache.poi}, y sirve a los tres informes porque todos llegan como
 * {@link ReportDocument}.
 *
 * <p>Una hoja por informe, con la cabecera en parejas etiqueta/valor arriba y la tabla debajo, que es
 * la forma que tenian las hojas a las que esto sustituye. El fichero se entrega al cliente, asi que
 * al abrirlo no hay que tocar nada: encabezados en negrita y congelados, autofiltro, anchos
 * calculados a partir del contenido, y fechas y numeros escritos como fechas y numeros.</p>
 *
 * <h2>Por que se congela tambien la cabecera del turno</h2>
 *
 * <p>Porque un .xlsx no sabe congelar una banda por el medio: el panel congelado siempre empieza en
 * A1, de modo que congelar la fila de encabezados arrastra todo lo que hay encima. La alternativa
 * —bajar la cabecera del turno a otra hoja— separaria el parte en dos. Se congela desde arriba y la
 * cabecera se dispone en varias parejas por fila, no en una columna de veintitres, para que el
 * bloque quede en siete filas y siga cabiendo media pantalla de datos.</p>
 */
@Component
class XlsxReportExporter implements ReportExporter {

    /** POI mide el ancho de columna en 1/256 de caracter de la fuente por defecto. */
    private static final int WIDTH_UNIT = 256;

    private static final int MIN_COLUMN_CHARS = 9;
    private static final int MAX_COLUMN_CHARS = 42;

    /** Una columna cuyo contenido pase de aqui ajusta el texto en vez de estirarse sin fin. */
    private static final int WRAP_FROM_CHARS = 28;

    private static final int PADDING_CHARS = 2;
    private static final int LABEL_CHARS = 22;
    private static final int VALUE_CHARS = 26;

    /** Tope de columnas que puede ocupar una etiqueta o un valor de la cabecera. */
    private static final int MAX_MERGE_COLUMNS = 8;

    private static final int DEFAULT_CHARS_OUTSIDE_TABLE = 10;

    @Override
    public ReportFormat format() {
        return ReportFormat.XLSX;
    }

    @Override
    public ExportedReport export(ReportDocument document) {
        // El libro y sus estilos nacen y mueren aqui: un CellStyle esta atado a su Workbook, asi que
        // guardarlo en un campo del bean lo compartiria entre peticiones y corromperia los ficheros.
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(document.sheetName()));
            write(sheet, new XlsxStyles(workbook), document);

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            workbook.write(bytes);
            return new ExportedReport(document.fileBaseName() + ".xlsx", ReportMediaTypes.XLSX, bytes.toByteArray());
        } catch (IOException exception) {
            // Un ByteArrayOutputStream no falla; queda por si POI la lanza al serializar el paquete.
            throw new IllegalStateException("The " + document.fileBaseName() + " workbook could not be written", exception);
        }
    }

    private static void write(Sheet sheet, XlsxStyles styles, ReportDocument document) {
        Columns columns = Columns.of(document);
        for (int column = 0; column < columns.chars().length; column++) {
            sheet.setColumnWidth(column, columns.chars()[column] * WIDTH_UNIT);
        }

        int rowIndex = 0;
        rowIndex = writeText(sheet, rowIndex, document.title(), styles.title);
        rowIndex = writeText(sheet, rowIndex, document.subtitle(), styles.subtitle);
        rowIndex++;

        rowIndex = writeFields(sheet, styles, columns, header(document), rowIndex);

        if (document.hasTable()) {
            rowIndex++;
            rowIndex = writeTable(sheet, styles, columns, document.table(), rowIndex);
        }

        if (!document.totals().isEmpty()) {
            rowIndex++;
            writeFields(sheet, styles, columns, document.totals(), rowIndex);
        }
    }

    /** La fecha de generacion se añade aqui, y no en las disposiciones, para que la lleven los tres. */
    private static List<ReportDocument.Field> header(ReportDocument document) {
        List<ReportDocument.Field> header = new ArrayList<>(document.header());
        header.add(new ReportDocument.Field("Generated", ReportValue.timestamp(document.generatedAt())));
        return header;
    }

    private static int writeText(Sheet sheet, int rowIndex, String text, CellStyle style) {
        if (text == null || text.isBlank()) {
            return rowIndex;
        }
        Cell cell = sheet.createRow(rowIndex).createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(style);
        return rowIndex + 1;
    }

    private static int writeFields(Sheet sheet, XlsxStyles styles, Columns columns, List<ReportDocument.Field> fields, int startRow) {
        int perRow = pairsPerRow(columns.chars().length);
        int rowIndex = startRow;
        Row row = null;
        int column = 0;

        for (int index = 0; index < fields.size(); index++) {
            if (index % perRow == 0) {
                row = sheet.createRow(rowIndex++);
                column = 0;
            }
            ReportDocument.Field field = fields.get(index);
            column = writeMerged(sheet, row, column, LABEL_CHARS, columns,
                    cell -> writeLabel(cell, field.label(), styles));
            column = writeMerged(sheet, row, column, VALUE_CHARS, columns,
                    cell -> writeValue(cell, field.value(), styles, false));
        }

        return rowIndex;
    }

    /**
     * Cuantas parejas etiqueta/valor caben en una fila de la cabecera. Con una sola pareja por fila,
     * las veintitres de un parte darian veintitres filas congeladas encima de la tabla.
     */
    private static int pairsPerRow(int columnCount) {
        return columnCount >= 8 ? 3 : columnCount >= 4 ? 2 : 1;
    }

    /**
     * Escribe una celda de la cabecera fusionando hacia la derecha hasta juntar el ancho pedido.
     *
     * <p>La cabecera comparte columnas con la tabla, y esas columnas estan dimensionadas para los
     * datos: "Measurement equipment" sobre la columna del numero de fila saldria cortado en cuanto la
     * celda de al lado tenga contenido. Cada llamada devuelve la primera columna libre, de modo que
     * dos fusiones de la misma fila no pueden solaparse —que es lo que POI rechaza en tiempo de
     * ejecucion— y la cabecera puede pasarse del ancho de la tabla sin problema: lo que limita una
     * region fusionada es la ultima columna de la hoja, no las que se hayan escrito.</p>
     */
    private static int writeMerged(Sheet sheet, Row row, int firstColumn, int chars, Columns columns, Consumer<Cell> write) {
        int lastColumn = firstColumn;
        int accumulated = columns.charsAt(firstColumn);

        while (accumulated < chars && lastColumn - firstColumn < MAX_MERGE_COLUMNS) {
            lastColumn++;
            accumulated += columns.charsAt(lastColumn);
        }

        write.accept(row.createCell(firstColumn));

        if (lastColumn > firstColumn) {
            sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), firstColumn, lastColumn));
        }

        return lastColumn + 1;
    }

    private static int writeTable(Sheet sheet, XlsxStyles styles, Columns columns, ReportDocument.Table table, int startRow) {
        int headerRow = startRow;
        Row header = sheet.createRow(headerRow);
        for (int column = 0; column < table.columns().size(); column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(table.columns().get(column).header());
            cell.setCellStyle(styles.tableHeader);
        }

        int rowIndex = headerRow + 1;
        for (List<ReportValue> values : table.rows()) {
            Row row = sheet.createRow(rowIndex++);
            for (int column = 0; column < values.size(); column++) {
                writeValue(row.createCell(column), values.get(column), styles, columns.wraps()[column]);
            }
        }

        sheet.createFreezePane(0, headerRow + 1);
        // Con cero filas el rango es la propia fila de encabezados: un rango con lastRow < firstRow
        // seria un IllegalArgumentException, y la resta habitual (-1) lo produce justo en ese caso.
        sheet.setAutoFilter(new CellRangeAddress(headerRow, headerRow + table.rows().size(), 0, table.columns().size() - 1));

        if (table.rows().isEmpty()) {
            // Un libro con solo los encabezados se lee como un fichero truncado. La nota va por
            // debajo del rango del autofiltro: una region fusionada dentro del rango hace que Excel
            // proteste al abrirlo.
            Cell note = sheet.createRow(rowIndex).createCell(0);
            note.setCellValue("No rows for the requested filters.");
            note.setCellStyle(styles.note);
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, table.columns().size() - 1));
            rowIndex++;
        }

        return rowIndex;
    }

    private static void writeLabel(Cell cell, String label, XlsxStyles styles) {
        cell.setCellValue(label);
        cell.setCellStyle(styles.label);
    }

    /**
     * Sin {@code default}: {@link ReportValue} es sellada, asi que un tipo de valor nuevo deja de
     * compilar aqui en vez de salir por el {@code toString()} y convertir un numero en texto.
     */
    private static void writeValue(Cell cell, ReportValue value, XlsxStyles styles, boolean wrap) {
        switch (value) {
            case ReportValue.Empty ignored -> cell.setBlank();
            case ReportValue.Text text -> {
                cell.setCellValue(text.value());
                cell.setCellStyle(wrap ? styles.wrappedText : styles.text);
            }
            // Cada elemento en su linea dentro de la celda: los tipos de trabajo y los materiales de
            // una tarea se leen de un vistazo, y siguen siendo una sola celda que se puede filtrar.
            case ReportValue.TextList list -> {
                cell.setCellValue(String.join("\n", list.values()));
                cell.setCellStyle(styles.wrappedText);
            }
            case ReportValue.Flag flag -> {
                cell.setCellValue(flag.value());
                cell.setCellStyle(styles.text);
            }
            case ReportValue.Count count -> {
                cell.setCellValue(count.value());
                cell.setCellStyle(styles.integer);
            }
            case ReportValue.Decimal decimal -> {
                cell.setCellValue(decimal.value().doubleValue());
                cell.setCellStyle(styles.decimal(decimal.scale()));
            }
            // Se escribe la proporcion tal cual: el formato 0.0% ya la muestra multiplicada por cien.
            case ReportValue.Ratio ratio -> {
                cell.setCellValue(ratio.value().doubleValue());
                cell.setCellStyle(styles.ratio);
            }
            case ReportValue.Date date -> {
                cell.setCellValue(date.value());
                cell.setCellStyle(styles.date);
            }
            case ReportValue.Timestamp timestamp -> {
                cell.setCellValue(timestamp.value());
                cell.setCellStyle(styles.timestamp);
            }
        }
    }

    /**
     * Anchos y ajuste de texto de cada columna, medidos sobre el contenido.
     *
     * <p>No se usa {@code autoSizeColumn}: necesita las metricas de fuente de AWT, que una imagen
     * headless puede no traer, y entonces dimensionaria las columnas segun las fuentes que le hayan
     * quedado dentro. Contar caracteres da el mismo resultado en cualquier maquina.</p>
     */
    private record Columns(int[] chars, boolean[] wraps) {

        static Columns of(ReportDocument document) {
            if (!document.hasTable()) {
                return new Columns(new int[] {LABEL_CHARS, VALUE_CHARS}, new boolean[] {false, false});
            }

            List<ReportDocument.Column> columns = document.table().columns();
            int[] measured = new int[columns.size()];
            for (int column = 0; column < columns.size(); column++) {
                measured[column] = columns.get(column).header().length();
            }
            for (List<ReportValue> row : document.table().rows()) {
                for (int column = 0; column < measured.length; column++) {
                    measured[column] = Math.max(measured[column], displayLength(row.get(column)));
                }
            }

            int[] chars = new int[measured.length];
            boolean[] wraps = new boolean[measured.length];
            for (int column = 0; column < measured.length; column++) {
                chars[column] = Math.clamp(measured[column] + PADDING_CHARS, MIN_COLUMN_CHARS, MAX_COLUMN_CHARS);
                wraps[column] = measured[column] > WRAP_FROM_CHARS;
            }
            return new Columns(chars, wraps);
        }

        /** Las columnas por las que la cabecera se pasa de la tabla no tienen ancho medido. */
        int charsAt(int column) {
            return column < chars.length ? chars[column] : DEFAULT_CHARS_OUTSIDE_TABLE;
        }
    }

    private static int displayLength(ReportValue value) {
        return switch (value) {
            case ReportValue.Empty ignored -> 0;
            case ReportValue.Text text -> text.value().length();
            case ReportValue.TextList list -> list.values().stream().mapToInt(String::length).max().orElse(0);
            case ReportValue.Flag ignored -> 5;
            case ReportValue.Count count -> String.valueOf(count.value()).length();
            case ReportValue.Decimal decimal ->
                    decimal.value().setScale(decimal.scale(), RoundingMode.HALF_UP).toPlainString().length();
            case ReportValue.Ratio ignored -> 7;
            case ReportValue.Date ignored -> 10;
            case ReportValue.Timestamp ignored -> 16;
        };
    }

    /**
     * Los estilos del libro, creados una sola vez.
     *
     * <p>Un .xlsx admite 64.000 estilos y POI falla al pasarse. Un estilo por celda los agota con
     * unas 3.400 filas de diecinueve columnas, asi que el estilo se crea por formato y se reparte
     * entre todas las celdas que lo usan. Vive dentro de la exportacion, nunca como campo del
     * componente: un {@code CellStyle} pertenece a su {@code Workbook}.</p>
     */
    private static final class XlsxStyles {

        private final Workbook workbook;
        private final DataFormat dataFormat;
        private final Map<Integer, CellStyle> decimals = new HashMap<>();

        private final CellStyle title;
        private final CellStyle subtitle;
        private final CellStyle label;
        private final CellStyle tableHeader;
        private final CellStyle text;
        private final CellStyle wrappedText;
        private final CellStyle integer;
        private final CellStyle ratio;
        private final CellStyle date;
        private final CellStyle timestamp;
        private final CellStyle note;

        XlsxStyles(Workbook workbook) {
            this.workbook = workbook;
            this.dataFormat = workbook.createDataFormat();

            this.title = styled(bold(13));
            this.subtitle = styled(italic());
            this.label = styled(bold(10));
            this.text = styled(null);
            this.note = styled(italic());

            this.tableHeader = styled(bold(10));
            this.tableHeader.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            this.tableHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            this.tableHeader.setAlignment(HorizontalAlignment.CENTER);
            this.tableHeader.setVerticalAlignment(VerticalAlignment.CENTER);
            this.tableHeader.setWrapText(true);

            this.wrappedText = styled(null);
            this.wrappedText.setWrapText(true);
            this.wrappedText.setVerticalAlignment(VerticalAlignment.TOP);

            this.integer = numeric("#,##0");
            this.ratio = numeric("0.0%");
            this.date = numeric("dd/mm/yyyy");
            // En un formato de Excel 'mm' son minutos solo cuando sigue a una hora; en cualquier
            // otro sitio son meses. Por eso el patron va en minusculas y con hh delante.
            this.timestamp = numeric("dd/mm/yyyy hh:mm");
        }

        CellStyle decimal(int scale) {
            return decimals.computeIfAbsent(scale,
                    key -> numeric(key <= 0 ? "#,##0" : "#,##0." + "0".repeat(key)));
        }

        private CellStyle numeric(String pattern) {
            CellStyle style = styled(null);
            style.setDataFormat(dataFormat.getFormat(pattern));
            style.setAlignment(HorizontalAlignment.RIGHT);
            return style;
        }

        private CellStyle styled(Font font) {
            CellStyle style = workbook.createCellStyle();
            if (font != null) {
                style.setFont(font);
            }
            return style;
        }

        private Font bold(int points) {
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) points);
            return font;
        }

        private Font italic() {
            Font font = workbook.createFont();
            font.setItalic(true);
            return font;
        }
    }
}
