-- Un turno puede cubrir mas de una via en la misma noche (raro que cubra mas de un EP: en ese caso
-- son dos turnos). La via unica pasa a una coleccion; la existente se conserva como primer elemento.

CREATE TABLE maintenance_shift_track (
    shift_id uuid NOT NULL,
    track_id bigint NOT NULL,
    CONSTRAINT pk_maintenance_shift_track PRIMARY KEY (shift_id, track_id),
    CONSTRAINT fk_maintenance_shift_track_shift FOREIGN KEY (shift_id) REFERENCES maintenance_shift (id) ON DELETE CASCADE
);

CREATE INDEX idx_maintenance_shift_track_track ON maintenance_shift_track (track_id);

INSERT INTO maintenance_shift_track (shift_id, track_id)
SELECT id, track_id FROM maintenance_shift WHERE track_id IS NOT NULL;

DROP INDEX IF EXISTS idx_maintenance_shift_track_date;
ALTER TABLE maintenance_shift DROP COLUMN track_id;

-- Gemelo de Envers de la coleccion, con la misma forma que maintenance_task_task_type_aud.
CREATE TABLE maintenance_shift_track_aud (
    rev integer NOT NULL,
    shift_id uuid NOT NULL,
    track_id bigint NOT NULL,
    revtype smallint,
    CONSTRAINT pk_maintenance_shift_track_aud PRIMARY KEY (rev, shift_id, track_id),
    CONSTRAINT fk_maintenance_shift_track_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);

ALTER TABLE maintenance_shift_aud DROP COLUMN track_id;
