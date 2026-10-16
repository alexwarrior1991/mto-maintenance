-- Personas, vehiculos y equipos de mto-resources (docs/06-messaging.md, "Consumed: resources").
--
-- mto-resources es el dueno del personal, el organigrama y la flota; aqui solo se guarda la
-- instantanea que un turno necesita, escrita con SQL nativo y con la marca de agua dentro del WHERE
-- (source_sequence_number = la cabecera sequenceNumber de su outbox), como catenary_asset. Por eso
-- no se auditan: Envers no veria esas escrituras. Sus enumerados (categoria, movilidad, estado) son
-- de otro servicio y van como varchar: un valor nuevo alli no rompe nada aqui.

CREATE TABLE crew_person (
    id uuid PRIMARY KEY,
    code varchar(16) NOT NULL,
    full_name varchar(260) NOT NULL,
    username varchar(100),
    category varchar(40) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    -- NULL: llego dentro del evento de un equipo, todavia sin su propio evento.
    source_sequence_number bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_crew_person_username ON crew_person (lower(username)) WHERE username IS NOT NULL;

CREATE TABLE fleet_vehicle (
    id uuid PRIMARY KEY,
    code varchar(32) NOT NULL,
    name varchar(150),
    type_code varchar(32),
    type_name varchar(150),
    mobility varchar(20) NOT NULL,
    category varchar(40) NOT NULL,
    self_propelled boolean NOT NULL DEFAULT true,
    portable boolean NOT NULL DEFAULT false,
    towing boolean NOT NULL DEFAULT false,
    seats integer,
    operational_status varchar(30) NOT NULL,
    available boolean NOT NULL,
    active boolean NOT NULL DEFAULT true,
    source_sequence_number bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Un equipo sincronizado es de mto-resources: lo que aqui se edita de el es nada (409 TEA-001). Los
-- locales de antes siguen editables mientras no se den de alta alli con su codigo, que es como se
-- adoptan. Un equipo sincronizado no tiene base: el vehiculo se aparca donde se puede.
ALTER TABLE maintenance_team
    ADD COLUMN source_service varchar(100),
    ADD COLUMN source_entity_id uuid,
    ADD COLUMN source_sequence_number bigint,
    ADD COLUMN leader_person_id uuid,
    ADD COLUMN supervisor_person_id uuid,
    ADD COLUMN default_vehicle_id uuid,
    ADD CONSTRAINT fk_maintenance_team_leader FOREIGN KEY (leader_person_id) REFERENCES crew_person (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_maintenance_team_supervisor FOREIGN KEY (supervisor_person_id) REFERENCES crew_person (id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_maintenance_team_source CHECK ((source_service IS NULL) = (source_entity_id IS NULL));

CREATE UNIQUE INDEX uq_maintenance_team_source_entity ON maintenance_team (source_entity_id) WHERE source_entity_id IS NOT NULL;

-- Los linemen de un equipo sincronizado, tal como los cuenta mto-resources (su jefe va aparte).
CREATE TABLE maintenance_team_member (
    team_id uuid NOT NULL,
    person_id uuid NOT NULL,
    CONSTRAINT pk_maintenance_team_member PRIMARY KEY (team_id, person_id),
    CONSTRAINT fk_maintenance_team_member_team FOREIGN KEY (team_id) REFERENCES maintenance_team (id) ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_team_member_person FOREIGN KEY (person_id) REFERENCES crew_person (id) ON DELETE RESTRICT
);

CREATE INDEX idx_maintenance_team_member_person ON maintenance_team_member (person_id);
