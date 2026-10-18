-- Cualificaciones, documentos e instrumentos de mto-resources (docs/02-domain-model.md, "Qualifications
-- and expiries"; docs/06-messaging.md, "Consumed: resources").
--
-- Lo caducado no entra en un turno: una persona con una cualificacion caducada, un vehiculo con un
-- documento caducado o un instrumento con la calibracion caducada a la fecha del turno. Lo que hace
-- falta para decidirlo llega en los eventos de mto-resources y se guarda en las instantaneas de V19,
-- escrito en la misma sentencia nativa que el resto de la fila y con la misma marca de agua. Un evento
-- sin la clave (un mto-resources anterior) deja lo que habia.

-- La lista de person.*.qualifications tal cual: [{code, kind, drivingLicence, expiresOn}]. Todas, tambien
-- las caducadas: quien decide por la fecha es el turno.
ALTER TABLE crew_person
    ADD COLUMN qualifications jsonb NOT NULL DEFAULT '[]';

-- Los documentos de vehicle.*.documents ([{kind, reference, expiresOn}]) y la habilitacion que su modelo
-- exige al conductor (vehicle.*.type.requiredDriverQualification, el codigo del tipo; NULL = no exige).
ALTER TABLE fleet_vehicle
    ADD COLUMN documents jsonb NOT NULL DEFAULT '[]',
    ADD COLUMN required_driver_qualification varchar(32);

-- Los instrumentos de mto-resources (instrument.*): una instantanea como las personas y los vehiculos,
-- escrita solo con SQL nativo y la marca de agua dentro del WHERE, y por eso sin gemela _aud. El tipo es
-- un enumerado de otro servicio y va como texto.
CREATE TABLE maintenance_instrument (
    id uuid PRIMARY KEY,
    code varchar(32) NOT NULL,
    name varchar(120) NOT NULL,
    kind varchar(30) NOT NULL,
    serial_number varchar(60),
    -- NULL: no tiene calibracion, o no caduca.
    calibration_expires_on date,
    active boolean NOT NULL DEFAULT true,
    source_sequence_number bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Los instrumentos que se llevan esa noche: un conjunto que se sustituye entero, como los linemen. El
-- turno se audita, asi que la tabla tiene su gemela; el instrumento se lee como esta ahora.
CREATE TABLE maintenance_shift_instrument (
    shift_id uuid NOT NULL,
    instrument_id uuid NOT NULL,
    CONSTRAINT pk_maintenance_shift_instrument PRIMARY KEY (shift_id, instrument_id),
    CONSTRAINT fk_maintenance_shift_instrument_shift FOREIGN KEY (shift_id) REFERENCES maintenance_shift (id) ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_shift_instrument_instrument FOREIGN KEY (instrument_id) REFERENCES maintenance_instrument (id) ON DELETE RESTRICT
);

CREATE INDEX idx_maintenance_shift_instrument_instrument ON maintenance_shift_instrument (instrument_id);

CREATE TABLE maintenance_shift_instrument_aud (
    rev integer NOT NULL,
    shift_id uuid NOT NULL,
    instrument_id uuid NOT NULL,
    revtype smallint,
    CONSTRAINT pk_maintenance_shift_instrument_aud PRIMARY KEY (rev, shift_id, instrument_id),
    CONSTRAINT fk_maintenance_shift_instrument_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);
