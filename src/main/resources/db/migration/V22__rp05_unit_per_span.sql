-- RP-05 (Contact wire, revision particular) va por vano, como RG-12: la hoja de tareas del plan da sus
-- 3 minutos por vano. V3 lo sembro por defecto (DEFECT), asi que el catalogo decia «3 min por defecto».
--
-- La estimacion no cambia: el estimador cuenta una vez por tarea cualquier unidad que no sea KM, sea
-- SPAN o DEFECT. Solo cambia lo que se lee en el catalogo.

UPDATE maintenance_task_type
   SET unit = 'SPAN', updated_at = now()
 WHERE code = 'RP-05';
