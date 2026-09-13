# 03 · Database

PostgreSQL, Flyway migrations in `src/main/resources/db/migration`, Hibernate `ddl-auto: validate`.
All tables have `id uuid`, `created_at`/`updated_at timestamptz`, `created_by`/`updated_by`
(except the catalogues and the history, see each table). Kp columns are `numeric(12,3)` (metres
with millimetre precision), quantities `numeric(19,6)`.

## Enums (PostgreSQL types)

`catenary_asset_type`, `track_kind`, `maintenance_order_type`, `maintenance_order_status`,
`maintenance_priority`, `maintenance_task_status`, `shift_status`, `possession_type`,
`functional_group`, `task_unit`, `inspection_kind`, `inspection_result`, `check_item_result`,
`defect_severity`, `defect_status`, `stock_sync_status`, `inbox_message_status`. Values match the
Java enums one to one; adding a value is a migration (`ALTER TYPE … ADD VALUE`).

## Sequences

`maintenance_order_code_seq` (`MO-000001`), `maintenance_shift_code_seq` (`SH-`),
`maintenance_inspection_code_seq` (`INS-`), `catenary_defect_code_seq` (`DEF-`). Six digits, no
yearly reset.

## Tables (`V1`)

| Table | Purpose | Notable constraints |
|---|---|---|
| `maintenance_team` (+ `maintenance_team_execution_package`) | Teams A/B and the EPs they cover | `uq code` |
| `maintenance_task_type` | `RG`/`RP` catalogue | `uq code`, minutes ≥ 0 |
| `inspection_template`, `inspection_template_item` | Checklists per asset type | `uq (asset_type, version)`, `uq (template_id, code)`, `min ≤ max` |
| `catenary_asset` | Assets | `uq code`, `uq (source_service, source_entity_id)`, source columns together, `start_kp ≤ end_kp`, `track_kind` only and always on `TRACK_SECTION`, interval > 0 |
| `maintenance_shift` (+ `maintenance_shift_track`, `V5`) | Shifts and the tracks they cover | `uq code`, planned/actual windows ordered, `CLOSED` needs actual times, net minutes ≥ 0; `pk (shift_id, track_id)` |
| `maintenance_order` | Orders | `uq code`, kp range, actual dates ordered, `COMPLETED` needs dates, `CANCELLED` needs reason; FKs to asset (restrict), team, shift-independent |
| `maintenance_task` (+ `maintenance_task_task_type`) | Tasks and their types | `uq (order_id, sequence)`, times ordered, `COMPLETED` needs `completed_at` |
| `maintenance_task_check_item` | Checklist of a diagnostic task | `uq (task_id, code)` |
| `maintenance_inspection`, `maintenance_inspection_item` | Inspections and items | `uq code`, `uq (inspection_id, code)` |
| `catenary_defect` | Defects | `uq code`, kp range, `detected_at ≤ resolved_at`, `RESOLVED`/`CLOSED` need `resolved_at` |
| `maintenance_material_usage` | Material lines | `uq (order_id, task_id, material_id, warehouse_id)` with `NULLS NOT DISTINCT`, quantities ≥ 0, `consumed ≤ planned` unless the flag |
| `maintenance_status_history` | Append-only history | exactly one of `order_id`/`defect_id`; no audit columns beyond `changed_at`/`changed_by` |

Indexes follow the API filters: asset `(track_id, start_kp)`, `(type, enabled)`; order
`(status, planned_date)`, `(asset_id, status)`, `(track_id, start_kp)`, `(type, priority)`; task
`(shift_id)`, `(asset_id, status)`, `(order_id, status)`; shift `(shift_date, team_id)`,
`maintenance_shift_track (track_id)`; inspection `(result, inspection_date)`; defect `(severity, status)`,
`(detected_at)`; material `(stock_sync_status)`.

`preventive_due_at(last_completed, interval_days)` is a SQL function used by the
`preventiveDueBefore` filter: `NULL` when there is no interval, `-infinity` when never done.

## Inbox (`V2`)

`inbox_message`: copy of the `mto-stock` table. Unique `(message_id, source_service)`, `payload`
as `json` (not `jsonb`, so the bytes keep matching `payload_hash`), statuses
`RECEIVED`/`PROCESSING`/`PROCESSED`/`FAILED`, attempts and last error.

## Seeds (`V3`)

- `maintenance_team`: `A` (Rishpon, EP 4/5/6) and `B` (Mishmar, EP 9/11/15).
- `maintenance_task_type`: the 30 codes of the OCS plan with functional group, standard minutes,
  unit, fixed minutes, `requires_full_possession` and `is_diagnostic`.
- `inspection_template` v1 for `PROFILE` (14 items: pole, foundation, cantilever, insulators,
  steady arm, height, stagger, droppers, wear, …), `DISCONNECTOR` and `SECTION_INSULATOR`.
  Thresholds are indicative; engineering fixes them by inserting a new version and deactivating
  the previous one.

## Shift tracks (`V5`)

`maintenance_shift.track_id` moved to the collection `maintenance_shift_track (shift_id, track_id)`
so one night can cover several tracks; existing rows were copied over. Envers twin
`maintenance_shift_track_aud`.

## Auditing (`V4`)

`audit_revision` (custom revision entity: instant, username, user id, source) and the `_aud`
twins of the nine audited entities plus `maintenance_task_task_type_aud` (and, since `V5`,
`maintenance_shift_track_aud`). See `07-auditing.md`.
