-- Esquema del servicio de mantenimiento de catenaria.
--
-- Este servicio NO guarda infraestructura: un activo mantenible es una referencia ligera (id externo,
-- snapshot de codigo/nombre y localizacion) a lo que publica mto-configuration por RabbitMQ, mas los
-- tramos por via que define el propio mantenimiento. Los ids de mto-configuration son bigint; los de
-- mto-stock, uuid. Los ids propios son uuid, como en mto-stock.
--
-- Las columnas created_at / updated_at / created_by / updated_by las rellena Spring Data auditing;
-- el historial de valores previos lo lleva Hibernate Envers (V4).

-- ---------------------------------------------------------------------------------------------
-- Tipos
-- ---------------------------------------------------------------------------------------------

CREATE TYPE catenary_asset_type AS ENUM ('TRACK_SECTION', 'PROFILE', 'DISCONNECTOR', 'SECTION_INSULATOR');
CREATE TYPE track_kind AS ENUM ('MAIN', 'DIVERTED');
CREATE TYPE maintenance_order_type AS ENUM ('PREVENTIVE', 'CORRECTIVE', 'INSPECTION', 'URGENT');
CREATE TYPE maintenance_order_status AS ENUM ('DRAFT', 'PLANNED', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');
CREATE TYPE maintenance_priority AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
CREATE TYPE maintenance_task_status AS ENUM ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');
CREATE TYPE shift_status AS ENUM ('PLANNED', 'IN_PROGRESS', 'CLOSED', 'CANCELLED');
CREATE TYPE possession_type AS ENUM ('PARTIAL', 'FULL');
CREATE TYPE functional_group AS ENUM (
    'STRUCTURAL_SUPPORTS', 'OVERHEAD_CONDUCTORS', 'DEVICES_AND_SWITCHES',
    'ANCHORAGE_COMPONENTS', 'TURNOUTS_AND_SWITCHES', 'DIAGNOSTICS', 'NONE');
CREATE TYPE task_unit AS ENUM ('UNIT', 'SPAN', 'KM', 'DEFECT', 'PROFILE');
CREATE TYPE inspection_kind AS ENUM ('VISUAL', 'TECHNICAL');
CREATE TYPE inspection_result AS ENUM ('OK', 'MINOR_DEFECT', 'MAJOR_DEFECT', 'UNSAFE');
CREATE TYPE check_item_result AS ENUM ('OK', 'DEFECT', 'NOT_APPLICABLE');
CREATE TYPE defect_severity AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
CREATE TYPE defect_status AS ENUM ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'DISCARDED');
CREATE TYPE stock_sync_status AS ENUM ('NOT_REQUESTED', 'RESERVED', 'CONSUMED', 'RELEASED', 'FAILED');

-- Codigos legibles de ordenes, turnos, inspecciones y defectos (MO-000001, SH-000001, INS-000001,
-- DEF-000001). Una secuencia por agregado y sin reinicio anual: reiniciar por ano exige una
-- secuencia por ano o un MAX bajo bloqueo, y no aporta nada al negocio.
CREATE SEQUENCE maintenance_order_code_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE maintenance_shift_code_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE maintenance_inspection_code_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE catenary_defect_code_seq START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------------------------------
-- Catalogos
-- ---------------------------------------------------------------------------------------------

-- Equipos de mantenimiento del plan OCS: dos equipos con vehiculo propio y base (Rishpon, Mishmar),
-- cada uno responsable de un grupo de paquetes de ejecucion.
CREATE TABLE maintenance_team (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(16) NOT NULL,
    name varchar(120) NOT NULL,
    base_name varchar(120),
    vehicle varchar(120),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_maintenance_team_code UNIQUE (code)
);

-- Paquetes de ejecucion (ids de mto-configuration) que atiende cada equipo.
CREATE TABLE maintenance_team_execution_package (
    team_id uuid NOT NULL,
    execution_package_id bigint NOT NULL,
    CONSTRAINT pk_maintenance_team_execution_package PRIMARY KEY (team_id, execution_package_id),
    CONSTRAINT fk_maintenance_team_execution_package_team
        FOREIGN KEY (team_id) REFERENCES maintenance_team (id) ON DELETE CASCADE
);

