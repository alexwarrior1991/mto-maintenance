-- Los seccionadores abiertos para bloquear la zona de trabajo pasan de dos columnas fijas
-- (block_a / block_b) a un conjunto: para que un corte sea seguro se abren todos los que aislan la
-- zona, y con varias vias por turno pueden ser bastantes mas de dos. Los dos existentes se
-- conservan como primeros elementos del conjunto.

CREATE TABLE maintenance_shift_disconnector (
    shift_id uuid NOT NULL,
    disconnector_id uuid NOT NULL,
    CONSTRAINT pk_maintenance_shift_disconnector PRIMARY KEY (shift_id, disconnector_id),
    CONSTRAINT fk_maintenance_shift_disconnector_shift FOREIGN KEY (shift_id) REFERENCES maintenance_shift (id) ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_shift_disconnector_asset FOREIGN KEY (disconnector_id) REFERENCES catenary_asset (id) ON DELETE RESTRICT
);

CREATE INDEX idx_maintenance_shift_disconnector_asset ON maintenance_shift_disconnector (disconnector_id);

INSERT INTO maintenance_shift_disconnector (shift_id, disconnector_id)
SELECT id, block_a_disconnector_id FROM maintenance_shift WHERE block_a_disconnector_id IS NOT NULL
UNION
SELECT id, block_b_disconnector_id FROM maintenance_shift WHERE block_b_disconnector_id IS NOT NULL;

ALTER TABLE maintenance_shift
    DROP CONSTRAINT fk_maintenance_shift_block_a,
    DROP CONSTRAINT fk_maintenance_shift_block_b,
    DROP COLUMN block_a_disconnector_id,
    DROP COLUMN block_b_disconnector_id;

-- Gemelo de Envers de la relacion, con la misma forma que maintenance_shift_track_aud.
CREATE TABLE maintenance_shift_disconnector_aud (
    rev integer NOT NULL,
    shift_id uuid NOT NULL,
    disconnector_id uuid NOT NULL,
    revtype smallint,
    CONSTRAINT pk_maintenance_shift_disconnector_aud PRIMARY KEY (rev, shift_id, disconnector_id),
    CONSTRAINT fk_maintenance_shift_disconnector_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);

ALTER TABLE maintenance_shift_aud
    DROP COLUMN block_a_disconnector_id,
    DROP COLUMN block_b_disconnector_id;
