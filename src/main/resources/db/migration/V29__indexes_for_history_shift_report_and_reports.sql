-- Indices para lo que crece: el historial (las _aud), el parte de un turno y los informes por fecha.
--
-- No cambia ninguna columna, asi que no hay gemela _aud que tocar: solo se anaden indices, tambien a
-- las propias _aud. Cada uno se comprobo con EXPLAIN (ANALYZE) del SQL que emite Hibernate, antes y
-- despues, sobre una base con 1,2 M de tareas, 2,4 M de filas en maintenance_task_aud y 3,6 M en
-- maintenance_task_task_type_aud (docs/03-database.md, "Indexes for what grows").
--
-- CREATE INDEX a secas, dentro de la transaccion de Flyway: hoy las tablas son pequenas y el bloqueo
-- de escritura dura un instante. Si esta migracion se aplicara alguna vez sobre tablas ya grandes,
-- crear antes cada indice a mano con CREATE INDEX CONCURRENTLY IF NOT EXISTS <el mismo nombre y
-- definicion> (fuera de una transaccion; un indice que quede INVALID se borra y se repite) y dejar que
-- esta migracion pase despues: el IF NOT EXISTS la convierte en un no-op.

-- ---------------------------------------------------------------------------------------------
-- El historial (GET /<recurso>/{id}/revisions)
-- ---------------------------------------------------------------------------------------------
-- Envers reconstruye cada revision con sus colecciones de entonces, y las busca por la columna del
-- padre:
--   ... from maintenance_task_aud where order_id = ? and rev = (select max(rev) ... where rev <= ? and id = ...)
--   ... from maintenance_task_task_type_aud where task_id = ?
--        and rev = (select max(rev) ... where rev <= ? and task_id = ... and task_type_id = ...)
-- Las _aud solo tenian (rev, id) e (id, rev), y las de coleccion solo su clave (rev, padre, hijo), con
-- rev delante: buscar por el padre recorria la tabla entera, una vez por revision de la orden y otra
-- por cada tarea de cada revision.

-- Las tareas de una orden en una revision. La subconsulta ya usa idx_maintenance_task_aud_id_rev.
CREATE INDEX IF NOT EXISTS idx_maintenance_task_aud_order ON maintenance_task_aud (order_id);

-- Los tipos de una tarea en una revision: el padre delante para la busqueda, y (hijo, rev) detras para
-- que el max(rev) de la subconsulta salga del mismo indice sin tocar la tabla.
CREATE INDEX IF NOT EXISTS idx_maintenance_task_task_type_aud_task
    ON maintenance_task_task_type_aud (task_id, task_type_id, rev);

-- Los puntos de una inspeccion en una revision.
CREATE INDEX IF NOT EXISTS idx_maintenance_inspection_item_aud_inspection ON maintenance_inspection_item_aud (inspection_id);

-- Las cinco colecciones de un turno en una revision: vias, seccionadores, linemen, instrumentos y
-- vehiculos.
CREATE INDEX IF NOT EXISTS idx_maintenance_shift_track_aud_shift
    ON maintenance_shift_track_aud (shift_id, track_id, rev);
CREATE INDEX IF NOT EXISTS idx_maintenance_shift_disconnector_aud_shift
    ON maintenance_shift_disconnector_aud (shift_id, disconnector_id, rev);
CREATE INDEX IF NOT EXISTS idx_maintenance_shift_lineman_aud_shift
    ON maintenance_shift_lineman_aud (shift_id, person_id, rev);
CREATE INDEX IF NOT EXISTS idx_maintenance_shift_instrument_aud_shift
    ON maintenance_shift_instrument_aud (shift_id, instrument_id, rev);
CREATE INDEX IF NOT EXISTS idx_maintenance_shift_vehicle_aud_shift ON maintenance_shift_vehicle_aud (shift_id);

-- maintenance_shift_vehicle_aud es una entidad auditada (V20) y nacio sin el (id, rev) que V4 dio a
-- todas las demas: el max(rev) de cada vehiculo, y sus propias revisiones (where id = ? order by rev),
-- recorrian la clave (rev, id) entera.
CREATE INDEX IF NOT EXISTS idx_maintenance_shift_vehicle_aud_id_rev ON maintenance_shift_vehicle_aud (id, rev);

-- ---------------------------------------------------------------------------------------------
-- El parte de un turno (GET /shifts/{id}/report)
-- ---------------------------------------------------------------------------------------------
-- Tres busquedas por columnas sin indice: los defectos encontrados en las tareas del turno, el
-- material de esas tareas y los defectos resueltos en el turno. Parciales: la mayoria de los defectos
-- no salen de una tarea ni se resuelven en un turno, y hay lineas de material sin tarea; un "= ?" o un
-- "in (...)" ya implica IS NOT NULL, asi que el plan generico de una sentencia preparada tambien los usa.
CREATE INDEX IF NOT EXISTS idx_catenary_defect_found_in_task
    ON catenary_defect (found_in_task_id) WHERE found_in_task_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_catenary_defect_resolved_in_shift
    ON catenary_defect (resolved_in_shift_id) WHERE resolved_in_shift_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_maintenance_material_usage_task
    ON maintenance_material_usage (task_id) WHERE task_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- Los informes por fecha (mensual y avance con ventana) y el orden por defecto de dos listas
-- ---------------------------------------------------------------------------------------------
-- Las tareas completadas en una ventana: completed_at solo existe en las completadas
-- (chk_maintenance_task_completed_has_time), y asset_id va incluido para el "distinct asset_id" del
-- informe de avance.
CREATE INDEX IF NOT EXISTS idx_maintenance_task_completed_at
    ON maintenance_task (completed_at) INCLUDE (asset_id) WHERE completed_at IS NOT NULL;

-- Las ordenes completadas en un mes.
CREATE INDEX IF NOT EXISTS idx_maintenance_order_actual_end_date
    ON maintenance_order (actual_end_date) WHERE actual_end_date IS NOT NULL;

-- Las ordenes creadas en un mes, y el orden por defecto de la lista de ordenes (createdAt desc).
CREATE INDEX IF NOT EXISTS idx_maintenance_order_created_at ON maintenance_order (created_at);

-- Los defectos resueltos en un mes.
CREATE INDEX IF NOT EXISTS idx_catenary_defect_resolved_at
    ON catenary_defect (resolved_at) WHERE resolved_at IS NOT NULL;

-- El orden por defecto de la lista de inspecciones (inspectionDate desc): el (result, inspection_date)
-- de V1 no sirve sin filtrar por resultado.
CREATE INDEX IF NOT EXISTS idx_maintenance_inspection_date ON maintenance_inspection (inspection_date);
