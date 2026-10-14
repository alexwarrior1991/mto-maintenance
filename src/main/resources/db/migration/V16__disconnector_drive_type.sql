-- Como se acciona un seccionador: con motor o a mano.
--
-- mto-configuration lo publica en el evento del seccionador (driveType: MOTOR, MANUAL o null sin
-- dato) y hasta ahora no se guardaba. Hace falta para generar las tareas de un preventivo: el unico
-- tipo del plan para seccionadores es RP-06 (Motor disconnectors), y sin saber cuales tienen motor se
-- pondria tambien a los manuales. Uno MANUAL o sin dato no lleva tipo por defecto.
--
-- Lo escribe solo el upsert de datos maestros. Lo que ya hay queda a null, que es «sin dato»: el
-- siguiente evento de cada seccionador lo pone (un republicado de mto-configuration).
--
-- Va tambien a catenary_asset_aud, anulable y sin restricciones, como toda columna de una tabla
-- auditada. La escribe solo SQL nativo, asi que solo aparece en la revision de un cambio hecho por la
-- API, con el valor que tenga.

CREATE TYPE disconnector_drive_type AS ENUM ('MOTOR', 'MANUAL');

ALTER TABLE catenary_asset     ADD COLUMN drive_type disconnector_drive_type;
ALTER TABLE catenary_asset_aud ADD COLUMN drive_type disconnector_drive_type;

-- Solo un seccionador se acciona.
ALTER TABLE catenary_asset ADD CONSTRAINT chk_catenary_asset_drive_type
    CHECK (drive_type IS NULL OR type = 'DISCONNECTOR');
