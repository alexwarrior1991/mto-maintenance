package com.alejandro.mtomaintenance.infrastructure.export;

import com.alejandro.mtomaintenance.application.dto.export.ExportedReport;
import com.alejandro.mtomaintenance.application.dto.export.ReportDocument;
import com.alejandro.mtomaintenance.application.dto.export.ReportFormat;
import com.alejandro.mtomaintenance.application.dto.export.ReportMediaTypes;
import com.alejandro.mtomaintenance.application.dto.export.ReportValue;
import com.alejandro.mtomaintenance.application.service.ReportExporter;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Escribe el informe como un PDF con OpenPDF. Es el unico sitio del servicio que importa
 * {@code com.lowagie.text}, y sirve a los tres informes por la misma razon que el exportador de
 * Excel: todos llegan como {@link ReportDocument}.
 *
 * <p>A4 apaisado para el parte y el avance, vertical para el mensual —que son doce contadores y una
 * tabla corta, y en apaisado dejaria medio folio en blanco—. La cabecera de la tabla se repite en
 * cada pagina y el pie lleva el numero de pagina y la fecha de generacion.</p>
 *
 * <p>Se imprimen solo las columnas con peso: dieciocho columnas en un A4 apaisado dejarian unos seis
 * caracteres a cada una, y las que hay que leer son las de texto largo. El .xlsx si las lleva todas,
 * y es una diferencia deliberada entre los dos formatos, anotada en {@code docs/04-rest-api.md}.</p>
 */
@Component
class PdfReportExporter implements ReportExporter {

    private static final float SIDE_MARGIN = 28f;
    private static final float TOP_MARGIN = 32f;

    /** Deja sitio al pie, que se pinta por debajo del area de texto. */
    private static final float BOTTOM_MARGIN = 40f;

    private static final float SPACING = 10f;
    private static final float CELL_PADDING = 3f;
    private static final int HEADER_FIELD_COLUMNS = 4;
    private static final Color TABLE_HEADER_BACKGROUND = new Color(228, 228, 228);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Raya de ausencia. Escapada por lo mismo que el separador del subtitulo. */
    private static final String EMPTY_MARK = "\u2014";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public ReportFormat format() {
        return ReportFormat.PDF;
    }

    @Override
    public ExportedReport export(ReportDocument document) {
        Rectangle page = document.layout() == ReportDocument.Layout.LANDSCAPE ? PageSize.A4.rotate() : PageSize.A4;
        Document pdf = new Document(page, SIDE_MARGIN, SIDE_MARGIN, TOP_MARGIN, BOTTOM_MARGIN);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(pdf, bytes);
            // Antes de open(): es open() quien dispara onOpenDocument, que es donde el pie reserva el
            // hueco del total de paginas. Registrarlo despues lo dejaria a null y reventaria al
            // cerrar la primera pagina.
            writer.setPageEvent(new PdfReportFooter(TIMESTAMP.format(document.generatedAt())));
            pdf.open();
            write(pdf, document);
            // close() vuelca el trailer y dispara onCloseDocument; leer los bytes antes daria un PDF
            // truncado que ningun lector abre.
            pdf.close();
        } catch (DocumentException exception) {
            throw new IllegalStateException("The " + document.fileBaseName() + " PDF could not be written", exception);
        }

