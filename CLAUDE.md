# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

MTO Maintenance: a Spring Boot 4 / Java 25 API for the maintenance of railway catenary (overhead
contact system). It plans and records **preventive** maintenance profile by profile (a profile is a
pole with its cantilever and wires), **corrective** work on defects, **inspections** with checklists,
night **shifts** with their daily report and the **materials** each job consumes, reserved and
consumed in the sibling warehouse service `mto-stock`.

Read `docs/` in order for the full domain and architecture context: `00-project-overview.md`,
`01-architecture.md`, `02-domain-model.md`, `03-database.md`, `04-rest-api.md`,
`05-development-roadmap.md`, `06-messaging.md`, `07-auditing.md`. Those documents plus this file are
the source of truth; `03-database.md` documents the schema and should be checked before changing
persistence code.

⚠️ `mto-stock`, `mto-configuration` and `mto-resources` are **independent sibling repositories**, not
modules of this one. Infrastructure master data (stations, tracks, profiles, disconnectors, section
insulators) belongs to `mto-configuration` and arrives here as events; the materials catalogue and the
stock belong to `mto-stock` and are reached through its REST API; the people, the organization and
the fleet belong to `mto-resources` and arrive here as events. **Nothing of them is re-modelled
here**: `catenary_asset` keeps only the snapshot a maintenance job needs (code, kp, track, station,
sectioning), `crew_person`/`fleet_vehicle`/`maintenance_instrument`/a synchronized `maintenance_team`
only what a shift needs (with the qualifications, documents and calibration dates its expiry rules
read, `V23`, and when each person and vehicle is not available and the capabilities of each model,
`V24`), and every material line stores the `mto-stock` ids.

⚠️ The local infrastructure (PostgreSQL, RabbitMQ, Keycloak, the trace collector, and `mto-stock`
itself) is brought up by `mto-platform`. `compose.yaml` here holds **only the application**.

## Commands

```bash
./mvnw compile
./mvnw test                                          # full suite (Docker for Testcontainers, or TEST_DATABASE_* pointing at a PostgreSQL)
./mvnw test -Dtest=BusinessLayerTest                  # one class
./mvnw test -Dtest=BusinessLayerTest#planningReservesEveryMaterialInStock
./mvnw spring-boot:run                               # SPRING_PROFILES_ACTIVE=dev + DATABASE_* (see README)
```

Local environment:

```bash
cd ../mto-platform && docker compose --profile all up -d && ./keycloak/apply-partials.sh
```

- Flyway runs on startup against `src/main/resources/db/migration`; Hibernate is `ddl-auto: validate`
  in every profile, so schema changes always go through a new migration. A migration that changes a
  column on one of the audited tables must change its `<table>_aud` twin in the same migration.
- Swagger UI: `http://localhost:8083/swagger-ui.html` (`dev` profile); OpenAPI JSON: `/v3/api-docs`.
- Virtual threads are on (`SPRING_THREADS_VIRTUAL_ENABLED`, see `docs/01-architecture.md`, Threads):
  the limit is the connection pool (`DATABASE_POOL_MAX_SIZE`, 10), not the threads; `outbox-dispatch`
  stays one platform thread on purpose.
- Profiles: `dev`, `test`, `prod`, selected via `SPRING_PROFILES_ACTIVE`. Local port is **8083**
  (`dev`); the container listens on 8080 and `mto-platform` publishes it on 8083.

## Architecture

Three layers under `com.alejandro.mtomaintenance`, the same split as `mto-stock`:

