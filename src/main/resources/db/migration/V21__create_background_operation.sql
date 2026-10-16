-- Operaciones de fondo (BudgetedOperations): lo que tarda mas de lo que la peticion esta dispuesta a
-- esperar (Prefer: respond-async) sigue en un hilo virtual y se contesta con 202 y esta fila.
--
-- El tope de operaciones vivas y el turno de cada ambito (un EP) se deciden aqui, dentro de la
-- sentencia que escribe y con recuento de filas, y no en memoria: con tres replicas un semaforo por
-- JVM seria el triple del tope. Una operacion viva late (heartbeat_at); la que deja de latir, porque su
-- replica murio, se da por abandonada.
--
-- No tiene gemela _aud: es su propio historico, y una purga borra las terminadas. Es la misma tabla
-- que la de mto-resources (V4), copiada como el outbox.

CREATE TYPE background_operation_status AS ENUM (
    'QUEUED',
    'RUNNING',
    'SUCCEEDED',
    'FAILED'
);

CREATE TABLE background_operation (
    id uuid PRIMARY KEY,
    kind varchar(60) NOT NULL,
    -- Ambito en el que no pueden correr dos a la vez ('EP-14'); nulo si no hay ninguno.
    scope varchar(100),
    status background_operation_status NOT NULL DEFAULT 'QUEUED',
    requested_by varchar(100) NOT NULL,
    correlation_id varchar(200),
    request_method varchar(10),
    request_uri varchar(500),
    -- Al terminar bien: el estado HTTP y el cuerpo que habria dado la llamada sincrona.
    result_status integer,
    result json,
    -- Al terminar mal: el estado, el codigo y el mensaje de esa misma llamada.
    error_status integer,
    error_code varchar(40),
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz,
    heartbeat_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_background_operation_finished CHECK (
        (status IN ('SUCCEEDED', 'FAILED')) = (finished_at IS NOT NULL)
    ),
    CONSTRAINT chk_background_operation_failed_has_error CHECK (status <> 'FAILED' OR error_code IS NOT NULL)
);

-- El tope cuenta las vivas, y el turno de un ambito mira las vivas de ese ambito.
CREATE INDEX idx_background_operation_alive ON background_operation (status, heartbeat_at)
    WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX idx_background_operation_scope_alive ON background_operation (scope, created_at)
    WHERE status IN ('QUEUED', 'RUNNING') AND scope IS NOT NULL;
CREATE INDEX idx_background_operation_finished_at ON background_operation (finished_at)
    WHERE status IN ('SUCCEEDED', 'FAILED');