        return new ExportedReport(document.fileBaseName() + ".pdf", ReportMediaTypes.PDF, bytes.toByteArray());
    }

    private static void write(Document pdf, ReportDocument document) throws DocumentException {
        // Siempre hay titulo: cerrar un documento al que no se ha añadido nada falla, y un informe
        // vacio es justo el caso en que el resto de secciones pueden no aportar ni una linea.
        pdf.add(paragraph(document.title(), ReportFonts.TITLE, SPACING / 2));

        if (document.subtitle() != null && !document.subtitle().isBlank()) {
            pdf.add(paragraph(document.subtitle(), ReportFonts.SUBTITLE, SPACING));
        }

        if (!document.header().isEmpty()) {
            pdf.add(fields(document.header()));
        }

        if (document.hasTable()) {
            pdf.add(table(document.table()));
        }

        if (!document.totals().isEmpty()) {
            pdf.add(fields(document.totals()));
        }
    }

    private static Paragraph paragraph(String text, Font font, float spacingAfter) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingAfter(spacingAfter);
        return paragraph;
    }

    /** Los pares etiqueta/valor, en dos parejas por linea y sin bordes: es una ficha, no una tabla. */
    private static PdfPTable fields(List<ReportDocument.Field> fields) throws DocumentException {
        PdfPTable table = new PdfPTable(HEADER_FIELD_COLUMNS);
        table.setWidthPercentage(100);
        table.setWidths(new float[] {22f, 28f, 22f, 28f});
        table.setSpacingAfter(SPACING);

        for (ReportDocument.Field field : fields) {
            table.addCell(borderless(new Phrase(field.label(), ReportFonts.LABEL)));
            table.addCell(borderless(new Phrase(render(field.value()), ReportFonts.VALUE)));
        }
        // Una lista impar deja media fila sin celdas y OpenPDF descarta la fila entera al componer.
        if (fields.size() % 2 != 0) {
            table.addCell(borderless(new Phrase("", ReportFonts.LABEL)));
            table.addCell(borderless(new Phrase("", ReportFonts.VALUE)));
        }

        return table;
    }

    private static PdfPTable table(ReportDocument.Table source) throws DocumentException {
        List<ReportDocument.Column> printed = source.columns().stream().filter(ReportDocument.Column::printed).toList();

        PdfPTable table = new PdfPTable(printed.size());
        table.setWidthPercentage(100);
        table.setWidths(weights(printed));
        // Antes de añadir ninguna celda de datos: es lo que hace que la cabecera se repita en cada
        // pagina, y lo que cuenta como cabecera son las primeras celdas que se añadan.
        table.setHeaderRows(1);
        table.setSpacingAfter(SPACING);

        for (ReportDocument.Column column : printed) {
            PdfPCell cell = new PdfPCell(new Phrase(column.header(), ReportFonts.TABLE_HEADER));
            cell.setBackgroundColor(TABLE_HEADER_BACKGROUND);
            cell.setPadding(CELL_PADDING);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        if (source.rows().isEmpty()) {
            PdfPCell note = new PdfPCell(new Phrase("No rows for the requested filters.", ReportFonts.NOTE));
            note.setColspan(printed.size());
            note.setPadding(CELL_PADDING);
            note.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(note);
            return table;
        }

        for (List<ReportValue> row : source.rows()) {
            for (int column = 0; column < source.columns().size(); column++) {
                if (!source.columns().get(column).printed()) {
                    continue;
                }
                ReportValue value = row.get(column);
                // Una celda construida con un Phrase compone el texto y crece hacia abajo: los
                // trabajos realizados y los defectos ajustan solos. Solo un setFixedHeight los
                // cortaria, y por eso no hay ninguno.
                PdfPCell cell = new PdfPCell(new Phrase(render(value), ReportFonts.CELL));
                cell.setPadding(CELL_PADDING);
                cell.setHorizontalAlignment(alignment(value));
                cell.setVerticalAlignment(Element.ALIGN_TOP);
                table.addCell(cell);
            }
        }

        return table;
    }

    private static float[] weights(List<ReportDocument.Column> printed) {
        float[] weights = new float[printed.size()];
        for (int column = 0; column < printed.size(); column++) {
            weights[column] = printed.get(column).printWeight();
        }
        return weights;
    }

    private static PdfPCell borderless(Phrase phrase) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(2f);
        return cell;
    }

    /**
     * Sin {@code default}, igual que en el exportador de Excel: {@link ReportValue} es sellada y un
     * tipo nuevo tiene que decidir aqui como se imprime en vez de caer en un {@code toString()}.
     */
    private static String render(ReportValue value) {
        return switch (value) {
            // Una raya, no un hueco: en papel, una celda vacia no se distingue de una celda perdida.
            case ReportValue.Empty ignored -> EMPTY_MARK;
            case ReportValue.Text text -> text.value();
            // Seguidos y no en lineas: en el PDF cada salto de linea cuesta alto de pagina.
            case ReportValue.TextList list -> String.join(", ", list.values());
            case ReportValue.Flag flag -> flag.value() ? "Yes" : "No";
            case ReportValue.Count count -> String.valueOf(count.value());
            case ReportValue.Decimal decimal -> decimal.value().setScale(decimal.scale(), RoundingMode.HALF_UP).toPlainString();
            case ReportValue.Ratio ratio -> ratio.value().multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP).toPlainString() + " %";
            case ReportValue.Date date -> DATE.format(date.value());
            case ReportValue.Timestamp timestamp -> TIMESTAMP.format(timestamp.value());
        };
    }

    /** La alineacion sale del tipo del valor: asi ninguna disposicion puede contradecir al dato. */
    private static int alignment(ReportValue value) {
        return switch (value) {
            case ReportValue.Count ignored -> Element.ALIGN_RIGHT;
            case ReportValue.Decimal ignored -> Element.ALIGN_RIGHT;
            case ReportValue.Ratio ignored -> Element.ALIGN_RIGHT;
            case ReportValue.Date ignored -> Element.ALIGN_CENTER;
            case ReportValue.Timestamp ignored -> Element.ALIGN_CENTER;
            case ReportValue.Flag ignored -> Element.ALIGN_CENTER;
            case ReportValue.Empty ignored -> Element.ALIGN_CENTER;
            case ReportValue.Text ignored -> Element.ALIGN_LEFT;
            case ReportValue.TextList ignored -> Element.ALIGN_LEFT;
        };
    }
}
