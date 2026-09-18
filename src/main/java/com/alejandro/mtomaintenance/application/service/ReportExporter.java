package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.export.ExportedReport;
import com.alejandro.mtomaintenance.application.dto.export.ReportDocument;
import com.alejandro.mtomaintenance.application.dto.export.ReportFormat;

/**
 * Escritura de un {@link ReportDocument} en un formato concreto.
 *
 * <p>La interfaz vive aqui y las implementaciones en {@code infrastructure/export}, que es donde
 * estan POI y OpenPDF: el mismo reparto que hace {@code StockClient} con su {@code RestClient}. Ni
 * el controlador ni los servicios importan una sola clase de esas dos librerias.</p>
 *
 * <p>Se registran solas por estar en el contexto, como los manejadores de datos maestros: dar de
 * alta un formato es escribir un {@code @Component} mas y su constante en {@link ReportFormat}, sin
 * tocar el controlador ni ningun {@code switch}.</p>
 *
 * <p>Una implementacion tiene que ser <b>sin estado</b>: hay un solo bean por formato y lo comparten
 * todas las peticiones. En particular los estilos de POI y los eventos de pagina de OpenPDF van
 * atados al libro o al documento que se esta escribiendo, asi que nacen y mueren dentro de
 * {@link #export(ReportDocument)} y nunca son campos.</p>
 */
public interface ReportExporter {

    /** Nunca {@link ReportFormat#JSON}: eso no es un fichero, es el DTO que el controlador ya devuelve. */
    ReportFormat format();

    ExportedReport export(ReportDocument document);
}
