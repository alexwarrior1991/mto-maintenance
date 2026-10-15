package com.alejandro.mtomaintenance.application.dto.export;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Un informe listo para escribir, sin saber todavia en que formato.
 *
 * <p>Es lo unico que ven los exportadores, y es la razon de que haya una clase de POI y una de
 * OpenPDF en vez de tres de cada: los tres informes tienen la misma forma —unos pares etiqueta/valor
 * arriba, una tabla y unos totales— y lo que cambia entre ellos son las etiquetas, las columnas y el
 * nombre del fichero, que decide la capa de aplicacion.</p>
 *
 * <p>{@code header} y {@code totals} no son dos clases de dato distintas: son la posicion, encima y
 * debajo de la tabla. El parte pone la cabecera del turno arriba y sus contadores abajo, porque
 * resumen las filas; el mensual pone sus contadores arriba, porque son el informe, y deja la tabla
 * de materiales como desglose.</p>
 *
 * <p>{@code table} puede ser {@code null} y los exportadores se saltan la seccion. Ninguno de los
 * tres informes de hoy lo necesita, pero exigir tabla convertiria el primer informe que no la tenga
 * en una fila inventada.</p>
 *
 * <p>{@code generatedAt} llega ya en la zona del informe, igual que {@link ReportValue.Timestamp}:
 * los exportadores escriben fechas, no las situan.</p>
 *
 * <p>{@code appendices} son tablas de otra cosa que acompañan al informe: las inspecciones del
 * turno no son tareas y no caben en sus columnas. Van despues de los totales, cada una con su
 * titulo: en el .xlsx en su propia hoja, con su autofiltro, y en el PDF a continuacion. Casi todos
 * los informes no tienen ninguno.</p>
 *
 * <p>Ejemplo, el parte del turno SH-000001 tal como lo construye {@code ShiftReportLayout.of}:</p>
 * <pre>{@code
 * new ReportDocument("shift-report-2026-01-27-SH-000001", "Shift report", "Daily shift report SH-000001", subtitle(shift),
 *         Layout.LANDSCAPE, time.generated(), header(shift, time), new Table(COLUMNS, rows), totals(report), appendices)
 * }</pre>
 */
public record ReportDocument(
        String fileBaseName,
        String sheetName,
        String title,
        String subtitle,
        Layout layout,
        LocalDateTime generatedAt,
        List<Field> header,
        Table table,
        List<Field> totals,
        List<Appendix> appendices
) {

    public ReportDocument {
        header = List.copyOf(header);
        totals = List.copyOf(totals);
        appendices = appendices == null ? List.of() : List.copyOf(appendices);
    }

    /** Un informe sin anexos, que son casi todos. */
    public ReportDocument(String fileBaseName, String sheetName, String title, String subtitle, Layout layout, LocalDateTime generatedAt,
                          List<Field> header, Table table, List<Field> totals) {
        this(fileBaseName, sheetName, title, subtitle, layout, generatedAt, header, table, totals, List.of());
    }

    /**
     * Una tabla aparte con su titulo, que tambien es el nombre de su hoja en el .xlsx.
     *
     * <p>La tabla es obligatoria y puede no tener filas: un turno sin inspecciones las enseña igual,
     * con la nota de que no hay, en vez de dejar al lector preguntandose si la seccion se perdio.</p>
     */
    public record Appendix(String title, Table table) {

        public Appendix {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("A report appendix needs a title");
            }
            if (table == null || table.columns().isEmpty()) {
                throw new IllegalArgumentException("Report appendix '" + title + "' needs a table with columns");
            }
        }
    }

    /** Orientacion del papel. Solo la mira el PDF: una hoja de calculo no tiene pagina. */
    public enum Layout {
        PORTRAIT,
        LANDSCAPE
    }

    /** Pareja etiqueta/valor de la cabecera o de los totales. */
    public record Field(String label, ReportValue value) {
    }

    public record Table(List<Column> columns, List<List<ReportValue>> rows) {

        /**
         * Una fila con mas o menos valores que columnas no se puede escribir bien: en la hoja
         * desplazaria los datos una columna y en el PDF tumbaria la tabla entera o la dejaria coja.
         * Falla aqui, donde se ve que el culpable es la disposicion, y no dentro de POI con una fila
         * de por medio.
         */
        public Table {
            columns = List.copyOf(columns);
            rows = rows.stream().map(List::copyOf).toList();
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).size() != columns.size()) {
                    throw new IllegalArgumentException(("Report row %d has %d values but the table declares %d columns")
                            .formatted(index, rows.get(index).size(), columns.size()));
                }
            }
        }
    }

    /**
     * Columna de la tabla.
     *
     * @param printWeight ancho relativo al imprimir; <b>{@code 0} significa que la columna no se
     *                    imprime</b>. Las dieciocho columnas del parte caben en una hoja de calculo,
     *                    donde sobra ancho, pero no en un A4: meterlas todas estrujaria justo las de
     *                    texto largo —trabajos realizados, defectos—, que son las que hay que leer.
     *                    Va en un solo campo y no en un {@code boolean} mas un peso porque asi no
     *                    existe la combinacion contradictoria "se imprime con ancho cero". El peso
     *                    describe la columna, no el formato: un exportador nuevo decide por su
     *                    cuenta si lo respeta o si le sobra sitio.
     */
    public record Column(String header, int printWeight) {

        public boolean printed() {
            return printWeight > 0;
        }
    }

    public boolean hasTable() {
        return table != null && !table.columns().isEmpty();
    }
}
