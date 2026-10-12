-- La via propia de un seccionador en un poste.
--
-- Hasta ahora un seccionador en un poste estaba siempre en la via de su perfil: el evento no traia
-- via con poste, e inheritLocationOfDisconnector y propagateLocationToDisconnectors la copiaban del
-- perfil. mto-configuration la publica ahora tambien con poste cuando el seccionador maniobra otra
-- via que la de su poste: en un haz de vias (talleres, cocheras) cuelga a veces del poste de una y
-- aisla otra, o une dos que no son la del poste. Esa via es la suya, y el perfil no puede pisarla.
--
-- own_track lo dice: true cuando el ultimo evento aplicado de un seccionador en un poste trajo su
-- via. Lo escribe solo el upsert de datos maestros, y con el las sentencias que copian la situacion
-- del perfil le pasan solo el paquete. Sin poste no se mira: la via es siempre la del evento (V26 de
-- mto-configuration), y ninguna de las dos sentencias toca un seccionador sin poste.
--
-- Lo que ya hay queda a false, que es lo que era: los eventos que ya llegaron no traian via con
-- poste. El siguiente evento de cada seccionador la pone (un republicado de mto-configuration).
--
-- Va tambien a catenary_asset_aud, anulable y sin restricciones, como toda columna de una tabla
-- auditada. La escribe solo SQL nativo, asi que solo aparece en la revision de un cambio hecho por la
-- API, con el valor que tenga.

ALTER TABLE catenary_asset     ADD COLUMN own_track boolean NOT NULL DEFAULT false;
ALTER TABLE catenary_asset_aud ADD COLUMN own_track boolean;

-- Una via propia solo tiene sentido en un seccionador en un poste, y con via.
ALTER TABLE catenary_asset ADD CONSTRAINT chk_catenary_asset_own_track
    CHECK (NOT own_track OR (type = 'DISCONNECTOR' AND profile_source_id IS NOT NULL AND track_id IS NOT NULL));