-- Catalogo de tipos de tarea del plan OCS (RG = revision general, RP = revision particular). El
-- tiempo estandar por unidad y el tiempo fijo permiten estimar la carga de una orden
-- (fixed_minutes + standard_minutes_per_unit * unidades). requires_full_possession marca lo que
-- solo puede hacerse con posesion total de via (grupos 3 y 5 y RP-12): entre semana, con una sola
-- via cortada y la contigua en tension, esas tareas estan prohibidas.
CREATE TABLE maintenance_task_type (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(16) NOT NULL,
    description varchar(255) NOT NULL,
    functional_group functional_group NOT NULL DEFAULT 'NONE',
    standard_minutes_per_unit numeric(8, 2) NOT NULL DEFAULT 0,
    unit task_unit NOT NULL DEFAULT 'UNIT',
    fixed_minutes numeric(8, 2) NOT NULL DEFAULT 0,
    requires_full_possession boolean NOT NULL DEFAULT false,
    is_diagnostic boolean NOT NULL DEFAULT false,
    active boolean NOT NULL DEFAULT true,
    order_index integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_maintenance_task_type_code UNIQUE (code),
    CONSTRAINT chk_maintenance_task_type_minutes_non_negative
        CHECK (standard_minutes_per_unit >= 0 AND fixed_minutes >= 0)
);

-- Plantillas de inspeccion por tipo de activo: los seccionadores y los aisladores de seccion tienen
-- inspeccion propia, distinta de la del perfil. Se versionan; la activa de mayor version es la que
-- se copia a cada inspeccion o tarea nueva.
CREATE TABLE inspection_template (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_type catenary_asset_type NOT NULL,
    version integer NOT NULL DEFAULT 1,
    name varchar(120) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_inspection_template_asset_type_version UNIQUE (asset_type, version)
);

-- Umbrales ORIENTATIVOS: los definitivos los fija ingenieria con ISR (el plan OCS lo deja para una
-- fase posterior). Un item sin min/max es de estado (OK / DEFECT), no de medida.
CREATE TABLE inspection_template_item (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id uuid NOT NULL,
    code varchar(32) NOT NULL,
    label varchar(255) NOT NULL,
    unit varchar(32),
    min_value numeric(12, 3),
    max_value numeric(12, 3),
    requires_measure boolean NOT NULL DEFAULT false,
    order_index integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_inspection_template_item_code UNIQUE (template_id, code),
    CONSTRAINT fk_inspection_template_item_template
        FOREIGN KEY (template_id) REFERENCES inspection_template (id) ON DELETE CASCADE,
    CONSTRAINT chk_inspection_template_item_range CHECK (min_value IS NULL OR max_value IS NULL OR min_value <= max_value)
);

-- ---------------------------------------------------------------------------------------------
-- Activos mantenibles
-- ---------------------------------------------------------------------------------------------

