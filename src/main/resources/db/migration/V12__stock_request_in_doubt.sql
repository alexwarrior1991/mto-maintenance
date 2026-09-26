-- La peticion a mto-stock que una linea de material mando y se quedo sin respuesta.
--
-- Una reserva o una salida que agotan el tiempo, o a las que stock responde con un 5xx, pueden
-- haberse aplicado alla sin que la linea lo sepa. La clave de idempotencia hace que repetirla no la
-- duplique, pero solo si se repite: hasta ahora la linea quedaba FAILED y lo siguiente que se hiciera
-- con ella podia olvidarla. Completar la orden daba salida directa a lo usado y la reserva que quiza
-- se habia creado seguia reteniendo material; cancelar o quitar la linea no la liberaba; y cambiar lo
-- previsto o el proyecto cambiaba la clave, con lo que el reintento creaba otra.
--
-- Esta columna dice que peticion tiene la linea en duda: RESERVATION u OUTPUT, o NULL si no hay
-- ninguna. Lo siguiente que se haga con la linea contra stock empieza por repetirla, con la misma
-- clave y el mismo cuerpo, y solo despues sigue; mientras tanto no cambia lo que viaja en ella (lo
-- previsto, lo consumido, el proyecto de stock de la orden).
--
-- Las lineas FAILED de antes no se marcan: no se sabe que peticion les fallo. Siguen como estaban.

CREATE TYPE stock_request_type AS ENUM ('RESERVATION', 'OUTPUT');

ALTER TABLE maintenance_material_usage ADD COLUMN stock_request_in_doubt stock_request_type;

-- La gemela _aud la lleva tambien, sin restricciones: el historial dice cuando se quedo una peticion
-- en duda y cuando se resolvio.
ALTER TABLE maintenance_material_usage_aud ADD COLUMN stock_request_in_doubt stock_request_type;
