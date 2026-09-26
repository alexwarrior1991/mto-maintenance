# 03 · Database

PostgreSQL, Flyway migrations in `src/main/resources/db/migration`, Hibernate `ddl-auto: validate`.
All tables have `id uuid`, `created_at`/`updated_at timestamptz`, `created_by`/`updated_by`
(except the catalogues and the history, see each table). Kp columns are `numeric(12,3)` (metres
with millimetre precision), quantities `numeric(19,6)`.

## Enums (PostgreSQL types)

`catenary_asset_type`, `track_kind`, `maintenance_order_type`, `maintenance_order_status`,
`maintenance_priority`, `maintenance_task_status`, `shift_status`, `possession_type`,
`functional_group`, `task_unit`, `inspection_kind`, `inspection_result`, `check_item_result`,
`defect_severity`, `defect_status`, `stock_sync_status`, `inbox_message_status`,
`section_insulator_installation` (`V7`). Values match the Java enums one to one; adding a value is a
migration (`ALTER TYPE … ADD VALUE`).

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
| `maintenance_shift` (+ `maintenance_shift_track`, `V5`; + `maintenance_shift_disconnector`, `V6`) | Shifts, the tracks they cover and the disconnectors opened to block the zone | `uq code`, planned/actual windows ordered, `CLOSED` needs actual times, net minutes ≥ 0; `pk (shift_id, track_id)`, `pk (shift_id, disconnector_id)` with `on delete restrict` on the asset |
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
`maintenance_shift_track (track_id)`, `maintenance_shift_disconnector (disconnector_id)`; inspection `(result, inspection_date)`; defect `(severity, status)`,
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

## Shift blocking disconnectors (`V6`)

`maintenance_shift.block_a_disconnector_id` / `block_b_disconnector_id` moved to the join table
`maintenance_shift_disconnector (shift_id, disconnector_id)`: a safe cut opens every disconnector
that isolates the zone, not two. Existing rows were copied. Envers twin
`maintenance_shift_disconnector_aud`.

## Section insulator switches (`V7`)

`catenary_asset` gains `connected_track_id` and `installation_type`
(`section_insulator_installation`: `TRACK_CONNECTION` / `IN_TRACK`), both nullable and both
meaningful only on a `SECTION_INSULATOR`; the `_aud` twin gains the same two columns. `track_kind`
is **not** reused for this: `chk_catenary_asset_track_kind` forbids it outside a `TRACK_SECTION`.

`catenary_asset_switch (id, asset_id, code, kp numeric(12,3), turnout_denominator, track_id,
enabled)` holds one row per turnout the insulator connects through — `W31` at its kp with its `1:9`
rate. Unique `(asset_id, code)`, `CHECK turnout_denominator > 0`, FK `on delete cascade` (nothing
else references a switch, and a master-data asset is never deleted, only disabled). `track_id` is a
`bigint` because it is an id of `mto-configuration`, like the asset's own.

`enabled` mirrors the flag the source publishes per switch. A turnout taken out of service still
arrives in the event — the source collection filters soft-deleted rows, not disabled ones — so it is
stored marked rather than dropped: "that turnout does not exist" and "that turnout is out of
service" are different answers for a crew on the ground, and the shift report prints the second as
`W31 1:9 (out of service)`. A payload with no `enabled` key means enabled, as everywhere else.

**No `_aud` twin, on purpose**, and no new `catenary_asset_type` value. The rows are written *only*
by the master-data handler, the same reason `inbox_message` has no twin: it would sit empty and read
as "never changed". The history of the insulator lives in `mto-configuration`, which owns the data.
The collection on `CatenaryAsset` is therefore `@NotAudited`.

## Stock rejections (`V8`)

`stock_sync_status` gains `REJECTED` (`ALTER TYPE … ADD VALUE`; the `_aud` twin uses the same type).
`FAILED` now means only that `mto-stock` did not answer; `REJECTED` that it answered no, with its
reason in `stock_sync_error`. No row changes: a line left `FAILED` by an old rejection is still
retried by `sync`, which now tells the two apart.

## Disconnector and insulator location (`V9`)

No schema change. `mto-configuration` publishes neither the track nor the package of a
disconnector, nor the package of a section insulator; since `V9` they are derived from the profiles
(`docs/06-messaging.md`), and the migration filled what was stored: disconnectors from their profile
(`profile_source_id` = the profile's `source_entity_id`), insulators from the profiles of their
track, and then the empty `execution_package_id`/`track_id` of the orders, defects and inspections
created on them (only the empty ones: a value is never overwritten). Like every master-data write it
is SQL outside Hibernate, so the `_aud` twins show the holes until the next change through the API.

## Auditing (`V4`)

`audit_revision` (custom revision entity: instant, username, user id, source) and the `_aud`
twins of the nine audited entities plus `maintenance_task_task_type_aud` (and, since `V5`,
`maintenance_shift_track_aud`, and since `V6` `maintenance_shift_disconnector_aud`). See `07-auditing.md`.
