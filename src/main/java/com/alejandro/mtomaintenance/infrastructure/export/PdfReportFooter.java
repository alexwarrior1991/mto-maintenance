package com.alejandro.mtomaintenance.infrastructure.export;

import com.lowagie.text.Document;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Pie de pagina del informe: la fecha de generacion a la izquierda y "Page N of M" a la derecha.
 *
 * <p>El total de paginas no se sabe hasta el final, asi que en cada pagina se deja un
 * {@link PdfTemplate} —un hueco— y se rellena al cerrar el documento, cuando ya se sabe. Es el modo
 * habitual de hacerlo con esta libreria.</p>
 *
 * <p>El total se cuenta aqui, en {@code onEndPage}, en lugar de leer {@code writer.getPageNumber()}
 * al cerrar: al cerrar, ese contador ya apunta a la pagina siguiente, y la correccion de uno que
 * suele escribirse depende de la version. Contando lo que de verdad se ha pintado no hay nada que
 * corregir.</p>
 *
 * <h2>Logo y pie corporativo</h2>
 *
 * <p>No se reserva hueco para ninguno de los dos, a proposito: una banda en blanco es espacio muerto
 * en todas las copias que se impriman hasta que exista un logo, y no hay ninguno. Cuando lo haya, el
 * cambio es en dos sitios de este paquete y en ningun otro: la imagen se pinta en {@code onEndPage}
 * —mismo sitio que este pie, pero contra {@code document.top()}— y el margen superior del
 * {@code Document} que crea {@code PdfReportExporter} sube lo que ocupe.</p>
 */
class PdfReportFooter extends PdfPageEventHelper {

    /** Ancho del hueco del total: caben cuatro digitos de sobra a este cuerpo. */
    private static final float TOTAL_WIDTH = 24f;
    private static final float TOTAL_HEIGHT = 10f;
    private static final float BASELINE_BELOW_TEXT_AREA = 16f;

    private final String generatedAt;

    private PdfTemplate total;
    private int pages;

    PdfReportFooter(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    @Override
    public void onOpenDocument(PdfWriter writer, Document document) {
        total = writer.getDirectContent().createTemplate(TOTAL_WIDTH, TOTAL_HEIGHT);
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        pages = writer.getPageNumber();

        // Solo se pinta contra el contenido directo del writer. Un document.add() desde un evento de
        // pagina reentra en la composicion y corrompe la salida.
        PdfContentByte canvas = writer.getDirectContent();
        float baseline = document.bottom() - BASELINE_BELOW_TEXT_AREA;

        canvas.saveState();
        canvas.beginText();
        canvas.setFontAndSize(ReportFonts.FOOTER_BASE, ReportFonts.FOOTER_SIZE);
        canvas.setTextMatrix(document.left(), baseline);
        canvas.showText("Generated " + generatedAt);
        canvas.endText();

        String page = "Page " + pages + " of ";
        float pageWidth = ReportFonts.FOOTER_BASE.getWidthPoint(page, ReportFonts.FOOTER_SIZE);

        canvas.beginText();
        canvas.setFontAndSize(ReportFonts.FOOTER_BASE, ReportFonts.FOOTER_SIZE);
        canvas.setTextMatrix(document.right() - pageWidth - TOTAL_WIDTH, baseline);
        canvas.showText(page);
        canvas.endText();
        canvas.addTemplate(total, document.right() - TOTAL_WIDTH, baseline);
        canvas.restoreState();
    }

    @Override
    public void onCloseDocument(PdfWriter writer, Document document) {
        total.beginText();
        total.setFontAndSize(ReportFonts.FOOTER_BASE, ReportFonts.FOOTER_SIZE);
        total.setTextMatrix(0, 0);
        total.showText(String.valueOf(pages));
        total.endText();
    }
}
