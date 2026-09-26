-- Dos voces para el estado de un activo: lo que dice mto-configuration y lo que decide mantenimiento.
--
-- Hasta ahora habia una sola columna, enabled, y la escribian los dos:
--   - el upsert de datos maestros hacia enabled = lo que traia el evento. Los perfiles llegan siempre
--     con true (el manejador lo fija) y los seccionadores tambien (mto-configuration no publica su
--     enabled), asi que un republicado (todo UPDATED) reactivaba lo que se hubiera desactivado aqui;
--   - el borrado de una via desactivaba todo lo que habia sobre ella sin mirar el origen, tramos
--     propios incluidos, y sin avanzar la marca de agua: el siguiente evento de un perfil lo reactivaba.
--
-- Ahora:
--   enabled_at_source  lo ultimo que dijo mto-configuration; null en los activos propios (tramos);
--   disabled_locally   la decision de mantenimiento (DELETE, o PUT con enabled=false);
--   enabled            el valor efectivo: coalesce(enabled_at_source, true) and not disabled_locally.
-- enabled sigue siendo lo que leen todas las consultas y el indice (type, enabled), que no cambian.
-- El CHECK impide escribir enabled sin las otras dos.
--
-- Lo que ya habia: en los sincronizados, enabled_at_source = enabled y disabled_locally = false. Lo
-- desactivado aqui antes de esta migracion no se distingue de lo desactivado en el origen, asi que
-- queda como del origen y el siguiente evento lo decide. En los propios, disabled_locally = not enabled.
--
-- Las dos columnas van tambien a catenary_asset_aud, anulables y sin restricciones: desactivar y
-- reactivar por la API son escrituras de Hibernate y dejan revision.

ALTER TABLE catenary_asset     ADD COLUMN enabled_at_source boolean;
ALTER TABLE catenary_asset     ADD COLUMN disabled_locally boolean NOT NULL DEFAULT false;
ALTER TABLE catenary_asset_aud ADD COLUMN enabled_at_source boolean;
ALTER TABLE catenary_asset_aud ADD COLUMN disabled_locally boolean;

UPDATE catenary_asset SET enabled_at_source = enabled WHERE source_service IS NOT NULL;
UPDATE catenary_asset SET disabled_locally = NOT enabled WHERE source_service IS NULL;

ALTER TABLE catenary_asset ADD CONSTRAINT chk_catenary_asset_enabled
    CHECK (enabled = (COALESCE(enabled_at_source, true) AND NOT disabled_locally));
