-- Los seccionadores de un poste, por el id de su perfil en mto-configuration. Desde su V29 un poste admite
-- varios (antes, uno vivo como mucho), y cada evento de un perfil les pasa su paquete, y su via si no
-- traen la suya, con un UPDATE ... WHERE profile_source_id = ... (propagateLocationToDisconnectors); el
-- seccionador que llega lo lee de su perfil por el mismo camino (inheritLocationOfDisconnector), y borrar
-- una via baja los que cuelgan de sus postes (deactivateByTrack). Sin indice, cada uno recorria la tabla.
--
-- Nada de esto suponia uno por poste: no hay restriccion unica sobre profile_source_id, ni la habra.

CREATE INDEX IF NOT EXISTS idx_catenary_asset_profile_source ON catenary_asset (source_service, profile_source_id);
