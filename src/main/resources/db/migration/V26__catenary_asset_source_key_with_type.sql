-- La clave de un activo sincronizado lleva su tipo.
--
-- Hasta aqui un activo que viene de mto-configuration se identificaba por (source_service,
-- source_entity_id), con el entityId del evento tal cual. Pero alli cada entidad tiene su secuencia
-- (profile_seq, disconnector_seq, section_insulator_seq) y las tres empiezan en 1: el perfil 5, el
-- seccionador 5 y el aislador 5 existen a la vez. Con la clave sin tipo, el evento del segundo caia en
-- el ON CONFLICT de la fila del primero y escribia sobre ella (su codigo, su nombre, su via, su kp; el
-- type no, que el upsert no lo toca), o iba a la DLQ si un CHECK ligado al tipo lo impedia
-- (chk_catenary_asset_own_track, chk_catenary_asset_drive_type, chk_catenary_asset_disconnector_function),
-- y el DELETED de uno desactivaba al otro.
--
-- Desde aqui la clave es (source_service, type, source_entity_id). source_entity_id sigue siendo el id
-- crudo del origen: profile_source_id (el poste de un seccionador) lo sigue nombrando, y todo lo que
-- cruza por el ya filtra por type = 'PROFILE'. catenary_asset_aud no cambia: no lleva restricciones.

ALTER TABLE catenary_asset DROP CONSTRAINT uq_catenary_asset_source;
ALTER TABLE catenary_asset ADD CONSTRAINT uq_catenary_asset_source UNIQUE (source_service, type, source_entity_id);

-- Lo ya pisado no se puede reconstruir aqui: de las entidades que compartian una fila solo queda lo que
-- escribio la ultima, bajo el tipo de la primera. Se reconoce porque el codigo no es el de su tipo
-- (PRF-<id> un perfil, DSC-<id> un seccionador, SIN-<id> un aislador):
--
--   SELECT id, code, type, source_entity_id, name, enabled
--     FROM catenary_asset
--    WHERE source_service = 'mto-configuration'
--      AND code <> CASE type WHEN 'PROFILE' THEN 'PRF-' WHEN 'DISCONNECTOR' THEN 'DSC-'
--                            WHEN 'SECTION_INSULATOR' THEN 'SIN-' END || source_entity_id;
--
-- (antes de esta migracion; despues, las devuelve el NOTICE de abajo y no la consulta, porque su
-- codigo ya esta corregido). Una fila que se piso y volvio a recibir un evento de su propia entidad
-- tiene su codigo bien y no se distingue: por eso se republica todo, no solo lo que sale aqui.
--
-- Lo que si se hace aqui es dejar el paso libre a la republicacion. La fila se queda con su tipo (es
-- el de la entidad para la que nacio, y el de las ordenes que la apuntan) y recupera el codigo de su
-- tipo: si no, el evento republicado de la otra entidad, que ahora inserta su propia fila, chocaria
-- con uq_catenary_asset_code y acabaria en la DLQ. El resto de la fila (nombre, via, kp, activo en
-- origen) lo corrige el evento republicado de su entidad, y la otra entidad recibe la fila que nunca
-- tuvo. Tras desplegar hay que republicar profile, disconnector y section-insulator desde
-- mto-configuration (docs/06-messaging.md).
DO $$
DECLARE
    overwritten integer;
BEGIN
    UPDATE catenary_asset asset
       SET code = expected.code,
           version = asset.version + 1,
           updated_at = now()
      FROM (SELECT id,
                   CASE type WHEN 'PROFILE' THEN 'PRF-' WHEN 'DISCONNECTOR' THEN 'DSC-' ELSE 'SIN-' END
                       || source_entity_id AS code
              FROM catenary_asset
             WHERE source_service = 'mto-configuration'
               AND type IN ('PROFILE', 'DISCONNECTOR', 'SECTION_INSULATOR')) expected
     WHERE asset.id = expected.id
       AND asset.code <> expected.code
       -- Un activo creado a mano con ese codigo: se deja, y lo dira el evento que choque con el.
       AND NOT EXISTS (SELECT 1 FROM catenary_asset other WHERE other.code = expected.code);
    GET DIAGNOSTICS overwritten = ROW_COUNT;
    IF overwritten > 0 THEN
        RAISE NOTICE 'catenary_asset: % synchronized assets had been overwritten by another entity with the same id; '
            'their code is back to that of their type. Republish profile, disconnector and section-insulator '
            'from mto-configuration.', overwritten;
    END IF;
END $$;

-- Las agujas son de un aislador. Las que un aislador dejo colgadas de la fila de un perfil o de un
-- seccionador con su mismo id no las borraria nadie: el aislador republicado escribe las suyas en su
-- propia fila.
DELETE FROM catenary_asset_switch
 WHERE asset_id IN (SELECT id FROM catenary_asset WHERE type <> 'SECTION_INSULATOR');
