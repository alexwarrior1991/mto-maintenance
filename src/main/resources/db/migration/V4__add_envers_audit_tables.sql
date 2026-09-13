-- Historial de cambios (Hibernate Envers).
--
-- Envers escribe una fila en audit_revision por TRANSACCION que toca una entidad auditada, y una
-- fila en <tabla>_aud por entidad cambiada dentro de ella. El "quien y cuando" esta en
-- audit_revision; las gemelas _aud guardan solo el estado de negocio en esa revision, y por eso no
-- repiten created_by / updated_by / created_at / updated_at.
--
-- Se auditan diez tablas: catenary_asset, maintenance_shift, maintenance_order, maintenance_task,
-- maintenance_task_task_type, maintenance_task_check_item, maintenance_inspection,
-- maintenance_inspection_item, catenary_defect y maintenance_material_usage. NO se auditan:
--   * maintenance_status_history, que ya es un historial append-only.
--   * inbox_message, que se escribe con SQL nativo: Envers no lo veria.
--   * maintenance_team, maintenance_task_type, inspection_template e inspection_template_item, que
--     son catalogos sembrados por migracion y de cambio raro.
--
-- Los activos que llegan por evento de datos maestros NO dejan revision: el upsert es SQL nativo a
-- proposito (la marca de agua se comprueba dentro del where). catenary_asset_aud recoge solo el
-- camino REST. Ver docs/07-auditing.md.
--
-- REGLA DE MANTENIMIENTO: toda migracion futura que anada, quite, renombre o cambie de tipo una
-- columna de una de las tablas base auditadas tiene que aplicar el mismo cambio a su gemela _aud en
-- esa misma migracion, con dos diferencias: en la gemela la columna es siempre nullable y no lleva
-- CHECK, UNIQUE ni clave ajena. Hibernate construye el mapeo de Envers desde la entidad, asi que
-- una columna anadida sin su gemela hace fallar a ddl-auto: validate y la aplicacion no arranca.

-- INCREMENT BY 1 tiene que coincidir con allocationSize = 1 en AuditRevision. No hay revision de
-- partida: las tablas nacen vacias con el historial ya instalado.
CREATE SEQUENCE audit_revision_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE audit_revision (
    id integer PRIMARY KEY,
    -- Milisegundos desde epoch, no timestamptz: es el tipo que @RevisionTimestamp admite en
    -- cualquier version de Envers. AuditRevision.getRevisionInstant() lo devuelve como Instant.
    timestamp bigint NOT NULL,
    username varchar(100),
    user_id varchar(100),
    -- HTTP | MESSAGING | SYSTEM | BASELINE. Dice de que canal vino la escritura y, por tanto, a que
    -- espacio de identificadores pertenece correlation_id.
    source varchar(20),
    correlation_id varchar(200),
    ip_address varchar(100),
    user_agent varchar(500),
    request_method varchar(20),
    request_uri varchar(500)
);

CREATE INDEX idx_audit_revision_timestamp ON audit_revision (timestamp);
CREATE INDEX idx_audit_revision_username ON audit_revision (username);
CREATE INDEX idx_audit_revision_correlation_id ON audit_revision (correlation_id)
    WHERE correlation_id IS NOT NULL;

-- Las gemelas. Todas las columnas de negocio son nullable y sin CHECK ni UNIQUE ni clave ajena a la
-- tabla base: una fila de historial tiene que sobrevivir a la fila que describe. La unica clave
-- ajena es rev. La clave primaria es (rev, id), que es lo que espera Envers, pero la consulta real
-- -"historial de esta entidad"- filtra por id; de ahi el indice (id, rev).

CREATE TABLE catenary_asset_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code varchar(64),
    name varchar(255),
    type catenary_asset_type,
    description text,
    execution_package_id bigint,
    track_id bigint,
    station_id bigint,
    start_kp numeric(12, 3),
    end_kp numeric(12, 3),
    profile_source_id varchar(100),
    sectioning varchar(255),
    track_kind track_kind,
    source_service varchar(100),
    source_entity_id varchar(100),
    source_sequence_number bigint,
    enabled boolean,
    preventive_interval_days integer,
    last_preventive_completed_at timestamptz,
    CONSTRAINT pk_catenary_asset_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_catenary_asset_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);
CREATE INDEX idx_catenary_asset_aud_id_rev ON catenary_asset_aud (id, rev);

CREATE TABLE maintenance_shift_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code varchar(32),
    shift_date date,
    team_id uuid,
    base_name varchar(120),
    vehicle varchar(120),
    possession_type possession_type,
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
    track_id bigint,
    start_kp numeric(12, 3),
    end_kp numeric(12, 3),
    personnel text,
    measurement_equipment varchar(500),
    status shift_status,
    observations text,
    CONSTRAINT pk_maintenance_shift_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_maintenance_shift_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);
CREATE INDEX idx_maintenance_shift_aud_id_rev ON maintenance_shift_aud (id, rev);

