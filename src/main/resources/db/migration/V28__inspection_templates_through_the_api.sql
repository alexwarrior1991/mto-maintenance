-- Las plantillas de inspeccion se mantienen por la API (docs/02-domain-model.md, "Inspections and
-- templates"): una version nueva sustituye a la activa de su tipo de activo, y de una version solo
-- cambian en su sitio el nombre y si es la activa. Sus puntos no cambian nunca: una inspeccion guarda
-- su copia y ademas el id de la version de la que la copio.

-- Como mucho una plantilla activa por tipo de activo, y lo decide la base: hasta ahora era una
-- convencion ("la activa de mayor version"). Si alguien dejo varias activas a mano, se queda la de
-- mayor version, que es la que ya se venia copiando; las tres sembradas por V3 no cambian.
UPDATE inspection_template older
   SET active = false, updated_at = now()
 WHERE older.active
   AND EXISTS (SELECT 1 FROM inspection_template newer
                WHERE newer.asset_type = older.asset_type AND newer.active AND newer.version > older.version);

CREATE UNIQUE INDEX uq_inspection_template_active_asset_type ON inspection_template (asset_type) WHERE active;

-- El bloqueo optimista del PATCH. No puede llamarse version como en las demas tablas: aqui version es
-- ya el numero de version de la plantilla, y la API lo publica con ese nombre. inspection_template es
-- un catalogo y no se audita, como maintenance_task_type (V24): su historia son sus versiones.
ALTER TABLE inspection_template
    ADD COLUMN lock_version bigint NOT NULL DEFAULT 0;
