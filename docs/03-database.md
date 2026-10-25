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
`section_insulator_installation` (`V7`), `stock_request_type` (`V12`), `disconnector_drive_type` (`V16`),
`background_operation_status` (`V21`). Values match the Java enums one to one; adding a value is a
migration (`ALTER TYPE … ADD VALUE`). The statuses, categories and mobilities of `mto-resources`
(`V19`), and the kinds of its instruments, qualifications and documents (`V23`), are `varchar` on
purpose: they are another service's, and a value added there breaks nothing here.

## Sequences

`maintenance_order_code_seq` (`MO-000001`), `maintenance_shift_code_seq` (`SH-`),
`maintenance_inspection_code_seq` (`INS-`), `catenary_defect_code_seq` (`DEF-`). Six digits, no
yearly reset.

## Tables (`V1`)

| Table | Purpose | Notable constraints |
|---|---|---|
| `maintenance_team` (+ `maintenance_team_execution_package`) | Teams (local, like the seeded A/B, or synchronized from `mto-resources` since `V19`) and the EPs they cover | `uq code` |
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
`maintenance_shift_track (track_id)`, `maintenance_shift_disconnector (disconnector_id)`; inspection `(result, inspection_date)`,
`(shift_id)` (`V15`, the inspections of a shift's report); defect `(severity, status)`,
`(detected_at)`; material `(stock_sync_status)`.

`preventive_due_at(last_completed, interval_days)` is a SQL function used by the
`preventiveDueBefore` filter: `NULL` when there is no interval, `-infinity` when never done.

## Inbox (`V2`)

`inbox_message`: copy of the `mto-stock` table. Unique `(message_id, source_service)`, `payload`
as `json` (not `jsonb`, so the bytes keep matching `payload_hash`), statuses
`RECEIVED`/`PROCESSING`/`PROCESSED`/`FAILED`, attempts and last error. One table for both consumed
channels: `source_service` is `mto-configuration` for the master data and, since `V19`,
`mto-resources` for its people, teams and vehicles.

## Seeds (`V3`)

- `maintenance_team`: `A` (Team A - Rishpon) and `B` (Team B - Mishmar), with their base and vehicle
  as text. No migration seeds their execution packages: `maintenance_team_execution_package` starts
  empty for them (a local team gets its packages through `POST`/`PUT /teams`), and since `V19` a team
  gets them from `mto-resources` when it is adopted, which also empties its base: a synchronized team
  has none.
- `maintenance_task_type`: the 30 codes of the OCS plan with functional group, standard minutes
  (those of the task sheet since `V18`), unit, fixed minutes, `requires_full_possession` and
  `is_diagnostic`.
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

A disconnector without a pole (`profile_source_id` null) brings its own kp and track since `V26` of
`mto-configuration`, and takes the package of its track like an insulator. No schema change either:
it is the same `track_id`, `start_kp`/`end_kp` and `execution_package_id`.

A disconnector that puts two tracks in parallel brings, since `V27` of `mto-configuration`, the other
track it joins (on a pole or without one). It goes to `connected_track_id`, which `V7` added for the
section insulator, so it is no longer meaningful only on a `SECTION_INSULATOR`: no schema change.

A disconnector on a pole that switches another track than its pole's keeps that track instead of its
profile's: `own_track` (`V14`, below).

## Asset enabled at the source and locally (`V10`)

`catenary_asset` gains `enabled_at_source` (nullable: what `mto-configuration` last said; `null` on
own sections) and `disabled_locally` (`not null default false`: maintenance's decision), both also in
`catenary_asset_aud`. `enabled` stays as the effective value every query and the `(type, enabled)`
index read, and `chk_catenary_asset_enabled` keeps it equal to
`coalesce(enabled_at_source, true) and not disabled_locally`. The master-data upsert writes
`enabled_at_source` and recomputes `enabled` without touching `disabled_locally`. Existing rows:
synchronized ones got `enabled_at_source = enabled`, own ones `disabled_locally = not enabled`.

## Optimistic locking (`V11`)

`version bigint not null default 0` on `catenary_asset`, `maintenance_order`, `maintenance_task`,
`maintenance_task_check_item`, `maintenance_shift`, `maintenance_inspection`,
`maintenance_inspection_item`, `catenary_defect` and `maintenance_material_usage`, mapped with
`@Version` in `VersionedEntity`. Hibernate bumps it on every write; the native master-data SQL of
`CatenaryAssetRepository` bumps it by hand. Not in the `_aud` twins: a row's version is not history,
and Envers does not audit it.

## Stock request in doubt (`V12`)

`maintenance_material_usage` gains `stock_request_in_doubt` (`stock_request_type`: `RESERVATION` or
`OUTPUT`, nullable), also in `maintenance_material_usage_aud`: the keyed request to `mto-stock` the
line sent and got no answer to. Anything done next with the line against stock repeats it first, with
the same idempotency key and body, and it is cleared as soon as stock answers, yes or no
(`docs/02-domain-model.md`). Existing `FAILED` lines are left `NULL`: nobody knows which request
failed. No index: the automatic retry looks for `FAILED` lines, which
`idx_maintenance_material_usage_sync_status` already covers.

## Outbox (`V13`)

`outbox_message`: the outbox of the events this service publishes (`06-messaging.md`), the
`mto-configuration` table in its final shape. `id` (uuid), `aggregate_type`/`aggregate_id` (the
entity and its id), `event_type`, `exchange_name`, `routing_key`, `payload` (`text`: the envelope
as sent; not a large object, which would not be deleted with the row), `status` (`varchar` with a
`CHECK` for `PENDING`/`IN_PROGRESS`/`PUBLISHED`/`FAILED`: `ddl-auto: validate` does not check
constraints, so the entity's enum and the `CHECK` are kept in step by hand), `attempts`,
`max_attempts`, `sequence_number` (`bigint` from `outbox_message_sequence`, assigned by the
database: the order of the relay and the "something earlier of this aggregate still unpublished"
rule), `created_at`, `next_attempt_at`, `published_at`, `last_error` (1000, truncated), and the
W3C trace context `trace_parent`/`trace_state`. Partial indexes for the relay's claim
(`sequence_number` where pending/in progress), the aggregate retention, the purge (`published_at`
where published) and the failed count. Written only by the outbox; no `_aud` twin.

## Own track of a disconnector on a pole (`V14`)

`catenary_asset` gains `own_track` (`not null default false`; nullable in `catenary_asset_aud`). A
disconnector on a pole used to be always on the track of its profile; `mto-configuration` now also
publishes a `track` with a pole when the disconnector switches another track than its pole's (in a
bundle of tracks, in workshops and depots, it sometimes hangs from the pole of one track and
isolates another, or joins two that are not the pole's). `own_track` says that `track_id` is that
one: the master-data upsert sets it when the event of a disconnector on a pole brings a track, and
`inheritLocationOfDisconnector` and `propagateLocationToDisconnectors` then pass it only the
package of its profile. `chk_catenary_asset_own_track` allows it only on a `DISCONNECTOR` with a
pole (`profile_source_id`) and a track. Existing rows stay `false`, which is what they were: the
next event of each disconnector (a republish) sets it.

## Drive of a disconnector (`V16`)

`catenary_asset` gains `drive_type` (`disconnector_drive_type`: `MOTOR`, `MANUAL`; nullable, also in
`catenary_asset_aud`), how a disconnector is driven as `mto-configuration` publishes it
(`driveType`). It decides one of its default task types when a preventive order is generated:
`RP-06` (remote drive test) only with a motor. `chk_catenary_asset_drive_type` allows it only on a
`DISCONNECTOR`. Written only by the master-data upsert; existing rows stay `NULL` (no data) until the
next event of each disconnector (a republish).

## Function of a disconnector (`V17`)

`catenary_asset` gains `disconnector_function` (`varchar(40)`, nullable, also in
`catenary_asset_aud`): the code of the `DisconnectorFunction` LOV of `mto-configuration` exactly as
its event publishes it (`disconnectorFunction.code`: `Disc/IO`, `LoadB/NS`, `SurgeA`…). A surge
arrester (`SurgeA`, compared ignoring case) also gets `RG-06` (catenary automatic valves) by default.
The code is stored, not a flag, because it is the source's data. `chk_catenary_asset_disconnector_function`
allows it only on a `DISCONNECTOR`. Written only by the master-data upsert; existing rows stay `NULL`
until the next event of each disconnector (a republish).

`V17` also corrects the description of `RP-07`, seeded in `V3` as "Catenary insulators": the plan calls
it **catenary isolators**, the disconnector itself (blades, jaws, alignment and two operations), and it
is now "Catenary isolators (particular revision)".

## Minutes of the task types (`V18`)

`V3` seeded orientative minutes where it had none; the task sheet of the plan
(`mainteinance_inspection_element`, "Inspection / Verification (minutes)") fixes them, and 19 of the
30 did not match. `V18` sets `standard_minutes_per_unit` to the sheet's number, keeping each type's
`unit`, which is how the catalogue already read the ones that matched: per unit (`UNIT`, `SPAN`,
`PROFILE`, `DEFECT`, counted once per task) the time of the element, and per kilometre (`KM`) per km
of track, like `RG-08` and `RG-10`, which were 3 in both. `fixed_minutes` is not in the sheet and
stays.

| Code | Before | Now | | Code | Before | Now |
|---|---|---|---|---|---|---|
| `RG-02` | 6 | 4 | | `RP-01` | 10 | 4 |
| `RG-03` | 30 | 20 | | `RP-02` | 45 | 20 |
| `RG-04` | 45 | 20 | | `RP-03` | 60 | 20 |
| `RG-06` | 20 | 15 | | `RP-04` | 30 | 15 |
| `RG-09` | 20 | 15 | | `RP-05` (per span, `V22`) | 20 | 3 |
| `RG-12` (per span) | 5 | 3 | | `RP-06` | 45 | 20 |
| `RG-13` | 5 | 2 | | `RP-07` | 10 | 20 |
| `RG-14` (per km) | 2 | 3 | | `RP-08` | 5 | 4 |
| `RG-17` (per km) | 2 | 3 | | `RP-09` (per profile) | 15 | 3 |
| `RG-18` (per km) | 5 | 45 | | | | |

The other 11 already matched. The "10 km/day" vegetation rate of `RG-02` and `RP-01` is not a time per
unit and is not carried. Tasks store no minutes, so the estimate of existing orders changes with the
migration. With the default types, a profile is now 34 min instead of 40 (`RG-08` and `RG-10`, once
per track, do not change), a section insulator 80 with turnouts and 40 without (180 and 75 before), and
a disconnector 20 (10 before), plus 20 with a motor (45) and 15 for a surge arrester (20).

`V22` changes the `unit` of `RP-05` from `DEFECT` to `SPAN`: the sheet gives its 3 minutes per span,
like `RG-12`. The estimate does not change, because every unit but `KM` counts once per task; only what
the catalogue says does.

## Snapshots of `mto-resources` (`V19`)

The people, teams and vehicles of `mto-resources` (`06-messaging.md`, "Consumed: resources"), kept
as the snapshot a shift needs, like `catenary_asset` for the infrastructure. All written only with
native SQL and the sequence watermark (`source_sequence_number`, the `sequenceNumber` header of the
`mto-resources` outbox) inside the `WHERE`, never read-then-write, so **none has an `_aud` twin**:
Envers would not see those writes, and the history lives in `mto-resources`. The ids are those of
`mto-resources` (`uuid`).

- `crew_person (id, code varchar(16), full_name varchar(260), username varchar(100), category
  varchar(40), active, source_sequence_number, created_at, updated_at)`: no `created_by`/`updated_by`,
  only the events write it. `source_sequence_number` is `NULL` for a person that arrived inside a team
  event before its own, so its own event overwrites it whatever its number. Partial index on
  `lower(username)` where it is set.
- `fleet_vehicle (id, code varchar(32), name, type_code, type_name, mobility varchar(20), category
  varchar(40), self_propelled, portable, towing, seats, operational_status varchar(30), available,
  active, source_sequence_number, created_at, updated_at)`: what the crew rules read
  (`02-domain-model.md`). `towing` is derived from the `TOWING` capability of the vehicle's model.
- `maintenance_team` gains `source_service`, `source_entity_id` (`uuid`) and `source_sequence_number`
  (`chk_maintenance_team_source`: the first two together or neither; partial unique index
  `uq_maintenance_team_source_entity`, the conflict target of the upsert), `leader_person_id` and
  `supervisor_person_id` (FKs to `crew_person`, `on delete restrict`) and `default_vehicle_id`
  (**no FK**: the team event may arrive before the vehicle's). A local team has all of them `NULL`;
  adopting it (`adoptByCode`) fills the source and empties `base_name`. The JPA entity maps them
  read-only (`insertable = false, updatable = false`): only the native upsert writes them.
- `maintenance_team_member (team_id, person_id)`: the linemen of a synchronized team (the leader
  apart), replaced whole by every applied team event. PK on the pair, FK to the team `on delete
  cascade`, to the person `on delete restrict`, index on `person_id`.
- `maintenance_team_execution_package` (from `V1`) is now also written by the team event, replaced
  whole like the members.

## Shift crew and vehicles (`V20`)

- `maintenance_shift` gains `team_leader_person_id` and `supervisor_person_id` (FKs to `crew_person`,
  `on delete restrict`; partial index on the leader where set), also in `maintenance_shift_aud`.
- `maintenance_shift_lineman (shift_id, person_id)`: the linemen of that night, a set replaced whole.
  PK on the pair, FK to the shift `on delete cascade`, to the person `on delete restrict`, index on
  `person_id`. Envers twin `maintenance_shift_lineman_aud (rev, shift_id, person_id, revtype)`.
- `maintenance_shift_vehicle (id, shift_id, vehicle_id, parking_site_id uuid, parking_track_id
  bigint, parking_kp numeric(12,3), parking_description varchar(500))` plus the audit columns: a child
  entity and not a join table, because it carries where the vehicle was left. `uq (shift_id,
  vehicle_id)`, FK to the shift `on delete cascade`, to `fleet_vehicle` `on delete restrict`, index on
  `vehicle_id`. The parking site is an id of `mto-resources` and the track one of `mto-configuration`,
  without FK like every external id. No `version`: it is edited through its shift. Envers twin
  `maintenance_shift_vehicle_aud` (PK `(rev, id)`, no constraints).

The double-booking check (`02-domain-model.md`) reads these tables by `shift_date` and status; the
`(shift_date, team_id)` index of `V1` serves it, and the advisory locks per vehicle and person, not a
constraint, keep two shifts saved at once apart.

## Background operations (`V21`)

`background_operation`: the operations answered with 202 (`02-domain-model.md`), the same table as
`V4` of `mto-resources`, copied like the outbox. `id`, `kind` (`GENERATE_TASKS`), `scope`
(`EP-14`; `NULL` when there is none), `status` (`background_operation_status`: `QUEUED`, `RUNNING`,
`SUCCEEDED`, `FAILED`), `requested_by`, the request's `correlation_id`, `request_method` and
`request_uri`; on success `result_status` and `result` (`json`: the body the synchronous call would
have given); on failure `error_status`, `error_code` and `error_message`; `created_at`, `started_at`,
`finished_at` and `heartbeat_at`. `chk_background_operation_finished` (`finished_at` exactly when
`SUCCEEDED`/`FAILED`) and `chk_background_operation_failed_has_error`. Partial indexes for the cap
(`(status, heartbeat_at)` of the live ones), the turn of a scope (`(scope, created_at)` of the live
ones with a scope) and the purge (`finished_at` of the finished ones).

Written only by the native, conditional statements of `BackgroundOperationRepository` (the cap and
the turn are decided inside the statement that writes, by row count), each its own short
transaction; JPA only reads it. No audit columns beyond `requested_by` and no `_aud` twin: it is its
own history, and the purge deletes what finished more than `app.operations.retention` ago.

## Qualifications, documents and instruments (`V23`)

What the expiry rules of a shift read (`02-domain-model.md`, "Qualifications and expiries"), carried by
the events of `mto-resources` (`06-messaging.md`) into the snapshots of `V19`, **in the same native
statement as the rest of the row and behind the same watermark**. An event without the key (from a
`mto-resources` older than its `V8`) leaves what was stored: the upsert writes
`coalesce(cast(:value as jsonb), <stored>)`.

- `crew_person.qualifications jsonb NOT NULL DEFAULT '[]'`: the list of `person.*.qualifications` as it
  arrives, reduced to `[{code, kind, drivingLicence, expiresOn}]` (`expiresOn` an ISO date, or `null`
  when it never expires). All of them, also the expired ones: the shift decides by the date. A person
  that arrived inside a team event has `[]` until its own event.
- `fleet_vehicle.documents jsonb NOT NULL DEFAULT '[]'` (`[{kind, reference, expiresOn}]`) and
  `fleet_vehicle.required_driver_qualification varchar(32)`: the code of the qualification type its
  model requires of the driver (`vehicle.*.type.requiredDriverQualification`; `NULL`, none). Since a
  `null` there means "requires none", the upsert takes a flag that says whether the key travelled at
  all.
- `maintenance_instrument (id uuid, code varchar(32), name varchar(120), kind varchar(30),
  serial_number varchar(60), calibration_expires_on date, active, source_sequence_number, created_at,
  updated_at)`: the instruments of `mto-resources` (`instrument.*`), a snapshot like `crew_person`,
  written only by `MaintenanceInstrumentRepository.upsertFromResources` with the watermark inside the
  `WHERE`, so **no `_aud` twin**. `calibration_expires_on` `NULL` is "no calibration that expires"; its
  `kind` (`EARTHING_POLE`, `VOLTAGE_DETECTOR`, `MEASURING`, `OTHER`) is an enum of another service and
  goes as text.
- `maintenance_shift_instrument (shift_id, instrument_id)`: the instruments taken that night, a set
  replaced whole. PK on the pair, FK to the shift `on delete cascade`, to the instrument `on delete
  restrict`, index on `instrument_id`. Envers twin `maintenance_shift_instrument_aud (rev, shift_id,
  instrument_id, revtype)`, created by the same migration.

The JPA entities map the two `jsonb` columns as typed lists (`@JdbcTypeCode(SqlTypes.JSON)`:
`CrewPerson.Qualification`, `FleetVehicle.Document`), read-only like the rest of the snapshot.


## Availability, scope and task equipment (`V24`)

- `crew_person.unavailable jsonb NOT NULL DEFAULT '[]'` and `fleet_vehicle.unavailable jsonb NOT NULL
  DEFAULT '[]'`: the periods of `person.*.unavailable` and `vehicle.*.unavailable` (`[{from, to}]`, ISO
  dates, `to` null when the end is unknown), written in the same statement and behind the same watermark
  as the rest of the row; without the key, the stored ones stay.
- `fleet_vehicle.capabilities jsonb NOT NULL DEFAULT '[]'`: the capabilities of the vehicle's model
  (`vehicle.*.type.capabilities`, names of `mto-resources` such as `INSULATED_PLATFORM` or `EARTHING`),
  read for the equipment a task type requires. A row written before `V24` has none until the next event
  (republish `mto-resources`).
- `maintenance_team.chief_supervisor_person_id uuid`, **without** an FK, unlike the leader and the
  supervisor (`V19` gave those two theirs, `on delete restrict`; the comment of `V24`, which cannot be
  edited, says otherwise): the chief supervisor of a synchronized team, for the scope by execution
  package.
- `maintenance_task_type.version bigint NOT NULL DEFAULT 0` (the optimistic lock of its `PATCH`) and
  `maintenance_task_type_equipment (task_type_id, equipment)`, PK both, `CHECK equipment IN
  ('INSULATED_PLATFORM', 'EARTHING')`, cascade from the type. Nothing is seeded. None of them is audited:
  the snapshots are written with native SQL and the task types are a catalogue.
## Disconnectors of a pole (`V25`)

`idx_catenary_asset_profile_source` on `catenary_asset (source_service, profile_source_id)`: the
disconnectors of a pole, which the profile event updates in one statement
(`propagateLocationToDisconnectors`), a disconnector reads from its profile
(`inheritLocationOfDisconnector`) and a track deletion takes down (`deactivateByTrack`). Since `V29` of
`mto-configuration` a pole may carry several; `profile_source_id` was never unique here, so nothing else
changes.

## Auditing (`V4`)

`audit_revision` (custom revision entity: instant, username, user id, source) and the `_aud`
twins of the nine audited entities plus `maintenance_task_task_type_aud` (and, since `V5`,
`maintenance_shift_track_aud`, since `V6` `maintenance_shift_disconnector_aud`, since `V20`
`maintenance_shift_vehicle_aud`, the tenth audited entity, and `maintenance_shift_lineman_aud`, and
since `V23` `maintenance_shift_instrument_aud`). See `07-auditing.md`.
