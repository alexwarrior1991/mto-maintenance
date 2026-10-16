package com.alejandro.mtomaintenance.application.mapper;

import com.alejandro.mtomaintenance.application.dto.crew.CrewPersonSummary;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CrewPerson;

/**
 * Una persona de mto-resources como resumen, para los metodos {@code default} de los mappers, que no
 * pueden llamar a otro mapper. Fuera de un {@code @Mapper} a proposito: si no, MapStruct veria dos
 * formas de mapear {@code CrewPerson} y no compilaria.
 */
final class CrewSummaries {

    private CrewSummaries() {
    }

    static CrewPersonSummary of(CrewPerson person) {
        return new CrewPersonSummary(person.getId(), person.getCode(), person.getFullName(), person.getUsername(), person.getCategory());
    }
}
