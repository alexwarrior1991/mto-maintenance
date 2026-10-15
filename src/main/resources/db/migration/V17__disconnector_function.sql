-- La funcion de un seccionador en el plano de seccionamiento: el codigo de la LOV DisconnectorFunction
-- de mto-configuration (Disc/IO, LoadB/NS, ED, SurgeA...).
--
-- mto-configuration lo publica en el evento del seccionador (disconnectorFunction: {id, code}) y hasta
-- ahora no se guardaba. Hace falta para generar las tareas de un preventivo: una autovalvula (SurgeA)
-- lleva ademas RG-06 (Catenary automatic valves). Se guarda el codigo tal cual llega, no un booleano,
-- porque es el dato del origen y otra funcion puede decidir otro tipo mas adelante. 40 caracteres,
-- como el codigo de la LOV en el origen (V9 de mto-configuration).
--
-- Lo escribe solo el upsert de datos maestros. Lo que ya hay queda a null, que es «sin dato»: el
-- siguiente evento de cada seccionador lo pone (un republicado de mto-configuration).
--
-- Va tambien a catenary_asset_aud, anulable y sin restricciones, como toda columna de una tabla
-- auditada. La escribe solo SQL nativo, asi que solo aparece en la revision de un cambio hecho por la
-- API, con el valor que tenga.

ALTER TABLE catenary_asset     ADD COLUMN disconnector_function varchar(40);
ALTER TABLE catenary_asset_aud ADD COLUMN disconnector_function varchar(40);

-- Solo un seccionador tiene funcion.
ALTER TABLE catenary_asset ADD CONSTRAINT chk_catenary_asset_disconnector_function
    CHECK (disconnector_function IS NULL OR type = 'DISCONNECTOR');

-- RP-07 es «Catenary isolators» en el plan: el seccionador mismo (cuchillas, mordazas, alineacion y dos
-- maniobras), no un aislador. La semilla de V3 lo tradujo como «Catenary insulators», que en la ficha
-- de una tarea de seccionador se leeria como otra cosa.
UPDATE maintenance_task_type
   SET description = 'Catenary isolators (particular revision)', updated_at = now()
 WHERE code = 'RP-07';