CREATE TABLE maintenance_order_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code varchar(32),
    title varchar(255),
    description text,
    type maintenance_order_type,
    status maintenance_order_status,
    priority maintenance_priority,
    asset_id uuid,
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
    stock_project_id uuid,
    CONSTRAINT pk_maintenance_order_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_maintenance_order_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);
CREATE INDEX idx_maintenance_order_aud_id_rev ON maintenance_order_aud (id, rev);

CREATE TABLE maintenance_task_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    order_id uuid,
    sequence integer,
    description varchar(500),
    status maintenance_task_status,
    assigned_user varchar(100),
    asset_id uuid,
    shift_id uuid,
    started_at timestamptz,
    completed_at timestamptz,
    defects_found text,
    notes text,
    photo_refs text,
    CONSTRAINT pk_maintenance_task_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_maintenance_task_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);
CREATE INDEX idx_maintenance_task_aud_id_rev ON maintenance_task_aud (id, rev);

-- Tabla de coleccion de la relacion tarea <-> tipo de tarea: Envers la audita por su cuenta con la
-- clave (rev, task_id, task_type_id).
CREATE TABLE maintenance_task_task_type_aud (
    rev integer NOT NULL,
    task_id uuid NOT NULL,
    task_type_id uuid NOT NULL,
    revtype smallint,
    CONSTRAINT pk_maintenance_task_task_type_aud PRIMARY KEY (rev, task_id, task_type_id),
    CONSTRAINT fk_maintenance_task_task_type_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);

CREATE TABLE maintenance_task_check_item_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    task_id uuid,
    code varchar(32),
    label varchar(255),
    unit varchar(32),
    min_value numeric(12, 3),
    max_value numeric(12, 3),
    requires_measure boolean,
    measured_value numeric(12, 3),
    adjusted boolean,
    value_after_adjustment numeric(12, 3),
    item_result check_item_result,
    notes text,
    order_index integer,
    CONSTRAINT pk_maintenance_task_check_item_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_maintenance_task_check_item_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);
CREATE INDEX idx_maintenance_task_check_item_aud_id_rev ON maintenance_task_check_item_aud (id, rev);

CREATE TABLE maintenance_inspection_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code varchar(32),
    asset_id uuid,
    execution_package_id bigint,
    track_id bigint,
    station_id bigint,
    kp numeric(12, 3),
    inspection_date date,
    inspector varchar(100),
    inspection_kind inspection_kind,
    template_id uuid,
    result inspection_result,
    description text,
    detected_defects text,
    recommended_actions text,
    generated_defect_id uuid,
    generated_order_id uuid,
    origin_order_id uuid,
    shift_id uuid,
    CONSTRAINT pk_maintenance_inspection_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_maintenance_inspection_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);
CREATE INDEX idx_maintenance_inspection_aud_id_rev ON maintenance_inspection_aud (id, rev);

CREATE TABLE maintenance_inspection_item_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    inspection_id uuid,
    code varchar(32),
    label varchar(255),
    unit varchar(32),
    min_value numeric(12, 3),
    max_value numeric(12, 3),
    requires_measure boolean,
    measured_value numeric(12, 3),
    adjusted boolean,
    value_after_adjustment numeric(12, 3),
    item_result check_item_result,
    notes text,
    order_index integer,
    CONSTRAINT pk_maintenance_inspection_item_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_maintenance_inspection_item_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);
CREATE INDEX idx_maintenance_inspection_item_aud_id_rev ON maintenance_inspection_item_aud (id, rev);

CREATE TABLE catenary_defect_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code varchar(32),
    asset_id uuid,
    inspection_id uuid,
    order_id uuid,
    severity defect_severity,
    status defect_status,
    description text,
    technical_notes text,
    detected_at timestamptz,
    resolved_at timestamptz,
    resolution_notes text,
    discard_reason text,
    execution_package_id bigint,
    track_id bigint,
    station_id bigint,
    start_kp numeric(12, 3),
    end_kp numeric(12, 3),
    correction_type varchar(120),
    parts_replaced text,
    resolved_in_shift_id uuid,
    repair_planned_date date,
    found_in_task_id uuid,
    photo_refs text,
    CONSTRAINT pk_catenary_defect_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_catenary_defect_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);
CREATE INDEX idx_catenary_defect_aud_id_rev ON catenary_defect_aud (id, rev);

CREATE TABLE maintenance_material_usage_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    order_id uuid,
    task_id uuid,
    material_id uuid,
    material_code varchar(64),
    material_description_snapshot varchar(255),
    warehouse_id uuid,
    planned_quantity numeric(19, 6),
    consumed_quantity numeric(19, 6),
    unit varchar(32),
    allow_over_consumption boolean,
    stock_reservation_id uuid,
    stock_sync_status stock_sync_status,
    stock_sync_error text,
    CONSTRAINT pk_maintenance_material_usage_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_maintenance_material_usage_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);
CREATE INDEX idx_maintenance_material_usage_aud_id_rev ON maintenance_material_usage_aud (id, rev);
