package com.alejandro.mtomaintenance.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lo caducado no entra en un turno, y cada vehiculo que se mueve solo sale con su conductor habilitado.
 * Las cualificaciones de las personas, los documentos de los vehiculos y los instrumentos son de
 * {@code mto-resources}; aqui solo se mira su fecha contra la del turno ({@code shiftDate}), que es la
 * noche en que se trabaja y no hoy: un turno de la semana que viene no admite lo que caduca manana.
 *
 * <ul>
 *   <li><b>Siempre</b> ({@link #expiredViolation}): ninguna cualificacion caducada en el jefe, el
 *       supervisor ni los linemen; ningun documento caducado de un vehiculo del turno; ningun instrumento
 *       retirado o con la calibracion caducada. Caducado es {@code expiresOn < shiftDate}: el dia de la
 *       caducidad todavia vale. Una fecha nula no caduca.</li>
 *   <li><b>Para iniciarlo y mientras esta en curso</b> ({@link #driverViolation}): por cada vehiculo
 *       autopropulsado cuyo modelo exige una habilitacion, una persona <em>distinta</em> de la dotacion
 *       (el jefe o un lineman; el supervisor no va en el vehiculo) que la tenga vigente ese dia. Es un
 *       emparejamiento ({@link #assignDrivers}), no un recorrido: quien puede conducir dos vehiculos
 *       conduce uno, y se busca la asignacion que deja a todos con conductor si existe.</li>
 * </ul>
 *
 * <p>Devuelve el motivo en vez de lanzar, como {@link CrewComposition}: quien llama lo convierte en un
 * 409. Sin framework: lo que entra son records de este paquete, no las entidades.</p>
 */
public final class ShiftQualifications {

    private ShiftQualifications() {
    }

    /** Una cualificacion de una persona: el codigo de su tipo ({@code RAIL-DRIVER}) y hasta cuando vale. */
    public record Qualification(String code, LocalDate expiresOn) {
    }

    /** Una persona del turno: {@code id} la distingue al emparejar, {@code name} la nombra en el motivo. */
    public record Person(UUID id, String name, List<Qualification> qualifications) {

        public Person {
            qualifications = qualifications == null ? List.of() : List.copyOf(qualifications);
        }

        public Person(UUID id, String name, Qualification... qualifications) {
            this(id, name, Arrays.asList(qualifications));
        }

        /** Si tiene la cualificacion de ese codigo vigente ese dia. */
        public boolean holds(String code, LocalDate date) {
            return qualifications.stream().anyMatch(qualification -> code.equals(qualification.code())
                    && !expired(qualification.expiresOn(), date));
        }
    }

    /** Un documento de un vehiculo: su clase ({@code ROAD_INSPECTION}), su referencia y hasta cuando vale. */
    public record Document(String kind, String reference, LocalDate expiresOn) {
    }

    /**
     * Un vehiculo del turno: si se mueve solo, la habilitacion que su modelo exige al conductor
     * ({@code null}: ninguna) y sus documentos.
     */
    public record Vehicle(String code, boolean selfPropelled, String requiredDriverQualification, List<Document> documents) {

        public Vehicle {
            requiredDriverQualification = requiredDriverQualification == null || requiredDriverQualification.isBlank()
                    ? null : requiredDriverQualification.trim();
            documents = documents == null ? List.of() : List.copyOf(documents);
        }

        /** Necesita un conductor habilitado: se mueve solo y su modelo lo exige. */
        public boolean needsADriver() {
            return selfPropelled && requiredDriverQualification != null;
        }
    }

    /** Un instrumento del turno: si sigue activo y hasta cuando vale su calibracion ({@code null}: no caduca). */
    public record Instrument(String code, boolean active, LocalDate calibrationExpiresOn) {
    }

    /**
     * Quien conduce cada vehiculo: los vehiculos con conductor, por su codigo y en el orden recibido, y
     * los que se quedan sin el.
     */
    public record DriverAssignment(Map<String, Person> drivers, List<Vehicle> withoutDriver) {

        public DriverAssignment {
            drivers = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(drivers));
            withoutDriver = List.copyOf(withoutDriver);
        }
    }

    /** Caducado a esa fecha: el dia de la caducidad todavia vale, y una fecha nula no caduca. */
    public static boolean expired(LocalDate expiresOn, LocalDate date) {
        return expiresOn != null && date != null && expiresOn.isBefore(date);
    }

    /**
     * Lo que ha caducado (o esta retirado) a la fecha del turno, todo en un motivo para arreglarlo de una
     * vez; vacio si no hay nada.
     */
    public static Optional<String> expiredViolation(String shift, LocalDate shiftDate, List<Person> people, List<Vehicle> vehicles,
                                                    List<Instrument> instruments) {
        List<String> found = new ArrayList<>();
        for (Person person : nullSafe(people)) {
            for (Qualification qualification : person.qualifications()) {
                if (expired(qualification.expiresOn(), shiftDate)) {
                    found.add(person.name() + ": qualification " + qualification.code() + " expired on " + qualification.expiresOn());
                }
            }
        }
        for (Vehicle vehicle : nullSafe(vehicles)) {
            for (Document document : vehicle.documents()) {
                if (expired(document.expiresOn(), shiftDate)) {
                    found.add("vehicle " + vehicle.code() + ": " + document.kind()
                            + (document.reference() == null ? "" : " " + document.reference()) + " expired on " + document.expiresOn());
                }
            }
        }
        for (Instrument instrument : nullSafe(instruments)) {
            if (!instrument.active()) {
                found.add("instrument " + instrument.code() + ": retired in mto-resources");
            } else if (expired(instrument.calibrationExpiresOn(), shiftDate)) {
                found.add("instrument " + instrument.code() + ": calibration expired on " + instrument.calibrationExpiresOn());
            }
        }
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(shift + " on " + shiftDate + " cannot take what has expired: " + String.join("; ", found)
                + ". Renew it in mto-resources or take it out of the shift");
    }

    /**
     * Si a algun vehiculo que lo necesita le falta un conductor habilitado distinto de los demas; vacio si
     * todos lo tienen (o ninguno lo necesita).
     */
    public static Optional<String> driverViolation(String shift, LocalDate shiftDate, List<Person> crew, List<Vehicle> vehicles) {
        DriverAssignment assignment = assignDrivers(shiftDate, crew, vehicles);
        if (assignment.withoutDriver().isEmpty()) {
            return Optional.empty();
        }
        String missing = assignment.withoutDriver().stream()
                .map(vehicle -> vehicle.code() + " (" + vehicle.requiredDriverQualification() + ")")
                .collect(Collectors.joining(", "));
        return Optional.of(shift + " has no qualified driver for " + missing + " on " + shiftDate
                + ": each self-propelled vehicle needs a different person of the crew (the team leader or a lineman) "
                + "holding the qualification its model requires, valid that day");
    }

    /**
     * El emparejamiento de vehiculos y conductores: un camino de aumento por vehiculo (Kuhn), que deja la
     * asignacion maxima. Un recorrido que diera a cada vehiculo el primero que puede conducirlo fallaria
     * donde el unico habilitado para el segundo ya conduce el primero, que otro tambien sabia llevar.
     * Los vehiculos se recorren en el orden recibido y las personas tambien, asi que el resultado es
     * siempre el mismo para la misma entrada.
     */
    public static DriverAssignment assignDrivers(LocalDate date, List<Person> crew, List<Vehicle> vehicles) {
        List<Vehicle> needing = nullSafe(vehicles).stream().filter(Vehicle::needsADriver).toList();
        // Una persona cuenta una vez aunque llegue repetida: conduce un vehiculo, no dos.
        List<Person> people = new ArrayList<>(nullSafe(crew).stream()
                .collect(Collectors.toMap(Person::id, person -> person, (first, second) -> first, LinkedHashMap::new)).values());
        int[] vehicleOf = new int[people.size()];
        Arrays.fill(vehicleOf, -1);
        for (int vehicle = 0; vehicle < needing.size(); vehicle++) {
            augment(vehicle, needing, people, date, vehicleOf, new boolean[people.size()]);
        }

        Map<String, Person> drivers = new LinkedHashMap<>();
        List<Vehicle> withoutDriver = new ArrayList<>();
        for (int vehicle = 0; vehicle < needing.size(); vehicle++) {
            Person driver = null;
            for (int person = 0; person < people.size(); person++) {
                if (vehicleOf[person] == vehicle) {
                    driver = people.get(person);
                }
            }
            if (driver == null) {
                withoutDriver.add(needing.get(vehicle));
            } else {
                drivers.put(needing.get(vehicle).code(), driver);
            }
        }
        return new DriverAssignment(drivers, withoutDriver);
    }

    /** Busca conductor para {@code vehicle}, moviendo a otro vehiculo a quien ya conduce si ese tiene alternativa. */
    private static boolean augment(int vehicle, List<Vehicle> needing, List<Person> people, LocalDate date, int[] vehicleOf, boolean[] visited) {
        String required = needing.get(vehicle).requiredDriverQualification();
        for (int person = 0; person < people.size(); person++) {
            if (visited[person] || !people.get(person).holds(required, date)) {
                continue;
            }
            visited[person] = true;
            if (vehicleOf[person] < 0 || augment(vehicleOf[person], needing, people, date, vehicleOf, visited)) {
                vehicleOf[person] = vehicle;
                return true;
            }
        }
        return false;
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
