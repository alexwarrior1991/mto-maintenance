package com.alejandro.mtomaintenance.application.dto.export;

import com.alejandro.mtomaintenance.application.exception.ValidationException;

/**
 * Formato en el que se pide un informe: el valor del parametro {@code ?format} de los tres
 * endpoints que los sirven.
 *
 * <p>{@link #JSON} esta en la enumeracion aunque no tenga exportador. Es un valor legitimo del
 * parametro y ademas el valor por defecto, y dejarlo fuera obligaria a tratar "json" como una cadena
 * magica en los tres controladores y a publicar en OpenAPI una lista de valores que no coincide con
 * lo que la API acepta.</p>
 */
public enum ReportFormat {

    JSON,
    XLSX,
    PDF;

    /**
     * Sin parametro se responde JSON, que es lo que hacian estos endpoints antes de existir la
     * exportacion: quien ya los llama no se entera de que esto existe.
     *
     * <p>Un formato desconocido es un error del cliente, no una averia: sale por
     * {@link ValidationException}, que {@code GlobalExceptionHandler} ya traduce a 400 con
     * {@code VAL-001}, y el mensaje enumera lo que si se acepta. Atarlo en cambio al enlace de tipos
     * de Spring —declarar el parametro como {@code ReportFormat}— daria un 400 tambien, pero
     * distinguiendo mayusculas de minusculas: {@code format=xlsx} fallaria y {@code format=XLSX} no.</p>
     */
    public static ReportFormat of(String value) {
        if (value == null || value.isBlank()) {
            return JSON;
        }
        String requested = value.trim();
        for (ReportFormat format : values()) {
            if (format.name().equalsIgnoreCase(requested)) {
                return format;
            }
        }
        throw new ValidationException(
                "Unknown report format '%s'. Supported formats: json (default), xlsx, pdf.".formatted(requested));
    }

    /** El unico reparto que hace el controlador: el DTO de siempre, o un fichero. */
    public boolean isJson() {
        return this == JSON;
    }
}
