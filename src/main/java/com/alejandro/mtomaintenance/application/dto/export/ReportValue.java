package com.alejandro.mtomaintenance.application.dto.export;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Valor de una celda junto con el tipo con el que hay que escribirlo.
 *
 * <p>Es lo que permite que la hoja de calculo lleve fechas que son fechas y numeros que son numeros.
 * Si el documento transportara cadenas ya formateadas, Excel recibiria texto: no se podria ordenar
 * por kp, ni filtrar por fecha, ni sumar una columna, y el fichero valdria para mirarlo y para nada
 * mas —que es justo lo que se quiere dejar atras.</p>
 *
 * <p>Es sellada a proposito. Los dos exportadores la recorren con un {@code switch} exhaustivo, asi
 * que añadir un tipo de valor rompe la compilacion en los dos sitios que tienen que decidir como se
 * pinta. Con una jerarquia abierta —o con un {@code Object} y un enum al lado— el tipo nuevo se
 * colaria por el {@code default} como {@code toString()} y nadie se enteraria hasta abrir el
 * fichero.</p>
 *
 * <p>No lleva alineacion ni ancho: los dos se deducen del tipo del valor (un numero a la derecha, un
 * texto a la izquierda) y del contenido de la columna. Ponerlos aqui obligaria a cada disposicion a
 * repetir una decision que ya esta tomada, y permitiria que se contradijeran.</p>
 */
public sealed interface ReportValue {

    /** Ausencia de dato: un turno sin cerrar, un kp que no se midio. */
    record Empty() implements ReportValue {
    }

    record Text(String value) implements ReportValue {
    }

    /**
     * Varios textos que van juntos en una celda: los tipos de trabajo de una tarea, sus materiales,
     * sus fotos. Se guardan sueltos y no unidos con un separador para que cada exportador elija el
     * suyo: en la hoja caben en lineas dentro de la misma celda, que se leen de un vistazo, y en el
     * PDF tienen que ir seguidos porque cada linea cuesta alto de pagina.
     */
    record TextList(List<String> values) implements ReportValue {

        public TextList {
            values = List.copyOf(values);
        }
    }

    /** Si o no. Va aparte del texto para que en la hoja sea un booleano de verdad y se pueda filtrar. */
    record Flag(boolean value) implements ReportValue {
    }

    /** Entero: minutos, contadores, el numero de fila del parte. */
    record Count(long value) implements ReportValue {
    }

    /**
     * Decimal con la escala con la que se lee: kp y kilometros con tres, medias con una.
     *
     * <p>Una hoja de calculo guarda numeros en coma flotante de doble precision, unos quince digitos
     * significativos. Los kp, {@code numeric(12,3)}, caben exactos; una cantidad de material,
     * {@code numeric(19,6)}, puede no caber. Es una perdida del formato, no del exportador: Excel no
     * sabe representar ese numero como numero, y escribirlo como texto para conservarlo costaria
     * poder sumar la columna.</p>
     */
    record Decimal(BigDecimal value, int scale) implements ReportValue {
    }

    /**
     * Proporcion 0..1. Se guarda como proporcion y se pinta como porcentaje: asi la celda sigue
     * siendo el numero con el que se calculo, y el formato {@code 0.0%} de Excel ya multiplica por
     * cien al mostrarlo. Multiplicarlo tambien aqui daria el clasico 10.000 %.
     */
    record Ratio(BigDecimal value) implements ReportValue {
    }

    record Date(LocalDate value) implements ReportValue {
    }

    /**
     * Fecha y hora ya en la zona del informe. La conversion desde {@code Instant} la hace la capa de
     * aplicacion ({@code ReportLayouts}), no el exportador: en que zona se lee un informe es una
     * decision del informe, tiene que ser la misma con la que se cuentan sus totales, y dejarla en
     * los exportadores permitiria que el xlsx y el PDF del mismo turno no coincidieran.
     */
    record Timestamp(LocalDateTime value) implements ReportValue {
    }

    ReportValue EMPTY = new Empty();

    /** Una cadena en blanco es ausencia de dato, no un texto vacio: en la hoja son celdas distintas. */
    static ReportValue text(String value) {
        return value == null || value.isBlank() ? EMPTY : new Text(value.trim());
    }

    static ReportValue text(Enum<?> value) {
        return value == null ? EMPTY : new Text(value.name());
    }

    static ReportValue textList(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return EMPTY;
        }
        List<String> present = values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
        return present.isEmpty() ? EMPTY : new TextList(present);
    }

    static ReportValue flag(Boolean value) {
        return value == null ? EMPTY : new Flag(value);
    }

    static ReportValue count(Number value) {
        return value == null ? EMPTY : new Count(value.longValue());
    }

    static ReportValue decimal(BigDecimal value, int scale) {
        return value == null ? EMPTY : new Decimal(value, scale);
    }

    static ReportValue ratio(BigDecimal value) {
        return value == null ? EMPTY : new Ratio(value);
    }

    static ReportValue date(LocalDate value) {
        return value == null ? EMPTY : new Date(value);
    }

    static ReportValue timestamp(LocalDateTime value) {
        return value == null ? EMPTY : new Timestamp(value);
    }
}
