package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.exception.ScopeException;
import com.alejandro.mtomaintenance.configuration.security.CurrentUserService;
import com.alejandro.mtomaintenance.configuration.shifts.ScopeProperties;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CrewPerson;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspection;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CrewPersonRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * El ambito por EP de quien escribe: un jefe de equipo, un supervisor o un jefe de supervisores solo crea,
 * modifica y mueve lo de los EP de sus equipos. Leer no se limita. Se calcula con lo que ya hay aqui, sin
 * llamar a nadie: la ficha de la persona del token ({@code crew_person}, por su usuario) y los equipos
 * sincronizados de mto-resources que dirige o en los que va ({@code V24}: tambien como jefe de
 * supervisores).
 *
 * <ul>
 *   <li><b>Sin limite</b>: quien no tiene ficha con ese usuario (los responsables de siempre, las cuentas
 *       de servicio como {@code mto-field-svc}, los consumidores de mensajes, que no tienen token), los jefes
 *       de mantenimiento, de construccion y de proyecto, y todos con {@code app.scope.enabled=false}.</li>
 *   <li><b>Con limite</b>: cualquier otra categoria, a los EP de sus equipos activos. Sin equipos, ninguno:
 *       esa persona no escribe nada hasta que mto-resources la ponga en uno.</li>
 * </ul>
 *
 * <p>Algo sin EP solo lo escribe quien no tiene limite, salvo un turno de un equipo de la persona. Fuera de
 * ambito es 403 {@code SCP-403} ({@link ScopeException}).</p>
 */
@Component
@RequiredArgsConstructor
class MaintenanceScope {

    /** Las categorias de mto-resources que no tienen limite: dirigen todo el mantenimiento, o el proyecto. */
    static final Set<String> UNRESTRICTED_CATEGORIES = Set.of("CHIEF_MAINTENANCE", "CHIEF_CONSTRUCTION", "PROJECT_MANAGER");

    private final CurrentUserService currentUser;
    private final CrewPersonRepository people;
    private final MaintenanceTeamRepository teams;
    private final ScopeProperties properties;

    /** El ambito de una persona: sin limite, o sus EP y sus equipos. */
    record Scope(String who, boolean unrestricted, Set<Long> executionPackageIds, Set<UUID> teamIds) {

        static final Scope NONE = new Scope(null, true, Set.of(), Set.of());

        boolean covers(Long executionPackageId) {
            return unrestricted || executionPackageId != null && executionPackageIds.contains(executionPackageId);
        }
    }

    /** El de quien hace la peticion ahora. */
    Scope current() {
        if (!properties.isEnabled()) {
            return Scope.NONE;
        }
        Optional<CrewPerson> person = currentUser.getUsername().flatMap(people::findByUsername);
        if (person.isEmpty() || UNRESTRICTED_CATEGORIES.contains(person.get().getCategory())) {
            return Scope.NONE;
        }
        UUID id = person.get().getId();
        return new Scope(person.get().getFullName() + " (" + person.get().getCode() + ")", false,
                new TreeSet<>(teams.findExecutionPackageIdsOfPerson(id)), Set.copyOf(teams.findTeamIdsOfPerson(id)));
    }

    /** Escribir en algo de ese EP ({@code what} lo nombra en el motivo). */
    void requirePackage(Long executionPackageId, String what) {
        Scope scope = current();
        if (!scope.covers(executionPackageId)) {
            throw outside(scope, what, executionPackageId);
        }
    }

    void requireOrder(MaintenanceOrder order) {
        requirePackage(order.getExecutionPackageId(), order.getCode());
    }

    void requireAsset(CatenaryAsset asset) {
        requirePackage(asset.getExecutionPackageId(), asset.getCode());
    }

    void requireInspection(MaintenanceInspection inspection) {
        requirePackage(inspection.getExecutionPackageId(), inspection.getCode());
    }

    void requireDefect(CatenaryDefect defect) {
        requirePackage(defect.getExecutionPackageId(), defect.getCode());
    }

    /** Un turno con EP, por su EP; uno sin el, si es de un equipo de la persona. */
    void requireShift(MaintenanceShift shift) {
        Scope scope = current();
        if (scope.covers(shift.getExecutionPackageId())) {
            return;
        }
        if (shift.getExecutionPackageId() == null && shift.getTeam() != null && scope.teamIds().contains(shift.getTeam().getId())) {
            return;
        }
        throw outside(scope, shift.getCode() == null ? "a new shift" : shift.getCode(), shift.getExecutionPackageId());
    }

    private static ScopeException outside(Scope scope, String what, Long executionPackageId) {
        String yours = scope.executionPackageIds().isEmpty() ? "none: you are in no team of mto-resources"
                : scope.executionPackageIds().stream().map(Objects::toString).collect(Collectors.joining(", "));
        String where = executionPackageId == null ? "has no execution package" : "is in execution package " + executionPackageId;
        return new ScopeException(what + " " + where + ", outside the scope of " + scope.who() + " (" + yours + ")");
    }
}
