# 07 · Auditing

Three layers answering three questions.

## Who touched a row last

`created_at`/`updated_at`/`created_by`/`updated_by` on every `AuditableEntity` (Spring Data JPA
auditing). The actor comes from `AuditActorResolver`: the JWT `preferred_username`, `system`
outside a request (consumer, scheduled work), `unknown` inside a request without authentication.

## What the previous values were

Hibernate Envers, `audit_revision` as the custom revision entity (instant, username, user id,
source `HTTP`/`MESSAGING`/`SYSTEM`). Audited: `CatenaryAsset`, `MaintenanceOrder`,
`MaintenanceTask` (with `maintenance_task_task_type_aud`), `MaintenanceTaskCheckItem`,
`MaintenanceInspection`, `MaintenanceInspectionItem`, `CatenaryDefect`,
`MaintenanceMaterialUsage`, `MaintenanceShift` (with its tracks, its blocking disconnectors, since
`V20` its linemen, `maintenance_shift_lineman_aud`, and since `V23` its instruments,
`maintenance_shift_instrument_aud`) and `MaintenanceShiftVehicle` (`V20`, a vehicle of a shift with
where it was left). Who went out that night, in what and with what is history of the shift: its
leader, supervisor, linemen and instruments are in its revisions, and parking a vehicle at close is a
revision of that vehicle line, not of the shift.

Not audited, on purpose: `MaintenanceStatusHistory` (append-only, a twin would duplicate it),
`InboxMessage` and `CatenaryAssetSwitch` (written only from master data, the twin would sit empty
and read as "never changed"; the switches' history lives in `mto-configuration`), `OutboxMessage`
(written only by the outbox; its history is itself), `MaintenanceTeam`,
`MaintenanceTaskType`, `InspectionTemplate`/`Item` (catalogues managed by migrations; a team
synchronized from `mto-resources` is written by native SQL anyway), `CrewPerson`, `FleetVehicle` and
`MaintenanceInstrument` (`V23`) (snapshots of `mto-resources` written only with native SQL, like
`CatenaryAssetSwitch`: a twin would sit empty; their history, their qualifications, documents and
calibrations included, lives in `mto-resources`) and `BackgroundOperation` (`V21`: written only by
its native statements, its own history, and purged).
`@Audited` goes on each entity, never on `AuditableEntity`; `JpaEntityModelTest` guards the
partition and `EnversAuditDataJpaTest` checks the revisions end to end (it disables the test
transaction on purpose: Envers writes at commit).

A migration that changes a column of an audited table must change its `_aud` twin in the same
migration (nullable, no constraints).

Deleting a row keeps its last state (`store_data_at_delete`): a material line removed from an
order leaves a DELETED revision with the author, the source and the correlation id.

Known gap: master-data upserts are native SQL and leave no revision, and there is no baseline
revision either. An asset that only arrived by master data therefore has no history of its own
(`/assets/{id}/revisions` answers 404 `AST-404`), and what hangs from it cannot look it up in
`catenary_asset_aud`: the `asset` of an order, a task, an inspection and a defect is
`@Audited(targetAuditMode = NOT_AUDITED)`, like the blocking disconnectors of a shift, so a
revision reads the asset as it is now. No request changes the asset of any of them. Before that,
the history of an order, an inspection or a defect on a profile, a disconnector or a section
insulator answered 500. The crew of a shift is read the same way: its `teamLeader` and `supervisor`
(columns of `maintenance_shift_aud`), its `linemen` (the join is audited, the person is not) and the
`vehicle` of each `MaintenanceShiftVehicle` are `NOT_AUDITED` targets, and so are its `instruments`
(`V23`; the join is audited, the instrument is not), so a revision shows the snapshot of
`mto-resources` as it is now: a calibration renewed after the night reads with its new date.

The vehicles of a shift are ordered by `id` (`@OrderBy("id ASC")`), not by `createdAt`: Envers
applies the `@OrderBy` of a collection to its `_aud` table too, and `maintenance_shift_vehicle_aud`
has no audit columns, so ordering by `createdAt` would make every read of a shift's revision fail.
What is shown is ordered by fleet code (`MaintenanceShift.vehiclesByCode()`: the response, the
events and the report).

A background operation (`02-domain-model.md`) writes in another thread after its request has
answered, and still records the revision as that request: `BackgroundOperationContext` carries what
the request gave (its `X-Correlation-Id`, method, URI, address, user agent), and
`AuditRevisionListener` stamps it with source `HTTP`; the `SecurityContext` is copied to the virtual
thread, so the author is the person who asked. The events it publishes carry the same
`correlationId` (`MessageContextResolver`).

History is read at `GET /<resource>/{id}/revisions` (`assets`, `orders`, `shifts`, `inspections`,
`defects`), paged, each entry with the revision metadata and the entity as it was.

## Why a status changed

`maintenance_status_history`: one row per transition of an order or a defect with
`previous_status`, `new_status`, `changed_at`, `changed_by` and the `comment`/`reason` given in the
request. Exposed at `/orders/{id}/history` and `/defects/{id}/history`. Envers would answer "what
changed" but not "why", and the reason is what a supervisor looks for.
