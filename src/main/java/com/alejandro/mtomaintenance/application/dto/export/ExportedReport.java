package com.alejandro.mtomaintenance.application.dto.export;

/**
 * Fichero ya generado, listo para escribir en la respuesta.
 *
 * <h2>Por que el contenido va en memoria</h2>
 *
 * <p>Porque volcarlo al flujo de la respuesta no bajaria el pico, solo lo moveria: POI y OpenPDF
 * construyen el documento entero antes de escribir el primer byte. Lo que si cambiaria es el fallo.
 * Escribir en el flujo confirma la linea de estado, de modo que un error a mitad dejaria un fichero
 * truncado bajo un 200 —el cliente se lleva un .xlsx que Excel no abre y nadie sabe por que—
 * mientras que con el array la excepcion todavia se convierte en el error JSON de siempre. De
 * propina, el tamaño conocido permite un {@code Content-Length} y que la barra de descarga avance.</p>
 *
 * <p>Con los tamaños de hoy sobra: decenas de filas en un parte y unos cientos en un avance, del
 * orden de 20 a 60 KB. <b>A partir de unas 50.000 filas</b> —varios MB por peticion, y varias
 * peticiones a la vez— deja de valer, y entonces hay que pasar a {@code SXSSFWorkbook} y a un
 * {@code StreamingResponseBody}, habiendo paginado antes los informes.</p>
 *
 * <p>El record lleva un {@code byte[]}, asi que su {@code equals} compara la referencia del array y
 * no el contenido. Nada depende de esa igualdad; el dia que dependa hay que escribirla a mano.</p>
 */
public record ExportedReport(String fileName, String contentType, byte[] content) {
}
