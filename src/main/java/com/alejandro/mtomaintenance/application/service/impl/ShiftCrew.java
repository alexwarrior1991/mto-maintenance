package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.shift.VehicleParkingRequest;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.exception.ShiftException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.domain.model.CrewComposition;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CrewPerson;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.FleetVehicle;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShiftVehicle;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTeam;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CrewPersonRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.FleetVehicleRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.MaintenanceShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * La dotacion y los vehiculos de un turno, de mto-resources: quien va esa noche y en que, y donde se
 * dejo cada vehiculo. Las reglas dan 409 {@code SHF-001}.
 *
 * <ul>
 *   <li><b>Siempre</b>: como mucho un jefe y 6 linemen, sin que el jefe este entre ellos; personas
 *       activas y un jefe de categoria jefe de equipo o mas; vehiculos activos y disponibles; uno que no
 *       se mueve solo (un vagon, un remolque) entra con otro del turno que remolque.</li>
 *   <li><b>Para iniciarlo y mientras esta en curso</b> ({@link CrewComposition}): jefe y de 2 a 6
 *       linemen. Solo a un turno con dotacion ({@link MaintenanceShift#hasCrew()}): los de los equipos
 *       locales de antes, sin ella, siguen como hoy.</li>
 *   <li><b>Plazas</b>: si los vehiculos autopropulsados del turno dicen sus plazas, alcanzan para el
 *       jefe y sus linemen.</li>
 *   <li><b>Doble asignacion</b>: ni un vehiculo ni el jefe ni un lineman van en dos turnos abiertos de la
 *       misma fecha, con un bloqueo asesor por cada uno.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class ShiftCrew {

    private static final Set<ShiftStatus> OPEN = EnumSet.of(ShiftStatus.PLANNED, ShiftStatus.IN_PROGRESS);
    private static final UUID NO_SHIFT = new UUID(0, 0);

    private final CrewPersonRepository personRepository;
    private final FleetVehicleRepository vehicleRepository;
    private final MaintenanceShiftRepository shiftRepository;

    /**
     * Al dar de alta: lo que la peticion dice, y lo que no dice de un equipo sincronizado (su jefe, su
     * supervisor, sus linemen y su vehiculo por defecto, si ya llego). Un equipo local no aporta nada.
     */
    void assignOnCreate(MaintenanceShift shift, Set<UUID> vehicleIds, UUID leaderId, UUID supervisorId, Set<UUID> linemanIds) {
        MaintenanceTeam team = shift.getTeam();
        boolean fromTeam = team != null && team.isSynchronized();
        shift.setTeamLeader(leaderId != null ? person(leaderId) : fromTeam ? team.getLeader() : null);
        shift.setSupervisor(supervisorId != null ? person(supervisorId) : fromTeam ? team.getSupervisor() : null);
        shift.getLinemen().clear();
        if (linemanIds != null) {
            shift.getLinemen().addAll(people(linemanIds));
        } else if (fromTeam) {
            shift.getLinemen().addAll(team.getMembers());
        }
        Set<UUID> vehicles = vehicleIds != null ? vehicleIds
                : fromTeam && team.getDefaultVehicleId() != null && vehicleRepository.existsById(team.getDefaultVehicleId())
                ? Set.of(team.getDefaultVehicleId()) : Set.of();
        replaceVehicles(shift, vehicles);
    }

    void setLeader(MaintenanceShift shift, UUID leaderId) {
        shift.setTeamLeader(leaderId == null ? null : person(leaderId));
    }

    void setSupervisor(MaintenanceShift shift, UUID supervisorId) {
        shift.setSupervisor(supervisorId == null ? null : person(supervisorId));
    }

    void replaceLinemen(MaintenanceShift shift, Set<UUID> linemanIds) {
        shift.getLinemen().clear();
        if (linemanIds != null) {
            shift.getLinemen().addAll(people(linemanIds));
        }
    }

    /** El conjunto entero; los que siguen conservan lo suyo (su aparcamiento). */
    void replaceVehicles(MaintenanceShift shift, Set<UUID> vehicleIds) {
        Set<UUID> wanted = vehicleIds == null ? Set.of() : new LinkedHashSet<>(vehicleIds);
        shift.getVehicles().removeIf(vehicle -> !wanted.contains(vehicle.getVehicle().getId()));
        Set<UUID> present = shift.getVehicles().stream().map(vehicle -> vehicle.getVehicle().getId()).collect(Collectors.toSet());
        for (UUID id : wanted) {
            if (!present.contains(id)) {
                shift.getVehicles().add(MaintenanceShiftVehicle.of(shift, vehicle(id)));
            }
        }
        if (shift.getVehicle() == null && !shift.getVehicles().isEmpty()) {
            // El texto de antes, para el informe y los consumidores que solo lo leen.
            shift.setVehicle(shift.vehiclesByCode().stream().map(vehicle -> vehicle.getVehicle().getCode()).collect(Collectors.joining(", ")));
        }
    }

    /**
     * Las reglas de siempre y, con {@code working} (al iniciarlo o en curso), la dotacion entera. La doble
     * asignacion se comprueba con los bloqueos tomados, en orden, para que dos turnos a la vez no la burlen.
     */
    void check(MaintenanceShift shift, boolean working) {
        String label = "Shift " + shift.getCode();
        CrewPerson leader = shift.getTeamLeader();
        int linemen = shift.getLinemen().size();
        CrewComposition.limitViolation(label, linemen).ifPresent(reason -> {
            throw new ShiftException(reason);
        });
        if (leader != null && shift.getLinemen().stream().anyMatch(lineman -> lineman.getId().equals(leader.getId()))) {
            throw new ShiftException(leader.getFullName() + " leads " + label + ": the team leader is not one of its linemen");
        }
        if (leader != null && !leader.canLeadATeam()) {
            throw new ShiftException(leader.getFullName() + " is a " + leader.getCategory() + ": a shift is led by a TEAM_LEADER or above");
        }
        for (CrewPerson person : crew(shift)) {
            if (!Boolean.TRUE.equals(person.getActive())) {
                throw new ShiftException(person.getFullName() + " is deactivated in mto-resources");
            }
        }
        if (working && shift.hasCrew()) {
            CrewComposition.completeViolation(label, leader != null, linemen).ifPresent(reason -> {
                throw new ShiftException(reason + " (teamLeaderPersonId, linemanPersonIds)");
            });
        }
        checkVehicles(shift, label);
        checkSeats(shift, label, leader != null, linemen);
        requireNoDoubleBooking(shift, label);
    }

    private static void checkVehicles(MaintenanceShift shift, String label) {
        List<FleetVehicle> vehicles = shift.getVehicles().stream().map(MaintenanceShiftVehicle::getVehicle).toList();
        for (FleetVehicle vehicle : vehicles) {
            if (!Boolean.TRUE.equals(vehicle.getActive()) || !Boolean.TRUE.equals(vehicle.getAvailable())) {
                throw new ShiftException("Vehicle " + vehicle.getCode() + " is not available (" + vehicle.getOperationalStatus()
                        + (Boolean.TRUE.equals(vehicle.getActive()) ? "" : ", retired") + "): it cannot work in " + label);
            }
        }
        boolean towing = vehicles.stream().anyMatch(vehicle -> Boolean.TRUE.equals(vehicle.getTowing()));
        for (FleetVehicle vehicle : vehicles) {
            if (!Boolean.TRUE.equals(vehicle.getSelfPropelled()) && !Boolean.TRUE.equals(vehicle.getPortable()) && !towing) {
                throw new ShiftException("Vehicle " + vehicle.getCode() + " does not move by itself: " + label
                        + " needs another vehicle that tows it");
            }
        }
    }

    /** Solo si todos los autopropulsados dicen sus plazas: un dato que falta no se da por cero. */
    private static void checkSeats(MaintenanceShift shift, String label, boolean hasLeader, int linemen) {
        List<FleetVehicle> carriers = shift.getVehicles().stream().map(MaintenanceShiftVehicle::getVehicle)
                .filter(vehicle -> Boolean.TRUE.equals(vehicle.getSelfPropelled())).toList();
        if (carriers.isEmpty() || carriers.stream().anyMatch(vehicle -> vehicle.getSeats() == null)) {
            return;
        }
        int seats = carriers.stream().mapToInt(FleetVehicle::getSeats).sum();
        int crew = CrewComposition.crewSize(hasLeader, linemen);
        if (crew > seats) {
            throw new ShiftException(label + " carries " + crew + " people and its vehicles seat " + seats);
        }
    }

    private void requireNoDoubleBooking(MaintenanceShift shift, String label) {
        if (!shift.isOpen() || shift.getShiftDate() == null) {
            return;
        }
        Set<UUID> vehicleIds = shift.getVehicles().stream().map(vehicle -> vehicle.getVehicle().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> personIds = new LinkedHashSet<>();
        if (shift.getTeamLeader() != null) {
            personIds.add(shift.getTeamLeader().getId());
        }
        shift.getLinemen().forEach(lineman -> personIds.add(lineman.getId()));
        if (vehicleIds.isEmpty() && personIds.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>();
        vehicleIds.forEach(id -> keys.add("vehicle:" + id));
        personIds.forEach(id -> keys.add("person:" + id));
        keys.stream().sorted().forEach(key -> shiftRepository.lockResource(("mto-maintenance:shift-resource:" + key).hashCode()));

        UUID self = shift.getId() == null ? NO_SHIFT : shift.getId();
        if (!vehicleIds.isEmpty()) {
            List<String> taken = shiftRepository.findOpenShiftCodesWithVehicles(vehicleIds, shift.getShiftDate(), OPEN, self);
            if (!taken.isEmpty()) {
                throw new ShiftException("A vehicle of " + label + " is already in shift " + String.join(", ", taken) + " on "
                        + shift.getShiftDate() + ": a vehicle works in one shift a night");
            }
        }
        if (!personIds.isEmpty()) {
            List<String> taken = shiftRepository.findOpenShiftCodesWithCrew(personIds, shift.getShiftDate(), OPEN, self);
            if (!taken.isEmpty()) {
                throw new ShiftException("Someone of the crew of " + label + " is already in shift " + String.join(", ", taken)
                        + " on " + shift.getShiftDate() + ": a person works in one shift a night");
            }
        }
    }

    /**
     * Donde se dejo cada vehiculo al cerrar. Uno que no es del turno es 400; uno de solo via aparcado
     * sin via ni sitio, 409: no se deja una dresina en un aparcamiento de carretera.
     */
    void park(MaintenanceShift shift, List<VehicleParkingRequest> parkings) {
        if (parkings == null || parkings.isEmpty()) {
            return;
        }
        Map<UUID, MaintenanceShiftVehicle> byVehicle = new HashMap<>();
        shift.getVehicles().forEach(vehicle -> byVehicle.put(vehicle.getVehicle().getId(), vehicle));
        for (VehicleParkingRequest parking : parkings) {
            MaintenanceShiftVehicle vehicle = byVehicle.get(parking.vehicleId());
            if (vehicle == null) {
                throw new ValidationException("Vehicle " + parking.vehicleId() + " is not in shift " + shift.getCode());
            }
            String description = parking.description() == null || parking.description().isBlank() ? null : parking.description().trim();
            if (parking.parkingSiteId() == null && parking.trackId() == null && description == null) {
                throw new ValidationException("Say where vehicle " + vehicle.getVehicle().getCode() + " was left: a parking site, a track "
                        + "and kp, or a description");
            }
            if (vehicle.getVehicle().isRailOnly() && parking.parkingSiteId() == null && parking.trackId() == null) {
                throw new ShiftException("Vehicle " + vehicle.getVehicle().getCode() + " runs only on rails: it is left on a track "
                        + "(trackId and kp) or at a parking site with rail access, not just somewhere");
            }
            vehicle.setParkingSiteId(parking.parkingSiteId());
            vehicle.setParkingTrackId(parking.trackId());
            vehicle.setParkingKp(parking.kp());
            vehicle.setParkingDescription(description);
        }
    }

    private CrewPerson person(UUID id) {
        return personRepository.findById(id).orElseThrow(() -> new NotFoundException("Crew person", id));
    }

    private List<CrewPerson> people(Collection<UUID> ids) {
        List<CrewPerson> people = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            people.add(person(id));
        }
        return people;
    }

    private FleetVehicle vehicle(UUID id) {
        return vehicleRepository.findById(id).orElseThrow(() -> new NotFoundException("Fleet vehicle", id));
    }

    private static List<CrewPerson> crew(MaintenanceShift shift) {
        List<CrewPerson> crew = new ArrayList<>(shift.getLinemen());
        if (shift.getTeamLeader() != null) {
            crew.add(shift.getTeamLeader());
        }
        return crew;
    }
}