-- PROFILE, DISCONNECTOR y SECTION_INSULATOR nacen de los eventos de mto-configuration y se
-- identifican por (source_service, source_entity_id), con la marca de agua source_sequence_number
-- comprobada dentro del UPDATE que los escribe. TRACK_SECTION lo define el mantenimiento por API:
-- una via entre dos puntos kilometricos, principal o desviada (las desviadas solo se trabajan con
-- posesion total).
--
-- Nunca se borra un activo: ordenes, inspecciones y defectos lo referencian con ON DELETE RESTRICT
-- y el borrado en origen lo deja enabled = false.
CREATE TABLE catenary_asset (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(64) NOT NULL,
    name varchar(255) NOT NULL,
    type catenary_asset_type NOT NULL,
    description text,
    execution_package_id bigint,
    track_id bigint,
    station_id bigint,
    start_kp numeric(12, 3),
    end_kp numeric(12, 3),
    -- Perfil (id de mto-configuration) del que cuelga un seccionador.
    profile_source_id varchar(100),
    -- Snapshot de los codigos de seccionamiento del perfil (A/S, S/A, AnMP, MP...): los informes lo
    -- muestran por perfil y no debe resolverse contra mto-configuration en cada consulta.
    sectioning varchar(255),
    track_kind track_kind,
    source_service varchar(100),
    source_entity_id varchar(100),
    source_sequence_number bigint,
    enabled boolean NOT NULL DEFAULT true,
    preventive_interval_days integer,
    last_preventive_completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_catenary_asset_code UNIQUE (code),
    CONSTRAINT uq_catenary_asset_source UNIQUE (source_service, source_entity_id),
    -- O ninguna de las dos o las dos: una sola no identifica nada, y una fila a medio rellenar
    -- volveria a insertarse en la siguiente entrega en lugar de actualizarse.
    CONSTRAINT chk_catenary_asset_source_columns_together CHECK (
        (source_service IS NULL AND source_entity_id IS NULL)
        OR (source_service IS NOT NULL AND source_entity_id IS NOT NULL)
    ),
    CONSTRAINT chk_catenary_asset_source_sequence_requires_source CHECK (
        source_sequence_number IS NULL OR source_service IS NOT NULL
    ),
    CONSTRAINT chk_catenary_asset_kp_range CHECK (start_kp IS NULL OR end_kp IS NULL OR start_kp <= end_kp),
    -- track_kind solo tiene sentido en un tramo, y un tramo siempre lo lleva.
    CONSTRAINT chk_catenary_asset_track_kind CHECK (
        (type = 'TRACK_SECTION' AND track_kind IS NOT NULL)
        OR (type <> 'TRACK_SECTION' AND track_kind IS NULL)
    ),
    CONSTRAINT chk_catenary_asset_preventive_interval_positive CHECK (
        preventive_interval_days IS NULL OR preventive_interval_days > 0
    )
);

CREATE INDEX idx_catenary_asset_track_kp ON catenary_asset (track_id, start_kp);
CREATE INDEX idx_catenary_asset_type_enabled ON catenary_asset (type, enabled);
CREATE INDEX idx_catenary_asset_execution_package ON catenary_asset (execution_package_id);
CREATE INDEX idx_catenary_asset_station ON catenary_asset (station_id);

-- ---------------------------------------------------------------------------------------------
-- Turnos
-- ---------------------------------------------------------------------------------------------

