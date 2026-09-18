package com.alejandro.mtomaintenance.application.dto.export;

/**
 * Tipos de contenido de los ficheros exportados.
 *
 * <p>Son constantes de compilacion y no metodos del exportador porque los necesitan tambien las
 * anotaciones {@code @ApiResponse} de los controladores, y una anotacion solo admite constantes.</p>
 */
public final class ReportMediaTypes {

    /** El de un .xlsx. El generico de Office (application/vnd.ms-excel) es el del .xls y abre mal. */
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public static final String PDF = "application/pdf";

    private ReportMediaTypes() {
    }
}