- `domain/model` — framework-free rules: `OrderStateMachine`, `DefectStateMachine`,
  `ShiftStateMachine`, `KilometricRange`, `Quantity`, `WorkloadEstimate`, `DomainValidations`,
  `CrewComposition` (the copy of `mto-resources`' crew rule; returns the reason, does not throw),
  `ShiftQualifications` (nothing expired at the shift's date and a different qualified driver per
  self-propelled vehicle, a bipartite matching; returns the reason too), `ShiftAvailability` (nobody and
  no vehicle unavailable at the shift's date, and the minimum rest between two nights; the reason too).
  An `IllegalArgumentException` from here is turned into a 400 by `DomainGuard` in the services.
- `application` — `dto` (one package per resource), `service` + `service.impl` (package-private
  impls behind public interfaces; `MaintenanceLookups`, `ShiftRules`, `ShiftCrew`, `ChecklistRules`,
  `MaintenanceScope` (the execution packages whoever writes may write in, 403 `SCP-403`),
  `MaterialStockSynchronizer` (with `StockLine`, the detached copy a line's stock conversation runs
  on) and `MaterialLineFactory` are shared helpers; `BudgetedOperations` runs the background
  operations; the `IncomingEvent*` pipeline, `IdempotentIncomingEventProcessor` →
  `DispatchingIncomingEventHandler` → `ResourcesPersonHandler`/`ResourcesVehicleHandler`/
  `ResourcesTeamHandler`/`ResourcesInstrumentHandler`, applies the events of `mto-resources`), `mapper` (MapStruct,
  **entity → response only**: entities are built in the services with their Lombok builders because
  MapStruct cannot call the protected constructors), `exception`.
- `infrastructure` — `persistence.entity` (JPA), `persistence.repository` (Spring Data + native
  upserts), `persistence.specification`, `persistence.audit` (Envers, `BackgroundOperationContext`),
  `web` (`PreferHeader`, `OperationResponses`), `web.controller`, `web.exception`
  (`ApiOperationErrorTranslator` too), `messaging.rabbitmq` (the two consumers,
  `MasterDataEventConsumer` and `ResourcesEventConsumer`, `ResourcesRabbitMqNames` and
  `MaintenanceRabbitMqNames`),
  `messaging.outbox` (the outbox of the own events: a copy of `mto-configuration`'s `core/outbox`
  plus the envelope factory, `MessageContextResolver` and `OutboxDomainEventPublisher`), `stock`
  (the `RestClientStockClient`), `export` (the POI and OpenPDF writers).
- `configuration` — security (Keycloak resource server), `rabbitmq` (both channels,
  `ResourcesRabbitProperties`), `messaging` (signature and signer), `outbox` (every outbox piece as a
  `@Bean`, gone with `app.rabbitmq.enabled=false`), `events` (the daily `PreventiveDueSoonService`
  scheduler), `async` (`AsyncConfiguration`: the `applicationTaskExecutor`, a virtual thread per task
  with the caller's `SecurityContext` and MDC through `MdcTaskDecorator`; `OperationsProperties`,
  `app.operations.*`; `OperationsSchedulingConfiguration`, the heartbeat, abandon check and purge),
  `reports` (`ReportProperties`, the report time zone), `stock` (`StockProperties`,
  `StockClientConfiguration`, `StockSyncProperties` for `app.stock.sync.*`), JPA auditing, OpenAPI.

### Domain in one paragraph

A `CatenaryAsset` is what maintenance is done on: a `TRACK_SECTION` (created through the API, a kp
range on a track, `MAIN` or `DIVERTED`), or a `PROFILE`, `DISCONNECTOR` or `SECTION_INSULATOR`
synchronized from `mto-configuration` (`source_service` + `source_entity_id`, never created through
the API, only `description`/`enabled`/`preventiveIntervalDays` editable; the rest is accepted only with
the value it already has). `enabled` is the sum of two
voices: `enabledAtSource` (only the events write it) and `disabledLocally` (only `DELETE` and `PUT`
write it; no event undoes it), kept together by a `CHECK`; `PUT enabled=false` needs
`maintenance-delete` like the `DELETE`. A disconnector on a pole takes its package from its
profile, and its track too unless its event brings its own (`ownTrack`, `V14`: in a bundle of tracks
it may hang from the pole of one track and switch another), and an insulator or a disconnector
without a pole (which brings its own kp and track since `V26` of `mto-configuration`) its package
from the profiles of its track, because the source publishes no package for them
(`docs/06-messaging.md`). A disconnector that puts
two tracks in parallel also brings the other one (`connectedTrackId`, `V27` of `mto-configuration`),
in the same column as an insulator's. A disconnector may belong to no station (`station: null`, on open
track, in a neutral section or a substation): its location never comes from the station. A `SECTION_INSULATOR`
carries besides its two tracks, its `installationType` (`TRACK_CONNECTION` / `IN_TRACK`) and its
turnouts (`catenary_asset_switch`: `W31` at its kp with its `1:9` rate) — a turnout is part of the
insulator, not an asset of its own, so there is no new `CatenaryAssetType`. A `MaintenanceOrder`
(`PREVENTIVE`, `CORRECTIVE`, `INSPECTION`, `URGENT`) targets one asset and moves
`DRAFT → PLANNED → ASSIGNED → IN_PROGRESS → COMPLETED` (or `CANCELLED`); `URGENT` is the only type
that jumps `DRAFT → IN_PROGRESS` and is born `CRITICAL`. A preventive order on a `TRACK_SECTION`
generates one `MaintenanceTask` per enabled `PROFILE` in the range (`POST /orders/{id}/tasks/generate`;
with `assetTypes` also one per `SECTION_INSULATOR` or `DISCONNECTOR` touching it, its own track's or one connecting to it),
and with `selections` any order mixes profiles, insulators and disconnectors of other sections, other
tracks or the whole execution package: **an order is of one package** (its stock project and the
monthly report are) and may cover several of its tracks; what is of another package goes in another
order. `PreventiveTaskSelector` decides what fits and what is skipped (already in the order, of
another package, open in another open preventive order, without a task type), `dryRun` previews it,
and generations of one package queue twice: in one instance on a `ReentrantLock` per scope
(`GenerationLocks`), waited for before the transaction opens, so without a connection; inside the
transaction on the PostgreSQL advisory locks, which is what holds between instances: one shared by
every generation and taken exclusively by an order without a package (which may take assets of any),
then the package's (`pg_advisory_xact_lock`, the one a task added by hand takes too), then one per
asset without a package, after which the selection is repeated. A task added by hand follows the
generation's rules too: no asset of another package, and in a preventive no asset with an open task
in another open preventive order (400); an order with tasks of its package does not move to another.
Every advisory key is the 64-bit `AdvisoryLockKeys.of(name)` (SHA-256), never a 32-bit `hashCode`; with

`Prefer: respond-async` one that does not finish in time answers 202 with a background operation
(`BudgetedOperations`, `GET /operations/{id}`, a deployment-wide cap, 429 `OPS-429`). Each task carries the
`MaintenanceTaskType`s performed (the `RG-xx`/`RP-xx` catalogue of the OCS plan, with standard
minutes that feed `WorkloadEstimator`; by default groups 1, 2 and 4 on a profile, `RG-03` and
`RP-02` on an insulator plus `RG-04` and `RP-03` when it has turnouts (`switches`), `RP-07` on a
disconnector plus `RP-06` when its `driveType` (`V16`) is `MOTOR` and `RG-06` when its
`disconnectorFunction` (`V17`) is `SurgeA`, a surge arrester). The only thing of a task type that changes
through the API is the equipment it requires (`requiredEquipment`, `V24`: `INSULATED_PLATFORM`,
`EARTHING`; `PATCH /task-types/{code}` with `maintenance-supervise`, nothing seeded): such a task is
assigned, started and completed only in a shift that carries it (a vehicle with that capability, or an
active `EARTHING_POLE` for earthing; `ShiftRules.requireEquipment`, 409 `SHF-001`), and a shift change
that takes it away from an assigned open task is refused.
A `MaintenanceTeam` is local (edited here, `A`/`B` seeded) or synchronized from `mto-resources`
(`V19`: leader, supervisor, linemen, packages and default vehicle; no base), which adopts the local
team of its code on its first event; from then on it is read-only here (`PUT` is 409 `TEA-001`).
An order of a synchronized team left without a user goes to the username of its leader
(`fillLeader`, at creation, `assign` and a team change; an explicit user wins).
Tasks are executed inside a `MaintenanceShift` (team, night window, possession `PARTIAL`/`FULL`, one
or more tracks) with its crew and vehicles of `mto-resources` (`V20`, copied from a synchronized team
when not given). `ShiftCrew` keeps them in rule, 409 `SHF-001`: at most 6 linemen, the leader not
among them and not a `WORKER`, everyone active, every vehicle active and available, one that neither
moves by itself nor is portable with a towing one, the crew within the seats (only when every
self-propelled vehicle declares them), no vehicle, leader, lineman or instrument in two open shifts
of one date (advisory locks), and nothing expired at the `shiftDate` (`ShiftQualifications`, `V23`: a
qualification of the leader, the supervisor or a lineman, a document of a vehicle, the calibration of
an instrument, or an instrument retired; `expiresOn < shiftDate`), nobody and no vehicle unavailable at
the `shiftDate` (`ShiftAvailability`, `V24`: the absences of a person and the repairs that immobilise a
vehicle, published by `mto-resources` as `unavailable [{from, to}]` without their reason), at least
`app.shifts.minimum-rest` (12 h) between two nights of the leader or a lineman (the shift before and
after, actual hours else planned; under the same person locks); to start and while `IN_PROGRESS`, a
leader and 2 to 6 linemen (`CrewComposition`), only on a shift with a crew (`hasCrew()`), and a
different person of the crew holding the qualification its model requires for each self-propelled
vehicle (a matching, not a loop). A shift also carries its instruments of `mto-resources`
(`instrumentIds`, a set; unknown is 404 `MSI-404`). Closing says where each vehicle was
left (`parkings`: a site, a track and kp, or a text; a rail-only one never by text alone), and
starting, closing and cancelling publish `shift.started`/`closed`/`cancelled`, which `mto-resources`
reads to move the vehicles. Writing may be limited by execution package (`MaintenanceScope`, `V24`):
the person of the token in `crew_person` (by username) writes only in the packages of the synchronized
teams they lead, supervise, chief-supervise or ride in; no record, the categories `CHIEF_MAINTENANCE`/
`CHIEF_CONSTRUCTION`/`PROJECT_MANAGER` or `app.scope.enabled=false` has no limit, and reading never
has. Every write of orders (and their tasks and material lines), shifts, inspections, defects and
assets checks it first: 403 `SCP-403`. A task can
only be completed with the order and a shift covering its track both `IN_PROGRESS`, a task type that requires
full possession cannot be done in a `PARTIAL` shift, and a `DIVERTED` section only accepts `FULL`
shifts. Completing a task can record `CatenaryDefect`s inline (`RESOLVED` in the shift if the work
was finished, `OPEN` with `repairPlannedDate` if not). A `MaintenanceInspection` copies the active
`InspectionTemplate` of the asset type into its items (recorded in a shift, one `IN_PROGRESS` or
`CLOSED` on its asset's track or the connected one, it appears in that shift's report), and can create a defect and a corrective
order (both idempotent). Every material line (`MaintenanceMaterialUsage`) is reserved in
`mto-stock` when the order is planned, consumed when it is completed and released when it is
cancelled, all the lines of the transition at once; a failure leaves the line `FAILED` (stock did not
answer) or `REJECTED` (it said no, with its reason) and `POST .../materials/{id}/sync` retries. A line
registered by mistake is removed with `DELETE .../materials/{id}` (a physical delete that Envers keeps
as a DELETED revision), releasing its reservation first; it is never cancelled.

### Persistence rules

- UUID ids, `AuditableEntity` (`created_at`/`updated_at`/`created_by`/`updated_by`), snake_case
  tables, PostgreSQL enums for every status/type, `numeric(12,3)` for kp, `numeric(19,6)` for
  quantities, `timestamptz` for instants.
- Ids of `mto-configuration` entities are `bigint` columns (`track_id`, `station_id`,
  `execution_package_id`, `parking_track_id`); ids of `mto-stock` entities are `uuid` columns
  (`material_id`, `warehouse_id`, `stock_reservation_id`, `stock_project_id`); ids of `mto-resources`
  are `uuid` too, the primary key of its snapshots (`crew_person`, `fleet_vehicle`, `maintenance_instrument`), a team's
  `source_entity_id`, and `default_vehicle_id`/`parking_site_id` without FK. Its enums are `varchar`.
- Codes come from sequences (`MO-000001`, `SH-`, `INS-`, `DEF-`), never from `MAX + 1`.
- `CatenaryAssetRepository.upsertFromMasterData`/`deactivateFromMasterData` are native SQL with the
  sequence watermark inside the `WHERE` (never read-then-write), which is why master-data changes
  leave no Envers revision. They write `enabled_at_source` and recompute `enabled`, never
  `disabled_locally`; `deactivateByTrack` advances the watermark of what it disables (the sequence of
  `mto-configuration` is global). The same goes for `crew_person`, `fleet_vehicle`,
  `maintenance_instrument` and the synchronized team (`upsertFromResources`, watermark = the
  `sequenceNumber` of the `mto-resources` outbox, not its `version`; the `jsonb` qualifications and
  documents of `V23` and the `unavailable` periods and capabilities of `V24` go in the same statement,
  and an event without the key keeps the stored ones),
  and for `background_operation` (`V21`), whose cap and turn per scope are decided by row count inside
  the writing statement.
- `maintenance_status_history` is append-only and not audited; `order_id`/`defect_id` with a
  `CHECK` that exactly one is set.
- Every entity edited through the API extends `VersionedEntity` (`@Version`, `V11`): its response
  carries `version`, its `PUT`/`PATCH` accept it (a stale one is 409 `CON-001`, checked by hand with
  `StaleVersionException.check` because `@Version` does not see it within one transaction), and any
  native SQL writing those tables must bump it. `PUT` never empties a field; `PATCH` with
  `application/merge-patch+json` does (`MergePatch`, read by `MergePatchArgumentResolver`), and each
  service lists what it may empty (`PatchRules.requireClearable`). `update` is `patch` with
  `MergePatch.of(request)`: there is one code path per resource.

### Messaging

Consumer of the master-data channel of `mto-configuration` (`mto.master-data.exchange`, own queue
`mto.maintenance.master-data.queue` with DLX/DLQ; this queue and the resources one below are `classic`
by default and `quorum` with `app.rabbitmq.queues.type`, as on OpenShift, with `x-delivery-limit` 5 —
`ConsumerQueueProperties`, which declares them exactly as before when `classic`, because a new argument
on an existing queue is refused by the broker) through the same idempotent **inbox** as
`mto-stock` (`inbox_message`, unique `(message_id, source_service)`). Four `MasterDataEntityHandler`s:
`profile`, `disconnector`, `section-insulator` (upsert/deactivate assets) and `track` (`DELETED`
only, deactivates every asset on the track). The other four entity names are logged and ignored.
The contract is owned by `mto-configuration`; see `docs/06-messaging.md` before touching
`application/dto/messaging`.

Consumer of `mto-resources` too (`mto.resources.exchange`, own queue
`mto.maintenance.resources.queue` bound to `mto.resources.{person,team,vehicle,instrument}.*`, with DLX/DLQ,
`app.rabbitmq.resources.*`) through the same inbox (`source_service = mto-resources`):
`ResourcesEventConsumer` checks the signature, the `origin` and that `data` has `entityName` and
`eventName`, and `DispatchingIncomingEventHandler` routes by source and entity to the
`IncomingEventHandler`s `person` (with its `qualifications` and `unavailable`), `vehicle` (with its
`documents`, `type.requiredDriverQualification`, `type.capabilities` and `unavailable`), `team` (with its
`chiefSupervisor`; only `MAINTENANCE` teams; the people a team carries are
inserted with `ON CONFLICT DO NOTHING` until their own event arrives) and `instrument` (`V23`). Its
expiry warnings (`qualification.*`, `vehicle-document.*`, `calibration.*`) are not bound here: the
shift reads the dates from the snapshot. This queue must
exist before `mto-resources` publishes (`mandatory=true` there): deploy this service first.

Producer of its own events for `mto-notification` (`mto.maintenance.exchange`, routing key
`mto.maintenance.<entity>.<event>`, no queue here). **`DomainEventPublisher.publish` is the only
door, and it is called inside the business transaction**: the event goes to `outbox_message`
(`V13`) with the change and the relay publishes it afterwards with publisher confirms
(`OutboxRabbitPublisher` refuses to start without them). The hooks: `StatusHistoryServiceImpl`
(`order.created`/`status-changed`/`reassigned`, `defect.created`/`status-changed`: every order and
defect transition passes through it), `MaintenanceShiftServiceImpl.start/close/cancel`
(`shift.started`/`closed`/`cancelled`, with the crew, the vehicles and where each was left, and at the
end the instruments' ids and codes: also
read by `mto-resources`, through its own queue on `mto.maintenance.shift.*`),
`MaterialStockSynchronizer.record` (`material.rejected`/`in-doubt`/`failed`, only when the line's
state changes: the 5-minute retry must not publish one per round),
`CatenaryAssetServiceImpl.disable/patch` (`asset.disabled`, once), `MaintenanceInspectionServiceImpl`
(`inspection.created`/`item-failed`/`defect-created`/`corrective-order-created`) and the daily
`PreventiveDueSoonService` (`preventive.due-soon`, advisory lock, `operationId` from the date). The
names and `values` of every event live in `MaintenanceEvents` and nowhere else; the envelope is the
`AsynchronousMessage` of `mto-configuration` plus `actor` (`PERSON`/`SERVICE`/`SYSTEM`, from the
token) and `correlationId` (`X-Correlation-Id` of the request, else the master-data message id, else
the one a background operation copied from its request, as Envers stores it), both read by
`MessageContextResolver` when the event is created. **Keys are only
added**; a new key or event changes its example in `docs/messaging/examples/` in the same commit
(`MessagingContractExamplesTest` compares them with the real factory;
`MESSAGING_EXAMPLES_WRITE=true` regenerates them). `DomainEvent` rejects any key that smells like a
secret. With `app.rabbitmq.enabled=false` the publisher is the `NoOpDomainEventPublisher` and no
outbox bean exists.

### Stock integration

`StockClient` (interface in `application/service`) is the only door to `mto-stock`.
`RestClientStockClient` authenticates with the Keycloak service account `mto-maintenance-svc`
(`client_credentials`, audience `mto-stock-api`) and runs every call inside the Spring Cloud
circuit breaker `stock` (Resilience4j, tuned with `app.stock.circuit-breaker.*`; with virtual threads
on, `StockCircuitBreakerExecutorConfiguration` gives each call a virtual thread, because the breaker
runs it on its own executor, a platform pool the flag does not reach). Its token manager
is built by `StockClientConfiguration` itself, never taken from the context: the one Spring Security
registers by default needs an HTTP request in course, and these calls run on the breaker's thread or
the scheduled retry (`servletRequest cannot be null`, every line `FAILED`). A failure is one
of two exceptions. `StockRejectedException` means stock answered no: a 4xx other than
401/403/408/429, carrying stock's `errorCode` (409 `STK-001` is no stock). `RestClientStockClient.isRejection`
is also the predicate the breaker ignores, so rejections never open it. Everything else (network,
timeout, 5xx, open circuit, the service account refused) is `StockUnavailableException`.
`MaterialStockSynchronizer` turns them into a `REJECTED` or `FAILED` line instead of failing the
order transition, and before consuming or releasing a line that has a reservation it asks stock how
that reservation is (`findReservation`), so a retry never consumes or releases twice (the table is in
`docs/02-domain-model.md`). A reservation or an output whose answer was lost cannot be asked about, so
`reserve` and `output` carry an `Idempotency-Key` (`MaterialStockSynchronizer.idempotencyKey`: the line,
the request and, for `reserve`, the previous reservation — never the body), and stock returns what it
already did instead of doing it twice. The line marks the request in doubt before sending it
(`stockRequestInDoubt`, `V12`), and anything done next with it against stock — `sync`, `start`,
`complete`, `cancel`, removing it — first repeats that request with the same key and body
(`confirmReservation`, or `settle` for an output). Meanwhile what travels in it cannot change: the
line's planned and consumed quantities and the order's stock project answer 409 `MAT-001`. The body
must also come out the same: no timestamp, and the same notes whichever path reaches the direct
output. `StockSyncRetryService` retries every `FAILED` line every 5 minutes (`StockSyncRetryConfiguration`,
`app.stock.sync-retry.*`, off with stock off and in tests), stopping at the first one stock does not
answer and resuming after it: that settles a request in doubt while `mto-stock` still remembers its
key (30 days). With several instances one round runs at a time: it holds a PostgreSQL advisory lock
of **session** (`SessionAdvisoryLock`, one connection set aside outside any transaction, because each
line syncs in its own transaction and an `xact` lock would not outlive the first), and an instance
that does not get it skips the round. Completing an order rejected for such lines (`UnsyncedMaterialsException`)
and an explicit `sync` keep what stock already did (`noRollbackFor`). `NoOpStockClient` replaces it
with `app.stock.enabled=false`.

An order transition (`reserveAll` on plan and start, `consumeAll`, `releaseAll`) talks to stock for
all its lines at once, each on a virtual thread (at most `app.stock.sync.parallelism`, 4) under one
deadline (`app.stock.sync.deadline`, 10 s, counted from before the order's stock project is resolved, so
the transition stays below the gateway's 15 s read timeout). Each conversation runs on a `StockLine`, a copy that never
touches the entity or the database; the transaction thread applies it back and publishes, in line
order. A line not answered by the deadline is cut (`StockLine.cancel`: its thread sends nothing more
to stock) and left `FAILED` with what it had in doubt, the order goes on, and after commit
`DeferredStockSyncListener` (`StockLinesDeferred`) retries those lines at once through
`StockSyncRetryService.retryLines`, once their call in the air is over (30 s at most),
`app.stock.sync.immediate-retry`. The one-line paths (`syncNow`, `releaseNow`, `reserve(usage)`,
`consume`/`release` of one line) stay synchronous on the caller's thread.

### Report export

The three reports (`GET /shifts/{id}/report`, `/reports/progress`, `/reports/monthly`) take
`?format=json|xlsx|pdf`, `json` by default: same routes, same `MAINTENANCE_READ` rule, unchanged JSON
contract. Each report is turned into one format-neutral `ReportDocument`
(`application/dto/export`: header pairs, an optional table, totals, titled appendix tables — the
shift's inspections, its crew, its vehicles with where each was left and its instruments with their
calibration, each a sheet of its own in the .xlsx —, typed `ReportValue`s) by its
layout in `application/service/impl`, and `ReportExportServiceImpl` hands it to the `ReportExporter`
of the requested format. The exporters are `@Component`s indexed by format at startup — the same
registry-by-key as the master-data handlers, failing the startup on a duplicate or a missing one — so
POI and OpenPDF live only in `infrastructure/export` and a new format is one class plus one
`ReportFormat` constant, with no controller change. Headers are in English on purpose (the catalogue
and the enum values printed in the cells already are); see `ReportLayouts` for that. Instants are
printed in `app.reports.time-zone` (`Asia/Jerusalem` by default, `ReportProperties`), turned into dates
only by `ReportTime`, and `MaintenanceReportServiceImpl` cuts its months and inspection days in the
same zone: one property for both halves, so a row and the counter that counts it never fall on
different days. The JSON stays UTC. `docs/04-rest-api.md` documents the parameter, the file names
and the columns the PDF leaves out.

### Auditing

Envers on `CatenaryAsset`, `MaintenanceOrder`, `MaintenanceTask` (+ its task-type join), `MaintenanceShift` (+ its track, blocking-disconnector, lineman and instrument collections),
`MaintenanceShiftVehicle` (a vehicle of a shift and where it was left, `V20`), `MaintenanceTaskCheckItem`,
`MaintenanceInspection`, `MaintenanceInspectionItem`, `CatenaryDefect`, `MaintenanceMaterialUsage`.
Not audited on purpose: `MaintenanceStatusHistory`
(append-only), `InboxMessage` and `CatenaryAssetSwitch` (written only from master data), `CrewPerson`,
`FleetVehicle` and `MaintenanceInstrument` (snapshots of `mto-resources`, native SQL only), `BackgroundOperation` and
`OutboxMessage` (their own history), `MaintenanceTeam`, `MaintenanceTaskType`,
`InspectionTemplate`/`Item` (catalogues). `JpaEntityModelTest` guards the split. History at
`GET /<resource>/{id}/revisions`. The `asset` of an order, a task, an inspection and a defect is
`@Audited(targetAuditMode = NOT_AUDITED)`, like a shift's disconnectors: an asset that only arrived
by master data has no revision (native SQL), so a revision reads it as it is now instead of looking
for it in `catenary_asset_aud` and failing (`EnversAuditDataJpaTest`). The leader, supervisor,
linemen and instruments of a shift and the vehicle of each `MaintenanceShiftVehicle` are `NOT_AUDITED`
targets for the same reason (`JpaEntityModelTest` guards them). A shift's vehicles are `@OrderBy("id ASC")`, never by an audit column: Envers applies the
`@OrderBy` to `maintenance_shift_vehicle_aud`, which has none; `vehiclesByCode()` orders what is shown.
A background operation keeps its request's source, URI and correlation in its revisions
(`BackgroundOperationContext`, read by `AuditRevisionListener`).

### Testing

- `PostgreSQLTestContainer` (in `support/`) starts `postgres:17-alpine` with Testcontainers, or uses
  `TEST_DATABASE_URL`/`TEST_DATABASE_USERNAME`/`TEST_DATABASE_PASSWORD` when set (no Docker needed).
  It caps the Hikari pool at 5 connections: every context Spring caches keeps its pool open, and with
  the production 10 a dozen contexts pass PostgreSQL's 100 `max_connections`.
- `MtoMaintenanceApplicationTests` boots the whole context against a real PostgreSQL and asserts
  every business service bean is present, `spring.threads.virtual.enabled` and that the background
  executor runs on virtual threads with the caller's identity.
- One class per layer: `BusinessLayerTest`, `RestControllerLayerTest` +
  `MaintenanceOrderControllerMockMvcTest` + `ReportExportControllerMockMvcTest` (a `@WebMvcTest`
  slice only loads the controllers it names, so the export endpoints cannot live in the order one),
  `ReportExportLayerTest` (reopens the generated .xlsx with POI and the .pdf with OpenPDF),
  `PersistenceLayerTest` +
  `InboxMessageRepositoryDataJpaTest` + `MasterDataAssetSyncDataJpaTest`, `EnversAuditDataJpaTest`
  (disables the test transaction on purpose), `MapperLayerTest`, `MessagingLayerTest`,
  `StockClientTest`, `DomainModelTest`, `JpaEntityModelTest`, `DtoValidationTest`,
  `GlobalExceptionHandlerTest`, `SecurityLayerTest`, `ApiAuthorizationRulesTest`,
  `MessagingContractExamplesTest` (one JSON per published event) and, for the outbox copied from
  `mto-configuration`, its own tests under `infrastructure/messaging/outbox` (`OutboxRelayDataJpaTest`
  against PostgreSQL, `OutboxWiringTest`, `OutboxRabbitPublisherTest`...). The planned exception of
  the same kind is `BudgetedOperationsDataJpaTest`, the cases of `mto-resources` for its copy of
  `BudgetedOperations`, against PostgreSQL and without the test transaction (each step is its own
  transaction and the work runs on a virtual thread). Add a method to the
  matching class instead of a new class; the events of each hook are methods of `BusinessLayerTest`
  (`RecordingEventPublisher`) and the envelope, the actor and the correlation of `MessagingLayerTest`.
  The crew rules (the expiries, the drivers, the availability and the rest too; the matching itself is
  `DomainModelTest`), the scope by execution package (`ScopeMocks`), the equipment of a task type, the
  parallel stock transitions, the deferred retry and the round skipped while another instance holds its lock are methods of `BusinessLayerTest` (the lock itself, against PostgreSQL, of `PersistenceLayerTest`); the resources
  handlers against PostgreSQL (adoption, the watermark, a team of construction, the `jsonb`
  qualifications and documents, the instruments, the `unavailable` periods and capabilities, the
  packages of a person's teams) of `MasterDataAssetSyncDataJpaTest`; the resources
  topology of `MessagingLayerTest`; the shift report's appendices in the real .xlsx and .pdf of
  `ReportExportLayerTest`;
the 202 of `Prefer: respond-async` of `MaintenanceOrderControllerMockMvcTest`.