-- El turno nocturno es la unidad real de ejecucion y el origen del informe diario: ventana
-- contractual 21:00-05:00, tiempo efectivo de 4 a 5 horas, seccionadores abiertos a cada lado de la
-- posesion, puntos de tierra, via y kp trabajados. Un turno cubre UNA via: el informe es por via.
CREATE TABLE maintenance_shift (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(32) NOT NULL,
    shift_date date NOT NULL,
    team_id uuid,
    base_name varchar(120),
    vehicle varchar(120),
    possession_type possession_type NOT NULL,
    planned_start timestamptz,
    planned_end timestamptz,
    actual_start timestamptz,
    actual_end timestamptz,
    voltage_cutoff_at timestamptz,
    net_work_minutes integer,
    block_a_disconnector_id uuid,
    block_b_disconnector_id uuid,
    earthing_points varchar(500),
    parking_place varchar(255),
    execution_package_id bigint,
    track_id bigint NOT NULL,
    start_kp numeric(12, 3),
    end_kp numeric(12, 3),
    personnel text,
    measurement_equipment varchar(500),
    status shift_status NOT NULL DEFAULT 'PLANNED',
    observations text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_maintenance_shift_code UNIQUE (code),
    CONSTRAINT fk_maintenance_shift_team FOREIGN KEY (team_id) REFERENCES maintenance_team (id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_shift_block_a FOREIGN KEY (block_a_disconnector_id) REFERENCES catenary_asset (id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_shift_block_b FOREIGN KEY (block_b_disconnector_id) REFERENCES catenary_asset (id) ON DELETE RESTRICT,
    CONSTRAINT chk_maintenance_shift_planned_window CHECK (planned_start IS NULL OR planned_end IS NULL OR planned_start < planned_end),
    CONSTRAINT chk_maintenance_shift_actual_window CHECK (actual_start IS NULL OR actual_end IS NULL OR actual_start <= actual_end),
    CONSTRAINT chk_maintenance_shift_kp_range CHECK (start_kp IS NULL OR end_kp IS NULL OR start_kp <= end_kp),
    CONSTRAINT chk_maintenance_shift_net_minutes_non_negative CHECK (net_work_minutes IS NULL OR net_work_minutes >= 0),
    -- Un turno cerrado tiene tiempos reales: sin ellos no hay informe diario.
    CONSTRAINT chk_maintenance_shift_closed_has_actual_times CHECK (
        status <> 'CLOSED' OR (actual_start IS NOT NULL AND actual_end IS NOT NULL)
    )
);

CREATE INDEX idx_maintenance_shift_date_team ON maintenance_shift (shift_date, team_id);
CREATE INDEX idx_maintenance_shift_track_date ON maintenance_shift (track_id, shift_date);
CREATE INDEX idx_maintenance_shift_status ON maintenance_shift (status);

-- ---------------------------------------------------------------------------------------------
-- Ordenes y tareas
-- ---------------------------------------------------------------------------------------------

CREATE TABLE maintenance_order (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(32) NOT NULL,
    title varchar(255) NOT NULL,
    description text,
    type maintenance_order_type NOT NULL,
    status maintenance_order_status NOT NULL DEFAULT 'DRAFT',
    priority maintenance_priority NOT NULL DEFAULT 'MEDIUM',
    asset_id uuid NOT NULL,
    -- Localizacion copiada del activo al crear, editable mientras la orden esta en DRAFT/PLANNED.
    execution_package_id bigint,
    track_id bigint,
    station_id bigint,
    start_kp numeric(12, 3),
    end_kp numeric(12, 3),
    planned_date date,
    actual_start_date timestamptz,
    actual_end_date timestamptz,
    team_id uuid,
    assigned_user varchar(100),
    closing_notes text,
    cancellation_reason text,
    origin_inspection_id uuid,
    origin_defect_id uuid,
    -- Proyecto de mto-stock (uuid) contra el que se reservan los materiales. Se resuelve por codigo
    -- EP-<execution_package_id> cuando la orden tiene EP; sin el no hay reserva posible y el consumo
    -- al completar es una salida directa.
    stock_project_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_maintenance_order_code UNIQUE (code),
    CONSTRAINT fk_maintenance_order_asset FOREIGN KEY (asset_id) REFERENCES catenary_asset (id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_order_team FOREIGN KEY (team_id) REFERENCES maintenance_team (id) ON DELETE RESTRICT,
    CONSTRAINT chk_maintenance_order_kp_range CHECK (start_kp IS NULL OR end_kp IS NULL OR start_kp <= end_kp),
    CONSTRAINT chk_maintenance_order_actual_dates CHECK (
        actual_start_date IS NULL OR actual_end_date IS NULL OR actual_start_date <= actual_end_date
    ),
    -- Las reglas "completada exige inicio real" y "cancelada exige motivo" viven tambien aqui: el
    -- servicio las comprueba, pero una fila que las incumpla no debe poder existir.
    CONSTRAINT chk_maintenance_order_completed_has_dates CHECK (
        status <> 'COMPLETED' OR (actual_start_date IS NOT NULL AND actual_end_date IS NOT NULL)
    ),
    CONSTRAINT chk_maintenance_order_cancelled_has_reason CHECK (
        status <> 'CANCELLED' OR cancellation_reason IS NOT NULL
    )
);

CREATE INDEX idx_maintenance_order_status_planned_date ON maintenance_order (status, planned_date);
CREATE INDEX idx_maintenance_order_asset_status ON maintenance_order (asset_id, status);
CREATE INDEX idx_maintenance_order_track_kp ON maintenance_order (track_id, start_kp);
CREATE INDEX idx_maintenance_order_execution_package ON maintenance_order (execution_package_id);
CREATE INDEX idx_maintenance_order_type_priority ON maintenance_order (type, priority);
CREATE INDEX idx_maintenance_order_team ON maintenance_order (team_id);

-- Una tarea es, en el preventivo, un perfil concreto del tramo; en el resto, un trabajo de la orden.
-- Se ejecuta dentro de un turno (shift_id) y es una fila del informe diario.
CREATE TABLE maintenance_task (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id uuid NOT NULL,
    sequence integer NOT NULL,
    description varchar(500) NOT NULL,
    status maintenance_task_status NOT NULL DEFAULT 'PENDING',
    assigned_user varchar(100),
    asset_id uuid,
    shift_id uuid,
    started_at timestamptz,
    completed_at timestamptz,
    defects_found text,
    notes text,
    -- Referencias a fotos (una por linea). El almacenamiento de ficheros queda fuera del servicio.
    photo_refs text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_maintenance_task_order_sequence UNIQUE (order_id, sequence),
    CONSTRAINT fk_maintenance_task_order FOREIGN KEY (order_id) REFERENCES maintenance_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_task_asset FOREIGN KEY (asset_id) REFERENCES catenary_asset (id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_task_shift FOREIGN KEY (shift_id) REFERENCES maintenance_shift (id) ON DELETE RESTRICT,
    CONSTRAINT chk_maintenance_task_times CHECK (started_at IS NULL OR completed_at IS NULL OR started_at <= completed_at),
    CONSTRAINT chk_maintenance_task_completed_has_time CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL)
);

CREATE INDEX idx_maintenance_task_shift ON maintenance_task (shift_id);
CREATE INDEX idx_maintenance_task_asset_status ON maintenance_task (asset_id, status);
CREATE INDEX idx_maintenance_task_order_status ON maintenance_task (order_id, status);

-- Tipos de tarea (RG/RP) realizados en una tarea. Una fila del informe diario suele llevar varios:
-- "Clean insulators, tighten cantilever screws and steady arm, straighten droppers".
CREATE TABLE maintenance_task_task_type (
    task_id uuid NOT NULL,
    task_type_id uuid NOT NULL,
    CONSTRAINT pk_maintenance_task_task_type PRIMARY KEY (task_id, task_type_id),
    CONSTRAINT fk_maintenance_task_task_type_task FOREIGN KEY (task_id) REFERENCES maintenance_task (id) ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_task_task_type_type FOREIGN KEY (task_type_id) REFERENCES maintenance_task_type (id) ON DELETE RESTRICT
);

-- Puntos a analizar / ajustar de una tarea, copiados de la plantilla del tipo de activo cuando la
-- tarea incluye un tipo de diagnostico. Misma forma que los items de inspeccion.
CREATE TABLE maintenance_task_check_item (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id uuid NOT NULL,
    code varchar(32) NOT NULL,
    label varchar(255) NOT NULL,
    unit varchar(32),
    min_value numeric(12, 3),
    max_value numeric(12, 3),
    requires_measure boolean NOT NULL DEFAULT false,
    measured_value numeric(12, 3),
    adjusted boolean NOT NULL DEFAULT false,
    value_after_adjustment numeric(12, 3),
    item_result check_item_result,
    notes text,
    order_index integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_maintenance_task_check_item_code UNIQUE (task_id, code),
    CONSTRAINT fk_maintenance_task_check_item_task FOREIGN KEY (task_id) REFERENCES maintenance_task (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------------------------
-- Inspecciones
-- ---------------------------------------------------------------------------------------------

CREATE TABLE maintenance_inspection (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(32) NOT NULL,
    asset_id uuid NOT NULL,
    execution_package_id bigint,
    track_id bigint,
    station_id bigint,
    kp numeric(12, 3),
    inspection_date date NOT NULL,
    inspector varchar(100),
    inspection_kind inspection_kind NOT NULL DEFAULT 'VISUAL',
    template_id uuid,
    result inspection_result NOT NULL,
    description text,
    detected_defects text,
    recommended_actions text,
    generated_defect_id uuid,
    generated_order_id uuid,
    -- Inspeccion hecha dentro de una orden de tipo INSPECTION.
    origin_order_id uuid,
    shift_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_maintenance_inspection_code UNIQUE (code),
    CONSTRAINT fk_maintenance_inspection_asset FOREIGN KEY (asset_id) REFERENCES catenary_asset (id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_inspection_template FOREIGN KEY (template_id) REFERENCES inspection_template (id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_inspection_generated_order FOREIGN KEY (generated_order_id) REFERENCES maintenance_order (id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_inspection_origin_order FOREIGN KEY (origin_order_id) REFERENCES maintenance_order (id) ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_inspection_shift FOREIGN KEY (shift_id) REFERENCES maintenance_shift (id) ON DELETE RESTRICT
);

CREATE INDEX idx_maintenance_inspection_result_date ON maintenance_inspection (result, inspection_date);
CREATE INDEX idx_maintenance_inspection_asset ON maintenance_inspection (asset_id);
CREATE INDEX idx_maintenance_inspection_track ON maintenance_inspection (track_id);
CREATE INDEX idx_maintenance_inspection_origin_order ON maintenance_inspection (origin_order_id);

CREATE TABLE maintenance_inspection_item (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_id uuid NOT NULL,
    code varchar(32) NOT NULL,
    label varchar(255) NOT NULL,
    unit varchar(32),
    min_value numeric(12, 3),
    max_value numeric(12, 3),
    requires_measure boolean NOT NULL DEFAULT false,
    measured_value numeric(12, 3),
    adjusted boolean NOT NULL DEFAULT false,
    value_after_adjustment numeric(12, 3),
    item_result check_item_result,
    notes text,
    order_index integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_maintenance_inspection_item_code UNIQUE (inspection_id, code),
    CONSTRAINT fk_maintenance_inspection_item_inspection
        FOREIGN KEY (inspection_id) REFERENCES maintenance_inspection (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------------------------
-- Defectos
-- ---------------------------------------------------------------------------------------------

CREATE TABLE catenary_defect (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(32) NOT NULL,
    asset_id uuid NOT NULL,
    inspection_id uuid,
    order_id uuid,
    severity defect_severity NOT NULL,
    status defect_status NOT NULL DEFAULT 'OPEN',
    description text NOT NULL,
    technical_notes text,
    detected_at timestamptz NOT NULL,
    resolved_at timestamptz,
    resolution_notes text,
    discard_reason text,
    execution_package_id bigint,
    track_id bigint,
    station_id bigint,
    start_kp numeric(12, 3),
    end_kp numeric(12, 3),
    -- Corrective Action Table del informe diario: que correccion se aplico, que piezas se cambiaron,
    -- en que turno quedo resuelto o para que fecha queda pendiente.
    correction_type varchar(120),
    parts_replaced text,
    resolved_in_shift_id uuid,
    repair_planned_date date,
    found_in_task_id uuid,
    photo_refs text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_catenary_defect_code UNIQUE (code),
    CONSTRAINT fk_catenary_defect_asset FOREIGN KEY (asset_id) REFERENCES catenary_asset (id) ON DELETE RESTRICT,
    CONSTRAINT fk_catenary_defect_inspection FOREIGN KEY (inspection_id) REFERENCES maintenance_inspection (id) ON DELETE RESTRICT,
    CONSTRAINT fk_catenary_defect_order FOREIGN KEY (order_id) REFERENCES maintenance_order (id) ON DELETE RESTRICT,
    CONSTRAINT fk_catenary_defect_resolved_in_shift FOREIGN KEY (resolved_in_shift_id) REFERENCES maintenance_shift (id) ON DELETE RESTRICT,
    CONSTRAINT fk_catenary_defect_found_in_task FOREIGN KEY (found_in_task_id) REFERENCES maintenance_task (id) ON DELETE SET NULL,
    CONSTRAINT chk_catenary_defect_kp_range CHECK (start_kp IS NULL OR end_kp IS NULL OR start_kp <= end_kp),
    CONSTRAINT chk_catenary_defect_dates CHECK (resolved_at IS NULL OR detected_at <= resolved_at),
    CONSTRAINT chk_catenary_defect_resolved_has_date CHECK (status NOT IN ('RESOLVED', 'CLOSED') OR resolved_at IS NOT NULL)
);

CREATE INDEX idx_catenary_defect_severity_status ON catenary_defect (severity, status);
CREATE INDEX idx_catenary_defect_asset ON catenary_defect (asset_id);
CREATE INDEX idx_catenary_defect_order ON catenary_defect (order_id);
CREATE INDEX idx_catenary_defect_detected_at ON catenary_defect (detected_at);

-- Las claves cruzadas orden <-> inspeccion / defecto se anaden al final porque las tres tablas se
-- referencian entre si.
ALTER TABLE maintenance_order
    ADD CONSTRAINT fk_maintenance_order_origin_inspection
        FOREIGN KEY (origin_inspection_id) REFERENCES maintenance_inspection (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_maintenance_order_origin_defect
        FOREIGN KEY (origin_defect_id) REFERENCES catenary_defect (id) ON DELETE RESTRICT;

ALTER TABLE maintenance_inspection
    ADD CONSTRAINT fk_maintenance_inspection_generated_defect
        FOREIGN KEY (generated_defect_id) REFERENCES catenary_defect (id) ON DELETE RESTRICT;

-- ---------------------------------------------------------------------------------------------
-- Materiales
-- ---------------------------------------------------------------------------------------------

-- Materiales previstos y consumidos por una orden (o por una tarea concreta, si task_id no es nulo).
-- Nada de stock se guarda aqui: material_id y warehouse_id son uuid de mto-stock y la reserva vive
-- alli; stock_sync_status dice en que punto esta la conversacion con stock.
CREATE TABLE maintenance_material_usage (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id uuid NOT NULL,
    task_id uuid,
    material_id uuid NOT NULL,
    material_code varchar(64),
    material_description_snapshot varchar(255),
    warehouse_id uuid NOT NULL,
    planned_quantity numeric(19, 6) NOT NULL,
    consumed_quantity numeric(19, 6) NOT NULL DEFAULT 0,
    unit varchar(32) NOT NULL,
    allow_over_consumption boolean NOT NULL DEFAULT false,
    stock_reservation_id uuid,
    stock_sync_status stock_sync_status NOT NULL DEFAULT 'NOT_REQUESTED',
    stock_sync_error text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    -- NULLS NOT DISTINCT: sin ello dos lineas de orden (task_id nulo) del mismo material y almacen
    -- pasarian la restriccion.
    CONSTRAINT uq_maintenance_material_usage_line UNIQUE NULLS NOT DISTINCT (order_id, task_id, material_id, warehouse_id),
    CONSTRAINT fk_maintenance_material_usage_order FOREIGN KEY (order_id) REFERENCES maintenance_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_material_usage_task FOREIGN KEY (task_id) REFERENCES maintenance_task (id) ON DELETE SET NULL,
    CONSTRAINT chk_maintenance_material_usage_quantities_non_negative
        CHECK (planned_quantity >= 0 AND consumed_quantity >= 0),
    CONSTRAINT chk_maintenance_material_usage_over_consumption
        CHECK (allow_over_consumption OR consumed_quantity <= planned_quantity)
);

CREATE INDEX idx_maintenance_material_usage_order ON maintenance_material_usage (order_id);
CREATE INDEX idx_maintenance_material_usage_sync_status ON maintenance_material_usage (stock_sync_status);

-- ---------------------------------------------------------------------------------------------
-- Historial de estados
-- ---------------------------------------------------------------------------------------------

-- Append-only: quien cambio el estado de una orden o de un defecto, cuando, desde que estado y con
-- que comentario. No se audita con Envers (seria auditar un historial).
CREATE TABLE maintenance_status_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id uuid,
    defect_id uuid,
    previous_status varchar(32),
    new_status varchar(32) NOT NULL,
    changed_at timestamptz NOT NULL DEFAULT now(),
    changed_by varchar(100) NOT NULL,
    comment text,
    CONSTRAINT fk_maintenance_status_history_order FOREIGN KEY (order_id) REFERENCES maintenance_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_status_history_defect FOREIGN KEY (defect_id) REFERENCES catenary_defect (id) ON DELETE CASCADE,
    -- Exactamente un dueno.
    CONSTRAINT chk_maintenance_status_history_single_owner CHECK (
        (order_id IS NOT NULL AND defect_id IS NULL) OR (order_id IS NULL AND defect_id IS NOT NULL)
    )
);

CREATE INDEX idx_maintenance_status_history_order ON maintenance_status_history (order_id, changed_at);
CREATE INDEX idx_maintenance_status_history_defect ON maintenance_status_history (defect_id, changed_at);

-- ---------------------------------------------------------------------------------------------
-- Funciones auxiliares
-- ---------------------------------------------------------------------------------------------

-- Fecha de vencimiento del preventivo de un activo. Existe como funcion SQL para que el filtro
-- "preventivo vencido antes de" se resuelva en la base (y pagine bien) desde una Specification, que
-- no sabe sumar un intervalo por fila.
CREATE FUNCTION preventive_due_at(last_completed timestamptz, interval_days integer) RETURNS timestamptz
    LANGUAGE sql IMMUTABLE
    RETURN last_completed + make_interval(days => interval_days);
