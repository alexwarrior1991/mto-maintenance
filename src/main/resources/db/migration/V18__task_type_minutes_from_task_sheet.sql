-- Los minutos de los tipos de tarea, los de la hoja de tareas del plan (mainteinance_inspection_element,
-- columna «Inspection / Verification (minutes)»).
--
-- V3 sembro tiempos ORIENTATIVOS donde no los tenia, y la hoja si los fija: 19 de los 30 no coincidian.
-- Se toma el numero de la hoja como el tiempo por unidad de cada tipo, con la unidad que ya tenia,
-- porque es la lectura que el catalogo ya hacia de los que coincidian:
--
-- - por unidad (UNIT, SPAN, PROFILE, DEFECT, que el estimador cuenta una vez por tarea): el tiempo del
--   elemento. RG-12 es el unico que la hoja da «por vano», y su unidad ya es SPAN;
-- - por kilometro (KM): por km de via recorrida, como RG-08 y RG-10, que ya eran 3 en los dos sitios.
--   El tiempo fijo no esta en la hoja y se queda como estaba.
--
-- Se quedan como estaban los 11 que ya coincidian (RG-01, RG-05, RG-07, RG-08, RG-10, RG-11, RG-15,
-- RG-16, RP-10, RP-11 y RP-12). El ritmo de «10 km/day» de la vegetacion en RG-02 y RP-01 no es un tiempo
-- por unidad y no se lleva: su tiempo es el de la inspeccion de los aisladores (4).
--
-- Las tareas ya creadas no guardan minutos: los leen del tipo al estimar, asi que su estimacion cambia
-- con esta migracion. El catalogo es de solo lectura por la API y no esta auditado.

UPDATE maintenance_task_type AS t
   SET standard_minutes_per_unit = v.minutes, updated_at = now()
  FROM (VALUES
    ('RG-02', 4),    -- insulators in tunnels (6)
    ('RG-03', 20),   -- section insulators (30)
    ('RG-04', 20),   -- turnouts (45)
    ('RG-06', 15),   -- catenary automatic valves (20)
    ('RG-09', 15),   -- compensation equipment (20)
    ('RG-12', 3),    -- droppers, por vano (5)
    ('RG-13', 2),    -- steel poles (5)
    ('RG-14', 3),    -- traction voltage return, por km (2)
    ('RG-17', 3),    -- feeders, por km (2)
    ('RG-18', 45),   -- rigid catenary, por km (5)
    ('RP-01', 4),    -- tunnel insulators (10)
    ('RP-02', 20),   -- section insulators (45)
    ('RP-03', 20),   -- turnouts (60)
    ('RP-04', 15),   -- compensation equipment (30)
    ('RP-05', 3),    -- contact wire (20)
    ('RP-06', 20),   -- remote catenary starters, el motor (45)
    ('RP-07', 20),   -- catenary isolators, el seccionador (10)
    ('RP-08', 4),    -- earthing (5)
    ('RP-09', 3)     -- static auscultation, por perfil (15)
  ) AS v (code, minutes)
 WHERE t.code = v.code;
