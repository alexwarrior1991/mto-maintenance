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
`MaintenanceMaterialUsage`, `MaintenanceShift`.

Not audited, on purpose: `MaintenanceStatusHistory` (append-only, a twin would duplicate it),
`InboxMessage` and `CatenaryAssetSwitch` (written only from master data, the twin would sit empty
and read as "never changed"; the switches' history lives in `mto-configuration`), `OutboxMessage`
(written only by the outbox; its history is itself), `MaintenanceTeam`,
`MaintenanceTaskType`, `InspectionTemplate`/`Item` (catalogues managed by migrations).
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
insulator answered 500.

History is read at `GET /<resource>/{id}/revisions` (`assets`, `orders`, `shifts`, `inspections`,
`defects`), paged, each entry with the revision metadata and the entity as it was.

## Why a status changed

`maintenance_status_history`: one row per transition of an order or a defect with
`previous_status`, `new_status`, `changed_at`, `changed_by` and the `comment`/`reason` given in the
request. Exposed at `/orders/{id}/history` and `/defects/{id}/history`. Envers would answer "what
changed" but not "why", and the reason is what a supervisor looks for.
