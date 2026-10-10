package com.alejandro.mtomaintenance.application.service.impl;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Piezas comunes a las tres disposiciones de informe.
 *
 * <h2>Por que las etiquetas estan en ingles y no se traducen</h2>
 *
 * <p>Porque lo que el informe <b>imprime</b> ya esta en ingles: el catalogo de tipos de trabajo
 * sembrado en {@code V3} ("Insulators in tunnels"), los nombres de equipo ("Team A - Rishpon"), los
 * vehiculos y los valores de los enums que caen en las celdas ({@code COMPLETED}, {@code PARTIAL}).
 * Una cabecera en castellano encima de ese contenido seria exactamente la mezcla que hay que evitar.
 * Lo demas que lee una persona en este repositorio —mensajes de error, resumenes de OpenAPI,
 * {@code docs/}, el recorrido de {@code http/}— tambien esta en ingles; el castellano vive en los
 * comentarios del codigo, que no se descarga nadie.</p>
 *
 * <p>Por eso tampoco hay {@code MessageSource}: un paquete de recursos con un solo idioma es
 * maquinaria sin segundo caso que la justifique. Las etiquetas son constantes de las tres clases de
 * disposicion, que es el sitio unico al que habria que ir el dia que haya un segundo idioma de
 * verdad.</p>
 */
final class ReportLayouts {

    /** Lo que sobrevive en un nombre de fichero en Windows, en una cabecera HTTP y en una terminal. */
    private static final Pattern UNSAFE_IN_FILE_NAME = Pattern.compile("[^A-Za-z0-9._]+");

    private static final Pattern REPEATED_SEPARATOR = Pattern.compile("-{2,}");
    private static final Pattern COMBINING_MARK = Pattern.compile("\\p{M}+");
    // Escapado y no literal: es dato que acaba en el fichero del cliente, no un comentario, y asi
    // no depende de con que codificacion se lea este .java.
    private static final String SUBTITLE_SEPARATOR = " \u00b7 ";

    private ReportLayouts() {
    }

    /**
     * Nombre del fichero sin extension: {@code <informe>-<fecha>-<ambito>}.
     *
     * <p>La fecha va en segundo lugar y en ISO para que una carpeta con los partes de un mes se
     * ordene sola por nombre. Todo el nombre es ASCII por construccion, que es lo que permite que la
     * cabecera {@code Content-Disposition} lleve {@code filename=} a secas, sin la forma codificada
     * de la RFC 5987 que algunos clientes entienden peor.</p>
     */
    static String fileBaseName(String report, String... parts) {
        StringBuilder name = new StringBuilder(sanitize(report));
        for (String part : parts) {
            String cleaned = sanitize(part);
            if (!cleaned.isEmpty()) {
                name.append('-').append(cleaned);
            }
        }
        return name.toString();
    }

    /** Alcance del informe para el subtitulo: lo que hay, separado, sin huecos de lo que falta. */
    static String subtitle(String... parts) {
        return Arrays.stream(parts)
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(SUBTITLE_SEPARATOR));
    }

    static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        // Los acentos se descomponen y se tiran: un nombre de fichero con 'ó' viaja mal por una
        // cabecera HTTP y peor por un recurso compartido de Windows.
        String ascii = COMBINING_MARK.matcher(Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)).replaceAll("");
        String separated = UNSAFE_IN_FILE_NAME.matcher(ascii).replaceAll("-");
        String collapsed = REPEATED_SEPARATOR.matcher(separated).replaceAll("-");
        int start = collapsed.startsWith("-") ? 1 : 0;
        int end = collapsed.length() - (collapsed.endsWith("-") && collapsed.length() > start ? 1 : 0);
        return start >= end ? "" : collapsed.substring(start, end);
    }
}
