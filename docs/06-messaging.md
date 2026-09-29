# 06 · Messaging

`mto-maintenance` **consumes** the master-data change events that `mto-configuration` publishes,
and **publishes** its own events (an order created, a defect that changes state, a shift closed, a
material the warehouse rejects, the preventives about to fall due) for `mto-notification`. The two
channels are separate: the master-data contract is owned by `mto-configuration` (see its
`README_MESSAGING.md`) and mirrored here in `application/dto/messaging`; the own-events contract
is owned by this repository and documented in [Published events](#published-events).

## Consumed: master data from `mto-configuration`

### Topology

| | Value |
|---|---|
| Exchange (publisher's) | `mto.master-data.exchange` (topic) |
| Routing key pattern | `mto.master-data.#` (`mto.master-data.<entity>.<created|updated|deleted>`) |
| Queue (own) | `mto.maintenance.master-data.queue` |
| Dead letter | `mto.maintenance.master-data.queue.dlx` → `mto.maintenance.master-data.queue.dlq` |

Gated by `app.rabbitmq.enabled` (topology) and `app.rabbitmq.master-data.listener-enabled`
(consumer). With the first off the application starts without a broker (what the tests do).

### Envelope

`MasterDataChangedMessage{operationId, referenceId, origin, creationDate, eventType,
data{entityName, entityId, operation, values}, messageHash}` plus the headers `sequenceNumber`
(global, increasing), `messageSignature` and `messageSignatureAlgorithm`. The signature is
verified over the received bytes (`app.messaging.signature.{secret, mode}`, same secret as the
publisher; `OPTIONAL` by default).

### Inbox

`MasterDataEventConsumer` → `IdempotentMasterDataEventProcessor` → `InboxMessageService` → handler.
The idempotency key is `operationId` (fallback: AMQP `message_id`), guaranteed by the unique
constraint `(message_id, source_service)` of `inbox_message` with conditional native updates —
never read-then-write. Recording, claiming, the handler and the `PROCESSED` mark share one
transaction; `recordFailure` runs in its own after it. A message without `data`, `entityName` or
`operation` goes to the DLQ.

### Handlers

| Entity | Handler | Effect |
|---|---|---|
| `profile` | `ProfileMasterDataHandler` | Upsert `PROFILE` (`PRF-<id>`, name = `profileId`, kp, `track.id`, `track.executionPackageId`, `sectionings[].code` joined), then passes its track and package on to its disconnectors and its package to the insulators of its track / deactivate on `DELETED` |
| `disconnector` | `DisconnectorMasterDataHandler` | Upsert `DISCONNECTOR` (`DSC-<id>`, `station.id`, `profile.id`, `profile.kp`), then takes track and package from its profile / deactivate |
| `section-insulator` | `SectionInsulatorMasterDataHandler` | Upsert `SECTION_INSULATOR` (`SIN-<id>`, `station.id`, `enabled`, `installationType`, `track.id`, `connectedTrack.id`, kp range) **plus its `switches[]` into `catenary_asset_switch`**, then takes the package of the profiles of its track / deactivate |
| `track` | `TrackMasterDataHandler` | `DELETED` only: `deactivateByTrack(trackId)` |
| `execution-package`, `station`, `cantilever`, `steady-arm` | none | logged and ignored |

Upserts and deactivations are native SQL in `CatenaryAssetRepository` with the `sequenceNumber`
watermark compared inside the `WHERE`: an older event returns 0 rows and is discarded; a missing
sequence applies and keeps the stored watermark; a deletion advances it even on an already
disabled row. Payloads are read tolerantly (`MasterDataPayload`): numbers as strings or numbers,
nested objects, lists of nested objects (`nestedList`), missing keys → `null`; an
`installationType` this side does not know is stored as nothing rather than sent to the DLQ. A
handler runs inside the inbox transaction and must not open its own.

#### Track and package derived from the profiles

The contract leaves two holes that maintenance fills from its own rows, without changing it:

- a **disconnector** event carries its station and its profile (id, name, kp), but neither the track
  nor the execution package. Both are the profile's: `profile_source_id` of the disconnector is the
  `source_entity_id` of its `PROFILE` asset (the numeric id `mto-configuration` publishes for both);
- a **section insulator** event carries its track and the one it connects to, but not the package.
  It is the package of its track, which every profile of that track carries
  (`track.executionPackageId`).

Four native statements in `CatenaryAssetRepository` keep it, after an upsert that was applied and in
the same transaction, whichever event arrives first:

| After the upsert of | Statement | Effect |
|---|---|---|
| a disconnector | `inheritLocationOfDisconnector` | track and package of its profile, if it has arrived |
| a section insulator | `inheritPackageOfSectionInsulator` | package of the last profile received on its track |
| a profile | `propagateLocationToDisconnectors` | its track and package to its disconnectors: a profile that changes track takes them along |
| a profile | `propagatePackageToSectionInsulators` | its package to the insulators of its track: a track moved to another package moves them too |

Without them, both types were missing from the progress and monthly reports by package (and the
disconnectors from the progress report by track), from the searches by package or track, and from
the `EP-<package>` stock project of their orders. `V9` filled what was already stored, including
orders, defects and inspections created with the hole. Like the upsert, none of this leaves an
Envers revision. A disconnector whose profile never arrives keeps both empty.

A consequence: a track deleted at the source now also disables the disconnectors on it
(`deactivateByTrack`), as it already did with its profiles and insulators.

#### The switches of a section insulator

A section insulator normally sits where **two tracks connect**, that is on a turnout; sometimes it
sits in the middle of a single track. Each connection is identified by a turnout labelled `W` and a
number, at a kilometric point, with its turnout rate beside it: `W31 1:9`, `W57 1:8`. They land in
`catenary_asset_switch`, one row per turnout — **not** a new `CatenaryAssetType`: a turnout is part
of the insulator, not something an order is opened on.

The rate travels as `turnoutDenominator` (the `9` of `1:9`) and not as text: the numerator is always
1, and the integer can be ordered and compared. `turnoutRate` also arrives in the payload and is
recomputed here rather than stored.

`enabled` arrives per turnout and **is stored**. A turnout taken out of service still travels in the
event (the source collection filters soft-deleted rows, not disabled ones), and dropping it here
would tell a crew the turnout does not exist instead of that it cannot be used; the shift report
prints it as `W31 1:9 (out of service)`. With no `enabled` key the turnout counts as enabled, like
the asset's own flag.

`start_kp`/`end_kp` are the **minimum and maximum** of the insulator's own kp and its turnouts',
sorted, so `chk_catenary_asset_kp_range` can never fire and the insulator shows up in `?kpFrom/kpTo`
and in the preventive task generation of a track section.

The block is replaced whole after an applied upsert: the emitter always sends the complete list, so
what arrives is the final state. Two things follow from the order of operations, and both are
tested in `MasterDataAssetSyncDataJpaTest`:

- **A late event does not touch the switches.** The upsert returns 0 rows and the handler returns
  before reaching them, so a stale delivery cannot undo what a newer one applied.
- **Only the section-insulator handler may clear them** (`ownsSwitches()`): a handler that knows
  nothing about turnouts cannot delete the ones that are there, while an empty list from the one
  that does mean the insulator has none left.

## Published events

Everything this service tells the outside world goes through **one door**,
`DomainEventPublisher.publish(DomainEvent)` (`application/service`), called **inside the business
transaction** that makes the change. The implementation (`infrastructure/messaging/outbox`,
`OutboxDomainEventPublisher`) writes the event into `outbox_message` (`V13`) in that same
transaction, and a relay publishes it afterwards to RabbitMQ, waiting for the broker's confirm: the
event exists if and only if the change it tells was committed, and it survives a broker outage.
With `app.rabbitmq.enabled=false` the publisher is a `NoOpDomainEventPublisher` and nothing is
written (the tests, and an environment without a broker).

### Topology

| | Value |
|---|---|
| Exchange (own) | `mto.maintenance.exchange` (topic, durable; `app.rabbitmq.events.exchange`) |
| Routing key | `mto.maintenance.<entity>.<event>` (`MaintenanceRabbitMqNames`), e.g. `mto.maintenance.order.status-changed` |
| Queue | **none here**: a queue belongs to whoever consumes it. `mto-notification` declares `mto.notification.maintenance.queue` bound to `mto.maintenance.#` |
| AMQP `message_id` | the id of the `outbox_message` row |
| Headers | `eventType`, `aggregateType` (the entity), `aggregateId`, `sequenceNumber` (global, increasing), `messageSignature`, `messageSignatureAlgorithm`, `traceparent`/`tracestate` |

The signature is computed over the bytes sent with `app.messaging.signature.secret` — **the same
value** as in `mto-configuration` and `mto-notification` (HMAC-SHA256 with a secret, plain SHA-256
without), by `MessagePayloadSignature`, the counterpart of the verifier used on the consuming side.

### Envelope

The same `AsynchronousMessage` as `mto-configuration`, so every source is read alike, with the two
keys the new producers add:

```json
{
  "operationId": "a0000000-0000-4000-8000-000000000002",
  "referenceId": "order-40000000-0000-4000-8000-000000000001",
  "origin": "mto-maintenance",
  "creationDate": "2026-09-29T09:00:00Z",
  "eventType": "MAINTENANCE_ORDER_STATUS_CHANGED",
  "data": {
    "entityName": "order",
    "entityId": "40000000-0000-4000-8000-000000000001",
    "eventName": "status-changed",
    "values": { "code": "MO-000123", "from": "PLANNED", "to": "ASSIGNED", "assignedUser": "mantenimiento.tecnico", "...": "..." }
  },
  "messageHash": "…",
  "actor": { "id": "6f1b1c8e-0000-4000-8000-000000000032", "username": "mantenimiento.responsable", "kind": "PERSON" },
  "correlationId": "8c3b8c1a-1111-4222-8333-444444444444"
}
```

- `data` is a `DomainEvent` (`entityName`, `entityId`, `eventName`, `values`), not a
  `MasterDataChangedEvent`: `status-changed` or `rejected` are not a create, an update or a delete,
  and the event name travels as text so a consumer decides what to do with what it does not know.
- `eventType` is `MAINTENANCE_<ENTITY>_<EVENT>`; the consumer derives the activity type from the
  routing key (`maintenance.<entity>.<event>`).
- `messageHash` is SHA-256 over the JSON of the seven original keys only; `actor` and
  `correlationId` are outside it.
- `actor` is who asked for the operation, classified by this service because only it has the token
  in hand: `PERSON` (the `preferred_username` and `sub` of the token), `SERVICE` (a Keycloak service
  account, named `service-account-<client>`) or `SYSTEM` (nobody authenticated: a scheduled job, a
  master-data message). Read in the thread that writes the outbox (`MessageContextResolver`), the
  only one that still has it.
- `correlationId` is the `X-Correlation-Id` header of the request in course (trimmed, printable
  ASCII, at most 200 characters; anything else counts as absent), else the id of the master-data
  message being processed (`MessagingAuditContext`), else `null` — the same value the Envers
  revision stores, so the activity registered by `mto-notification` joins the revision and the inbox
  row by one key.
- **Keys are only added.** Renaming or removing a key, an entity name, an event name or a routing
  key breaks `mto-notification`; a new key changes the example below in the same commit. `values`
  never carries anything that smells like a secret: `DomainEvent` rejects such keys (`password`,
  `secret`, `token`, `credential`, `otp`, `apiKey`...) at any depth before the event reaches the
  outbox. Null values do travel ("no team" is information).

### Events

One per hook; the values are built in one place, `MaintenanceEvents` (`application/service/impl`),
and every event has a real JSON example in `docs/messaging/examples/` that
`MessagingContractExamplesTest` builds with the real factory and compares with the file
(`MESSAGING_EXAMPLES_WRITE=true ./mvnw test -Dtest=MessagingContractExamplesTest` regenerates them).
`mto-notification` copies those files as its contract fixtures.

| Event (`entity.event`) | When | `values` (besides the common ones) |
|---|---|---|
| `order.created` | `StatusHistoryService.recordOrderChange` with no previous status: every `POST /orders`, including the corrective one an inspection generates | order (`code`, `title`, `type`, `priority`, `status`, asset, location, `plannedDate`, team, `assignedUser`, `originInspectionId`, `originDefectId`, `stockProjectId`) + `comment` |
| `order.status-changed` | every transition (`plan`, `assign`, `start`, `complete`, `cancel`) | order + `from`, `to`, `comment`; with `to = ASSIGNED`, `assignedUser` is whom to notify |
| `order.reassigned` | `assign` on an order `IN_PROGRESS` (team or person change without a transition) | order + `comment` |
| `defect.created` | `recordDefectChange` with no previous status: `POST /defects`, an inline defect at task completion, the defect an inspection generates | defect (`code`, `severity`, `status`, `description`, asset, location, `inspectionCode`, `orderCode`, `foundInTaskId`, `resolvedInShiftCode`, `detectedAt`, `repairPlannedDate`, `resolvedAt`) + `comment` |
| `defect.status-changed` | every transition of a defect (`link`, `resolve`, `close`, `discard`, reopened by a cancelled order...) | defect + `from`, `to`, `comment`. A re-link without a state change stays in the history only |
| `inspection.created` | `POST /inspections` | inspection (`code`, `result`, `inspectionKind`, `inspectionDate`, `inspector`, asset, location, `kp`, `originOrderCode`, `shiftCode`) + `itemCount` |
| `inspection.item-failed` | a checklist item that becomes `DEFECT` (`PATCH/PUT .../items/{id}`); staying `DEFECT` does not fail again | inspection + `itemId`, `itemCode`, `itemLabel`, `itemResult`, `measuredValue`, `valueAfterAdjustment`, `unit`, `minValue`, `maxValue`, `notes` |
| `inspection.defect-created` | `POST /inspections/{id}/defect` (the defect also publishes its own `defect.created`) | inspection + `defectId`, `defectCode`, `severity`, `description` |
| `inspection.corrective-order-created` | `POST /inspections/{id}/corrective-order` (the order also publishes its own `order.created`) | inspection + `orderId`, `orderCode`, `orderType`, `priority`, `defectId`, `defectCode` |
| `shift.started` / `shift.closed` | `POST /shifts/{id}/start` and `/close` | shift (`code`, `status`, `shiftDate`, `possessionType`, team, `baseName`, `vehicle`, `trackIds`, `executionPackageId`, kp range, planned and actual instants, `voltageCutoffAt`, `netWorkMinutes`) |
| `material.rejected` | a material line `mto-stock` said no to (`REJECTED`), on any step | line (`orderCode`, `orderStatus`, `taskId`, `materialId`, `materialCode`, `materialDescription`, `warehouseId`, quantities, `unit`, `stockReservationId`, `stockSyncStatus`, `request`) + `step`, `reason`, `stockErrorCode` (`STK-001` is no stock), `stockHttpStatus` |
| `material.in-doubt` | a reservation or an output sent to `mto-stock` that got no answer (`FAILED` with `stockRequestInDoubt`) | line + `step`, `reason`; `request` says which one (`RESERVATION`/`OUTPUT`) |
| `material.failed` | `mto-stock` did not answer and nothing is in doubt (the request never left, e.g. resolving the project) | line + `step`, `reason` |
| `asset.disabled` | `DELETE /assets/{id}` or `PUT enabled=false`, the first time it is disabled here | asset (`code`, `name`, `type`, location, `sourceService`, `sourceEntityId`, `enabledAtSource`, `disabledLocally`, `preventiveIntervalDays`) |
| `preventive.due-soon` | the daily check (below) | `date`, `horizonDays`, `count`, `overdueCount`, `sampleSize`, `assets[]` (`assetId`, `assetCode`, `assetName`, `assetType`, `trackId`, `executionPackageId`, `lastPreventiveCompletedAt`, `dueAt`, `overdue`) |

The aggregate of the outbox is the entity (`order`-`<id>`), so the relay's strict ordering per
aggregate keeps `created` before `status-changed` of the same order even if the first one fails and
is retried. The `entityId` is the UUID of the entity (the date, for `preventive`).

### The daily preventive check

`PreventiveDueSoonService` (`app.events.preventive-due-soon.*`, cron `0 7 6 * * *` by default,
horizon 7 days) runs once a day in one transaction: a PostgreSQL advisory lock bound to the
transaction (`pg_try_advisory_xact_lock`) so that with several instances only one works; the
assets `enabled` whose preventive is due before `now + horizon` or that were never checked (the same
rule as the API filter `preventiveDueBefore`, `preventive_due_at` in `V1`); and **one** event with
the count, the overdue count and a sample of the earliest ones. Its `operationId` is derived from
the date (`UUID.nameUUIDFromBytes("preventive-due-soon:<yyyy-MM-dd>")`), so a second pass the same
day — a restart, an instance that got the lock later — is a duplicate for the consumer's inbox.
Nothing due, nothing published. Off in the tests (`BusinessLayerTest` calls it) and whenever the
broker is off.

### The outbox

A copy of `core/outbox` of `mto-configuration`, wired as `@Bean`s in `configuration/outbox`
(all gone with `app.rabbitmq.enabled=false`):

- `OutboxService.save` writes the row in the business transaction and, on commit, wakes the relay
  (`immediate-dispatch`); the scheduled poll (`publisher-fixed-delay`, 5 s) is the safety net.
- `OutboxRabbitPublisher` sends with `mandatory=true` and **waits for the publisher confirm**
  (`spring.rabbitmq.publisher-confirm-type=correlated`, `publisher-returns=true`): a nack, an
  unroutable return or a timeout is a failure, and without confirms the relay refuses to start.
- Failures retry with exponential backoff and jitter (`max-attempts` 20 covers more than an hour of
  broker outage), then the row is `FAILED` and stays for someone to look at; `POST /actuator/outbox`
  (`ops-write`) redrives them, `GET /actuator/outbox` (`ops-metrics`) shows the counts.
  `app.outbox.enabled=false` stops the relay only (the outbox keeps writing).
- Metrics `outbox.messages.pending`, `outbox.messages.in.progress`, `outbox.messages.failed`,
  `outbox.pending.oldest.age.seconds` (the useful alarm: if it ages, the relay is broken) and
  `outbox.publish.total{result=success|failure}`.
- Published rows are purged after `app.outbox.purge.retention` (7 days), in batches.
- The trace context of the operation is stored in the row and restored when publishing, so the
  publication span hangs from the request that caused it and not from the scheduler.

The tests of the relay (`OutboxRelayDataJpaTest`, against a real PostgreSQL), the wiring
(`OutboxWiringTest`) and the pieces (`OutboxRabbitPublisherTest`...) are the ones of
`mto-configuration`, on the same copy.

## Known gaps

- An asset changed by an event leaves no Envers revision (native SQL). `catenary_asset_aud`
  covers the REST path only.
- Orders already created keep the location they copied from the asset; a later kp change of the
  profile is not propagated.
- An asset disabled by a master-data event (`deactivateFromMasterData`, native SQL) publishes no
  `asset.disabled`: the source already tells it, and `mto-notification` hears it from
  `mto-configuration`. Only a disable decided here is an event of this service.
- The state of a task (`maintenance_task`) publishes nothing: the shift report and the order's
  transitions already tell what a crew did.
