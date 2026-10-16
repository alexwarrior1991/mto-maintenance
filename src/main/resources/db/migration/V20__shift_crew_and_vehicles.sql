-- La dotacion y los vehiculos de un turno (docs/02-domain-model.md, "Shift crew and vehicles").
--
-- Un turno lleva su jefe de equipo, su supervisor, sus linemen (de 2 a 6 para iniciarlo) y sus
-- vehiculos, todos de mto-resources (crew_person, fleet_vehicle de V19). Al cerrar, cada vehiculo dice
-- donde se ha podido dejar: no hay base fija. Las tablas nuevas tienen su gemela _aud: el turno se
-- audita, y quien iba esa noche y en que es historia del turno.

ALTER TABLE maintenance_shift
    ADD COLUMN team_leader_person_id uuid,
    ADD COLUMN supervisor_person_id uuid,
    ADD CONSTRAINT fk_maintenance_shift_team_leader FOREIGN KEY (team_leader_person_id) REFERENCES crew_person (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_maintenance_shift_supervisor FOREIGN KEY (supervisor_person_id) REFERENCES crew_person (id) ON DELETE RESTRICT;

ALTER TABLE maintenance_shift_aud
    ADD COLUMN team_leader_person_id uuid,
    ADD COLUMN supervisor_person_id uuid;

CREATE INDEX idx_maintenance_shift_team_leader ON maintenance_shift (team_leader_person_id) WHERE team_leader_person_id IS NOT NULL;

-- Una entidad hija y no un @ManyToMany: lleva columnas propias (donde quedo aparcado). El sitio es un id
-- de mto-resources y la via uno de mto-configuration, sin clave ajena, como el resto de ids externos.
CREATE TABLE maintenance_shift_vehicle (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    shift_id uuid NOT NULL,
    vehicle_id uuid NOT NULL,
    parking_site_id uuid,
    parking_track_id bigint,
    parking_kp numeric(12, 3),
    parking_description varchar(500),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_maintenance_shift_vehicle UNIQUE (shift_id, vehicle_id),
    CONSTRAINT fk_maintenance_shift_vehicle_shift FOREIGN KEY (shift_id) REFERENCES maintenance_shift (id) ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_shift_vehicle_vehicle FOREIGN KEY (vehicle_id) REFERENCES fleet_vehicle (id) ON DELETE RESTRICT
);

CREATE INDEX idx_maintenance_shift_vehicle_vehicle ON maintenance_shift_vehicle (vehicle_id);

CREATE TABLE maintenance_shift_vehicle_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    shift_id uuid,
    vehicle_id uuid,
    parking_site_id uuid,
    parking_track_id bigint,
    parking_kp numeric(12, 3),
    parking_description varchar(500),
    CONSTRAINT pk_maintenance_shift_vehicle_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_maintenance_shift_vehicle_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);

-- Los linemen de esa noche, que pueden no ser los del equipo (un suplente, uno de baja).
CREATE TABLE maintenance_shift_lineman (
    shift_id uuid NOT NULL,
    person_id uuid NOT NULL,
    CONSTRAINT pk_maintenance_shift_lineman PRIMARY KEY (shift_id, person_id),
    CONSTRAINT fk_maintenance_shift_lineman_shift FOREIGN KEY (shift_id) REFERENCES maintenance_shift (id) ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_shift_lineman_person FOREIGN KEY (person_id) REFERENCES crew_person (id) ON DELETE RESTRICT
);

CREATE INDEX idx_maintenance_shift_lineman_person ON maintenance_shift_lineman (person_id);

CREATE TABLE maintenance_shift_lineman_aud (
    rev integer NOT NULL,
    shift_id uuid NOT NULL,
    person_id uuid NOT NULL,
    revtype smallint,
    CONSTRAINT pk_maintenance_shift_lineman_aud PRIMARY KEY (rev, shift_id, person_id),
    CONSTRAINT fk_maintenance_shift_lineman_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);
