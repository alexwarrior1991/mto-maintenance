-- Las agujas del aislador de seccion, y las dos columnas que necesita el aislador para situarlas.
--
-- Un aislador de seccion separa electricamente dos secciones de alimentacion de la catenaria.
-- Normalmente se coloca donde CONECTAN DOS VIAS, es decir sobre una aguja; a veces se coloca en
-- medio de una sola via. Cada conexion con la via se identifica por una aguja etiquetada 'W' y un
-- numero, en un punto kilometrico, con la tangente de su desvio al lado: 'W31 1:9', 'W57 1:8'.
--
-- Hasta ahora el aislador llegaba aqui con nombre, estacion y estado, y nada mas: el activo nacia
-- sin KP y sin via, de modo que no habia con que situar al equipo sobre el terreno ni con que
-- encontrarlo en una ventana kilometrica. mto-configuration ya publica el dato (su V23); esto es
-- donde aterriza.
--
-- NO hay un tipo de activo nuevo: la aguja es PARTE del aislador, no un activo mantenible aparte.
-- Por eso cuelga de catenary_asset y no amplia catenary_asset_type.

CREATE TYPE section_insulator_installation AS ENUM ('TRACK_CONNECTION', 'IN_TRACK');

-- ---------------------------------------------------------------------------------------------
-- Las dos columnas nuevas del activo.
--
-- Anulables: solo tienen sentido en un SECTION_INSULATOR, y ni siquiera en todos —un aislador en
-- medio de una via no conecta con ninguna otra—. Van tambien a la gemela _aud en ESTA misma
-- migracion: Hibernate construye el mapeo de Envers desde la entidad, asi que una columna anadida
-- sin su gemela hace fallar a ddl-auto: validate y la aplicacion no arranca.
--
-- No se reutiliza track_kind para nada: chk_catenary_asset_track_kind lo prohibe fuera de un
-- TRACK_SECTION.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE catenary_asset     ADD COLUMN connected_track_id bigint;
ALTER TABLE catenary_asset_aud ADD COLUMN connected_track_id bigint;

ALTER TABLE catenary_asset     ADD COLUMN installation_type section_insulator_installation;
ALTER TABLE catenary_asset_aud ADD COLUMN installation_type section_insulator_installation;

CREATE INDEX idx_catenary_asset_connected_track ON catenary_asset (connected_track_id);

-- ---------------------------------------------------------------------------------------------
-- La aguja.
--
-- Sin gemela _aud a proposito. Es la misma razon por la que inbox_message tampoco la tiene: estas
-- filas se escriben UNICAMENTE desde los eventos de datos maestros, que ya de por si son SQL
-- nativo que Envers no ve, asi que la gemela se quedaria vacia y se leeria como "nunca cambio",
-- que es peor que no tenerla. El historial del aislador esta en mto-configuration, que es quien
-- posee el dato. Por eso la coleccion va @NotAudited en la entidad.
--
-- ON DELETE CASCADE es seguro aqui y solo aqui: nada mas referencia a la aguja, y el activo nunca
-- se borra desde datos maestros —se desactiva, porque ordenes, inspecciones y defectos apuntan a
-- el—, asi que el cascade solo se dispara si alguien borra el activo a mano.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE catenary_asset_switch (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id uuid NOT NULL,
    -- El identificador del plano: 'W' y un numero. Cuarenta, el mismo ancho que la columna de
    -- mto-configuration: mas estrecha obligaria a truncar, y dos codigos distintos podrian
    -- acabar chocando contra uq_catenary_asset_switch sin que se vea de donde sale el choque.
    code varchar(40) NOT NULL,
    -- En METROS y con tres decimales, como start_kp/end_kp: el plano escribe '110+176'.
    kp numeric(12, 3),
    -- El 9 de '1:9'. Solo el denominador: el numerador siempre es 1, y el entero se puede ordenar.
    turnout_denominator integer,
    -- Id de mto-configuration, como track_id del activo: bigint, no uuid.
    track_id bigint,
    -- Una aguja fuera de servicio sigue estando en el plano y en el evento, asi que se guarda en
    -- lugar de descartarla: el parte de turno la marca y el equipo sabe que no puede contar con
    -- ella. Igual que catenary_asset.enabled, y por lo mismo.
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_catenary_asset_switch_asset FOREIGN KEY (asset_id)
        REFERENCES catenary_asset (id) ON DELETE CASCADE,
    -- El codigo identifica la aguja dentro de su aislador, igual que en el origen.
    CONSTRAINT uq_catenary_asset_switch UNIQUE (asset_id, code),
    CONSTRAINT chk_catenary_asset_switch_denominator CHECK (
        turnout_denominator IS NULL OR turnout_denominator > 0
    )
);

CREATE INDEX idx_catenary_asset_switch_asset ON catenary_asset_switch (asset_id);
CREATE INDEX idx_catenary_asset_switch_code ON catenary_asset_switch (code);
