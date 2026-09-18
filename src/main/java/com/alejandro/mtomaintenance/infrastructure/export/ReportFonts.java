package com.alejandro.mtomaintenance.infrastructure.export;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;

import java.awt.Color;
import java.io.IOException;

/**
 * Las fuentes del PDF.
 *
 * <p>Helvetica con la codificacion WinAnsi (Cp1252) y <b>sin incrustar</b>: es una de las catorce
 * fuentes que todo lector de PDF trae, y Cp1252 es un superconjunto de Latin-1, asi que los acentos
 * y la ñ salen sin llevar ningun .ttf dentro del jar ni engordar cada fichero generado.</p>
 *
 * <p>Lo que no cubre Cp1252 —un texto libre pegado con un caracter cirilico o hebreo en las
 * observaciones de un turno— se pierde en silencio al imprimir. Se asume: el contenido de estos
 * informes es el catalogo del plan OCS y notas de campo, y la alternativa es incrustar una fuente
 * completa con {@code BaseFont.IDENTITY_H} en todos los ficheros por un caso que todavia no se ha
 * dado. El xlsx, que es Unicode, lo conserva de todos modos.</p>
 */
final class ReportFonts {

    private static final BaseFont HELVETICA = load(BaseFont.HELVETICA);
    private static final BaseFont HELVETICA_BOLD = load(BaseFont.HELVETICA_BOLD);

    static final Font TITLE = new Font(HELVETICA_BOLD, 13f);
    static final Font SUBTITLE = new Font(HELVETICA, 9f, Font.NORMAL, Color.DARK_GRAY);
    static final Font LABEL = new Font(HELVETICA_BOLD, 7.5f);
    static final Font VALUE = new Font(HELVETICA, 7.5f);
    static final Font TABLE_HEADER = new Font(HELVETICA_BOLD, 7f);
    static final Font CELL = new Font(HELVETICA, 7f);
    static final Font NOTE = new Font(HELVETICA, 8f, Font.ITALIC, Color.DARK_GRAY);

    static final BaseFont FOOTER_BASE = HELVETICA;
    static final float FOOTER_SIZE = 7.5f;

    private ReportFonts() {
    }

    private static BaseFont load(String name) {
        try {
            return BaseFont.createFont(name, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (IOException exception) {
            // Las catorce fuentes estandar las resuelve OpenPDF por nombre, sin tocar el disco: si
            // esto falla, el jar de OpenPDF esta roto y no hay informe que valga.
            throw new IllegalStateException("The standard PDF font " + name + " could not be loaded", exception);
        }
    }
}
