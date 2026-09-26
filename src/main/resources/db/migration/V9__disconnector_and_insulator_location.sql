-- La via y el paquete de seccionadores y aisladores de seccion, que mto-configuration no publica.
--
-- El evento de un seccionador trae su estacion y su perfil (id, nombre y kp), pero ni la via ni el
-- paquete de ejecucion; el de un aislador trae su via, pero no el paquete. Sus activos quedaban con
-- esos huecos, y con ellos las ordenes, defectos e inspecciones creados sobre ellos, que copian la
-- situacion del activo al nacer. Eso sacaba a los dos tipos del avance y del mensual por paquete
-- (y a los seccionadores del avance por via), de las busquedas por paquete o via y del proyecto de
-- almacen EP-<paquete> de sus ordenes.
--
-- Se derivan de las filas que este servicio ya tiene, sin tocar el contrato:
--   - un seccionador esta en la via y el paquete del perfil del que cuelga
--     (catenary_asset.profile_source_id = source_entity_id del PROFILE);
--   - un aislador esta en el paquete de su via, que es el que traen todos los perfiles de esa via.
-- Desde ahora lo mantienen los manejadores de datos maestros tras cada upsert, en los dos sentidos
-- (llegue antes el perfil o el activo). Esto rellena lo que ya habia.
--
-- Como el upsert, esto es SQL fuera de Hibernate y no deja revision de Envers: las filas _aud
-- siguen mostrando los huecos hasta el siguiente cambio hecho por la API.

-- Seccionadores: via y paquete de su perfil.
UPDATE catenary_asset disconnector
   SET track_id = profile.track_id,
       execution_package_id = profile.execution_package_id,
       updated_at = now()
  FROM catenary_asset profile
 WHERE disconnector.type = 'DISCONNECTOR'
   AND profile.type = 'PROFILE'
   AND profile.source_service = disconnector.source_service
   AND profile.source_entity_id = disconnector.profile_source_id
   AND (disconnector.track_id IS DISTINCT FROM profile.track_id
        OR disconnector.execution_package_id IS DISTINCT FROM profile.execution_package_id);

-- Aisladores: el paquete de los perfiles de su via (el del ultimo perfil que llego, si discreparan).
UPDATE catenary_asset insulator
   SET execution_package_id = located.execution_package_id,
       updated_at = now()
  FROM (SELECT DISTINCT ON (source_service, track_id) source_service, track_id, execution_package_id
          FROM catenary_asset
         WHERE type = 'PROFILE'
           AND track_id IS NOT NULL
           AND execution_package_id IS NOT NULL
         ORDER BY source_service, track_id, updated_at DESC, id) located
 WHERE insulator.type = 'SECTION_INSULATOR'
   AND located.source_service = insulator.source_service
   AND located.track_id = insulator.track_id
   AND insulator.execution_package_id IS DISTINCT FROM located.execution_package_id;

-- Lo creado sobre ellos copio los huecos: se rellenan solo los huecos, nunca se pisa un valor.
UPDATE maintenance_order target
   SET execution_package_id = COALESCE(target.execution_package_id, asset.execution_package_id),
       track_id = COALESCE(target.track_id, asset.track_id)
  FROM catenary_asset asset
 WHERE target.asset_id = asset.id
   AND asset.type IN ('DISCONNECTOR', 'SECTION_INSULATOR')
   AND ((target.execution_package_id IS NULL AND asset.execution_package_id IS NOT NULL)
        OR (target.track_id IS NULL AND asset.track_id IS NOT NULL));

UPDATE catenary_defect target
   SET execution_package_id = COALESCE(target.execution_package_id, asset.execution_package_id),
       track_id = COALESCE(target.track_id, asset.track_id)
  FROM catenary_asset asset
 WHERE target.asset_id = asset.id
   AND asset.type IN ('DISCONNECTOR', 'SECTION_INSULATOR')
   AND ((target.execution_package_id IS NULL AND asset.execution_package_id IS NOT NULL)
        OR (target.track_id IS NULL AND asset.track_id IS NOT NULL));

UPDATE maintenance_inspection target
   SET execution_package_id = COALESCE(target.execution_package_id, asset.execution_package_id),
       track_id = COALESCE(target.track_id, asset.track_id)
  FROM catenary_asset asset
 WHERE target.asset_id = asset.id
   AND asset.type IN ('DISCONNECTOR', 'SECTION_INSULATOR')
   AND ((target.execution_package_id IS NULL AND asset.execution_package_id IS NOT NULL)
        OR (target.track_id IS NULL AND asset.track_id IS NOT NULL));
