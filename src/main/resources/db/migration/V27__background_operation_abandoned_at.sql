-- Una operacion de fondo dada por abandonada (su latido se paro) cuyo trabajo, sin embargo, termina.
--
-- El latido se para cuando la replica muere, pero tambien cuando solo va lenta: el planificador ocupado,
-- una pausa larga, la base de datos sin contestar un rato. Entonces otra replica la deja FAILED con
-- OPS-410 mientras su trabajo sigue, y al terminar ese trabajo, ya confirmado, la marca de exito no
-- encontraba la fila en RUNNING y no cambiaba nada: quien la pidio leia un fallo, repetia, y lo hecho se
-- hacia dos veces o se rechazaba. Ahora lo que dice la operacion es lo que hizo su trabajo (su resultado
-- o su error sustituyen al OPS-410), y esta columna guarda que se la dio por abandonada y cuando, para
-- que quien la vio fallida entre medias sepa por que cambio. Nula en las que nunca se abandonaron.

ALTER TABLE background_operation ADD COLUMN abandoned_at timestamptz;
