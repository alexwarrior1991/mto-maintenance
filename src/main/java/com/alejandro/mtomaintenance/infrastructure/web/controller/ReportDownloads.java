package com.alejandro.mtomaintenance.infrastructure.web.controller;

import com.alejandro.mtomaintenance.application.dto.export.ExportedReport;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Respuesta de descarga de un informe ya exportado. Es lo unico que los controladores saben de la
 * exportacion aparte del formato pedido: ni que formatos hay, ni con que se escriben.
 *
 * <p>El nombre lo compone la capa de aplicacion y es ASCII por construccion —letras, digitos, punto,
 * guion y guion bajo—, que es lo que permite mandar {@code filename=} a secas: la forma codificada
 * {@code filename*=UTF-8''...} de la RFC 5987 solo hace falta fuera de ASCII y hay clientes que la
 * entienden peor. La cabecera se construye con {@link ContentDisposition} y no concatenando, porque
 * una comilla o un salto de linea en el nombre serian una inyeccion de cabecera.</p>
 *
 * <p>Fijar el {@code Content-Type} concreto corta la negociacion de contenido: un cliente que mande
 * {@code Accept: application/json} con {@code ?format=pdf} recibe el PDF, que es lo que ha pedido.
 * Por eso tampoco se declara {@code produces} en el mapeo: eso si filtraria por {@code Accept}, y un
 * {@code Accept} que no encajara daria un 406 antes de llegar al metodo.</p>
 */
final class ReportDownloads {

    /** Lo que OpenAPI cuenta del parametro, en un solo sitio para los tres endpoints. */
    static final String FORMAT = "json (the default), xlsx or pdf. The file is served as an attachment "
            + "with a stable, sortable name.";

    private ReportDownloads() {
    }

    static ResponseEntity<byte[]> of(ExportedReport report) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(report.contentType()))
                .contentLength(report.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(report.fileName()).build().toString())
                .body(report.content());
    }
}
