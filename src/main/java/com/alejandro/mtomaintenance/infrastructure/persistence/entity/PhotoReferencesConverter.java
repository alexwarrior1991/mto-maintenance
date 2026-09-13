package com.alejandro.mtomaintenance.infrastructure.persistence.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

/**
 * Referencias a fotos (URL o nombre de fichero), una por linea en una columna {@code text}.
 *
 * <p>Una tabla de coleccion pediria su gemela de Envers y una consulta mas por tarea, para un dato
 * que solo se muestra en el informe diario. El almacenamiento de las fotos queda fuera del servicio.</p>
 */
@Converter
public class PhotoReferencesConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return String.join("\n", attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        return Arrays.stream(dbData.split("\n")).filter(line -> !line.isBlank()).map(String::trim).toList();
    }
}
