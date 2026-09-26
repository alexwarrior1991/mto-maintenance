-- Bloqueo optimista en todo lo que se edita por la API.
--
-- Hasta ahora ganaba el ultimo: dos personas editando la misma orden se pisaban sin enterarse, y un
-- PUT de un activo pisaba lo que un evento de datos maestros hubiera escrito en medio (Hibernate
-- escribe la fila entera). Con version:
--   - Hibernate la sube en cada escritura y rechaza la que llega con una version vieja (409 CON-001);
--   - la API la devuelve en cada respuesta y la acepta, opcional, en cada PUT y PATCH: si viene y no
--     es la de la fila, 409 CON-001 sin escribir nada; si no viene, todo sigue como hasta ahora;
--   - el SQL nativo de datos maestros (upsert, desactivaciones, via y paquete heredados) la sube a
--     mano, porque Hibernate no lo ve.
--
-- Las gemelas _aud no la llevan: la version de una fila no es historia, y Envers no la audita.

ALTER TABLE catenary_asset              ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE maintenance_order           ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE maintenance_task            ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE maintenance_task_check_item ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE maintenance_shift           ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE maintenance_inspection      ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE maintenance_inspection_item ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE catenary_defect             ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE maintenance_material_usage  ADD COLUMN version bigint NOT NULL DEFAULT 0;
