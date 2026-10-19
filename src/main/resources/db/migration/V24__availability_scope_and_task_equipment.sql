-- Fase 5 de mto-resources (docs/02-domain-model.md): la disponibilidad de personas y vehiculos, el ambito
-- por EP y el equipo que exige un tipo de tarea.

-- Cuando no esta disponible cada persona y cada vehiculo, tal como lo publica mto-resources en
-- person.*.unavailable y vehicle.*.unavailable: [{from, to}], con las fechas incluidas y to nulo si no se
-- sabe cuando acaba (una reparacion abierta). Sin la clase de ausencia: una baja medica no sale de
-- mto-resources. Se escribe en la misma sentencia nativa que el resto de la fila y con la misma marca de
-- agua; un evento sin la clave (un mto-resources anterior) deja lo que habia.
ALTER TABLE crew_person
    ADD COLUMN unavailable jsonb NOT NULL DEFAULT '[]';

-- Las capacidades del modelo del vehiculo (vehicle.*.type.capabilities, nombres de mto-resources como
-- INSULATED_PLATFORM o EARTHING), que hasta ahora solo se leian para saber si remolca.
ALTER TABLE fleet_vehicle
    ADD COLUMN unavailable jsonb NOT NULL DEFAULT '[]',
    ADD COLUMN capabilities jsonb NOT NULL DEFAULT '[]';

-- El jefe de supervisores de un equipo sincronizado (team.*.chiefSupervisor), sin FK como el jefe y el
-- supervisor. Cuenta para el ambito por EP: dirige los equipos de su jefatura. maintenance_team no se
-- audita.
ALTER TABLE maintenance_team
    ADD COLUMN chief_supervisor_person_id uuid;

-- El equipo que exige un tipo de tarea: la tarea solo se asigna, se inicia y se completa en un turno que
-- lo lleva. INSULATED_PLATFORM: un vehiculo con plataforma aislada. EARTHING: un vehiculo con puesta a
-- tierra o una pertiga de tierra entre los instrumentos del turno. No se siembra nada: lo decide quien
-- supervisa, con PATCH /task-types/{code}. maintenance_task_type es un catalogo y no se audita; su
-- version es el bloqueo optimista de ese PATCH.
ALTER TABLE maintenance_task_type
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

CREATE TABLE maintenance_task_type_equipment (
    task_type_id uuid NOT NULL,
    equipment varchar(40) NOT NULL,
    CONSTRAINT pk_maintenance_task_type_equipment PRIMARY KEY (task_type_id, equipment),
    CONSTRAINT fk_maintenance_task_type_equipment_type FOREIGN KEY (task_type_id) REFERENCES maintenance_task_type (id) ON DELETE CASCADE,
    CONSTRAINT chk_maintenance_task_type_equipment CHECK (equipment IN ('INSULATED_PLATFORM', 'EARTHING'))
);
