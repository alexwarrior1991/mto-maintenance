-- Las inspecciones de un turno, para su parte.
--
-- El parte del turno (GET /shifts/{id}/report) lista ahora las inspecciones registradas en el, y las
-- busca por shift_id en cada informe. La columna existe desde V1 pero sin indice: hasta ahora nadie
-- buscaba por ella. Las tareas ya tienen el suyo (idx_maintenance_task_shift).
--
-- No cambia ninguna columna, asi que maintenance_inspection_aud no se toca.

CREATE INDEX idx_maintenance_inspection_shift ON maintenance_inspection (shift_id);
