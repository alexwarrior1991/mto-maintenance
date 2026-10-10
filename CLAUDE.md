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

⚠️ `mto-stock` and `mto-configuration` are **independent sibling repositories**, not modules of this
one. Infrastructure master data (stations, tracks, profiles, disconnectors, section insulators)
belongs to `mto-configuration` and arrives here as events; the materials catalogue and the stock
belong to `mto-stock` and are reached through its REST API. **Nothing of either is re-modelled
here**: `catenary_asset` keeps only the snapshot a maintenance job needs (code, kp, track, station,
sectioning) and every material line stores the `mto-stock` ids.

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
- Profiles: `dev`, `test`, `prod`, selected via `SPRING_PROFILES_ACTIVE`. Local port is **8083**
  (`dev`); the container listens on 8080 and `mto-platform` publishes it on 8083.

## Architecture

Three layers under `com.alejandro.mtomaintenance`, the same split as `mto-stock`:

- `domain/model` — framework-free rules: `OrderStateMachine`, `DefectStateMachine`,
  `ShiftStateMachine`, `KilometricRange`, `Quantity`, `WorkloadEstimate`, `DomainValidations`.
  An `IllegalArgumentException` from here is turned into a 400 by `DomainGuard` in the services.
- `application` — `dto` (one package per resource), `service` + `service.impl` (package-private
  impls behind public interfaces; `MaintenanceLookups`, `ShiftRules`, `ChecklistRules`,
  `MaterialStockSynchronizer` and `MaterialLineFactory` are shared helpers), `mapper` (MapStruct,
  **entity → response only**: entities are built in the services with their Lombok builders because
  MapStruct cannot call the protected constructors), `exception`.
- `infrastructure` — `persistence.entity` (JPA), `persistence.repository` (Spring Data + native
  upserts), `persistence.specification`, `persistence.audit` (Envers), `web.controller`,
  `web.exception`, `messaging.rabbitmq` (the consumer and `MaintenanceRabbitMqNames`),
  `messaging.outbox` (the outbox of the own events: a copy of `mto-configuration`'s `core/outbox`
  plus the envelope factory, `MessageContextResolver` and `OutboxDomainEventPublisher`), `stock`
  (the `RestClientStockClient`), `export` (the POI and OpenPDF writers).
- `configuration` — security (Keycloak resource server), `rabbitmq`, `messaging` (signature and
  signer), `outbox` (every outbox piece as a `@Bean`, gone with `app.rabbitmq.enabled=false`),
  `events` (the daily `PreventiveDueSoonService` scheduler), `stock` (`StockProperties`,
  `StockClientConfiguration`), JPA auditing, OpenAPI.

### Domain in one paragraph

A `CatenaryAsset` is what maintenance is done on: a `TRACK_SECTION` (created through the API, a kp
range on a track, `MAIN` or `DIVERTED`), or a `PROFILE`, `DISCONNECTOR` or `SECTION_INSULATOR`
synchronized from `mto-configuration` (`source_service` + `source_entity_id`, never created through
the API, only `description`/`enabled`/`preventiveIntervalDays` editable). `enabled` is the sum of two
voices: `enabledAtSource` (only the events write it) and `disabledLocally` (only `DELETE` and `PUT`
write it; no event undoes it), kept together by a `CHECK`; `PUT enabled=false` needs
`maintenance-delete` like the `DELETE`. A disconnector on a pole takes its
track and package from its profile, and an insulator or a disconnector without a pole (which brings
its own kp and track since `V26` of `mto-configuration`) its package from the profiles of its track,
because the source publishes no package for them (`docs/06-messaging.md`). A disconnector that puts
two tracks in parallel also brings the other one (`connectedTrackId`, `V27` of `mto-configuration`),
in the same column as an insulator's. A disconnector may belong to no station (`station: null`, on open
track, in a neutral section or a substation): its location never comes from the station. A `SECTION_INSULATOR`
carries besides its two tracks, its `installationType` (`TRACK_CONNECTION` / `IN_TRACK`) and its
turnouts (`catenary_asset_switch`: `W31` at its kp with its `1:9` rate) — a turnout is part of the
insulator, not an asset of its own, so there is no new `CatenaryAssetType`. A `MaintenanceOrder`
(`PREVENTIVE`, `CORRECTIVE`, `INSPECTION`, `URGENT`) targets one asset and moves
`DRAFT → PLANNED → ASSIGNED → IN_PROGRESS → COMPLETED` (or `CANCELLED`); `URGENT` is the only type
that jumps `DRAFT → IN_PROGRESS` and is born `CRITICAL`. A preventive order on a `TRACK_SECTION`
generates one `MaintenanceTask` per enabled `PROFILE` in the range (`POST /orders/{id}/tasks/generate`),
each carrying the `MaintenanceTaskType`s performed (the `RG-xx`/`RP-xx` catalogue of the OCS plan,
with standard minutes that feed `WorkloadEstimator`). Tasks are executed inside a
`MaintenanceShift` (team, night window, possession `PARTIAL`/`FULL`, one or more tracks): a task can
only be completed with the order and a shift covering its track both `IN_PROGRESS`, a task type that requires
full possession cannot be done in a `PARTIAL` shift, and a `DIVERTED` section only accepts `FULL`
shifts. Completing a task can record `CatenaryDefect`s inline (`RESOLVED` in the shift if the work
was finished, `OPEN` with `repairPlannedDate` if not). A `MaintenanceInspection` copies the active
`InspectionTemplate` of the asset type into its items, and can create a defect and a corrective
order (both idempotent). Every material line (`MaintenanceMaterialUsage`) is reserved in
`mto-stock` when the order is planned, consumed when it is completed and released when it is
cancelled; a failure leaves the line `FAILED` (stock did not answer) or `REJECTED` (it said no, with
its reason) and `POST .../materials/{id}/sync` retries. A line
registered by mistake is removed with `DELETE .../materials/{id}` (a physical delete that Envers keeps
as a DELETED revision), releasing its reservation first; it is never cancelled.

### Persistence rules

- UUID ids, `AuditableEntity` (`created_at`/`updated_at`/`created_by`/`updated_by`), snake_case
  tables, PostgreSQL enums for every status/type, `numeric(12,3)` for kp, `numeric(19,6)` for
  quantities, `timestamptz` for instants.
- Ids of `mto-configuration` entities are `bigint` columns (`track_id`, `station_id`,
  `execution_package_id`); ids of `mto-stock` entities are `uuid` columns (`material_id`,
  `warehouse_id`, `stock_reservation_id`, `stock_project_id`).
- Codes come from sequences (`MO-000001`, `SH-`, `INS-`, `DEF-`), never from `MAX + 1`.
- `CatenaryAssetRepository.upsertFromMasterData`/`deactivateFromMasterData` are native SQL with the
  sequence watermark inside the `WHERE` (never read-then-write), which is why master-data changes
  leave no Envers revision. They write `enabled_at_source` and recompute `enabled`, never
  `disabled_locally`; `deactivateByTrack` advances the watermark of what it disables (the sequence of
  `mto-configuration` is global).
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
`mto.maintenance.master-data.queue` with DLX/DLQ) through the same idempotent **inbox** as
`mto-stock` (`inbox_message`, unique `(message_id, source_service)`). Four `MasterDataEntityHandler`s:
`profile`, `disconnector`, `section-insulator` (upsert/deactivate assets) and `track` (`DELETED`
only, deactivates every asset on the track). The other four entity names are logged and ignored.
The contract is owned by `mto-configuration`; see `docs/06-messaging.md` before touching
`application/dto/messaging`.

Producer of its own events for `mto-notification` (`mto.maintenance.exchange`, routing key
`mto.maintenance.<entity>.<event>`, no queue here). **`DomainEventPublisher.publish` is the only
door, and it is called inside the business transaction**: the event goes to `outbox_message`
(`V13`) with the change and the relay publishes it afterwards with publisher confirms
(`OutboxRabbitPublisher` refuses to start without them). The hooks: `StatusHistoryServiceImpl`
(`order.created`/`status-changed`/`reassigned`, `defect.created`/`status-changed`: every order and
defect transition passes through it), `MaintenanceShiftServiceImpl.start/close`,
`MaterialStockSynchronizer.record` (`material.rejected`/`in-doubt`/`failed`, only when the line's
state changes: the 5-minute retry must not publish one per round),
`CatenaryAssetServiceImpl.disable/patch` (`asset.disabled`, once), `MaintenanceInspectionServiceImpl`
(`inspection.created`/`item-failed`/`defect-created`/`corrective-order-created`) and the daily
`PreventiveDueSoonService` (`preventive.due-soon`, advisory lock, `operationId` from the date). The
names and `values` of every event live in `MaintenanceEvents` and nowhere else; the envelope is the
`AsynchronousMessage` of `mto-configuration` plus `actor` (`PERSON`/`SERVICE`/`SYSTEM`, from the
token) and `correlationId` (`X-Correlation-Id` of the request, else the master-data message id, as
Envers stores it), both read by `MessageContextResolver` when the event is created. **Keys are only
added**; a new key or event changes its example in `docs/messaging/examples/` in the same commit
(`MessagingContractExamplesTest` compares them with the real factory;
`MESSAGING_EXAMPLES_WRITE=true` regenerates them). `DomainEvent` rejects any key that smells like a
secret. With `app.rabbitmq.enabled=false` the publisher is the `NoOpDomainEventPublisher` and no
outbox bean exists.

### Stock integration

`StockClient` (interface in `application/service`) is the only door to `mto-stock`.
`RestClientStockClient` authenticates with the Keycloak service account `mto-maintenance-svc`
(`client_credentials`, audience `mto-stock-api`) and runs every call inside the Spring Cloud
circuit breaker `stock` (Resilience4j, tuned with `app.stock.circuit-breaker.*`). Its token manager
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
key (30 days). Completing an order rejected for such lines (`UnsyncedMaterialsException`)
and an explicit `sync` keep what stock already did (`noRollbackFor`). `NoOpStockClient` replaces it
with `app.stock.enabled=false`.

### Report export

The three reports (`GET /shifts/{id}/report`, `/reports/progress`, `/reports/monthly`) take
`?format=json|xlsx|pdf`, `json` by default: same routes, same `MAINTENANCE_READ` rule, unchanged JSON
contract. Each report is turned into one format-neutral `ReportDocument`
(`application/dto/export`: header pairs, an optional table, totals, typed `ReportValue`s) by its
layout in `application/service/impl`, and `ReportExportServiceImpl` hands it to the `ReportExporter`
of the requested format. The exporters are `@Component`s indexed by format at startup — the same
registry-by-key as the master-data handlers, failing the startup on a duplicate or a missing one — so
POI and OpenPDF live only in `infrastructure/export` and a new format is one class plus one
`ReportFormat` constant, with no controller change. Headers are in English on purpose (the catalogue
and the enum values printed in the cells already are); see `ReportLayouts` for that and for the
report time zone. `docs/04-rest-api.md` documents the parameter, the file names and the columns the
PDF leaves out.

### Auditing

Envers on `CatenaryAsset`, `MaintenanceOrder`, `MaintenanceTask` (+ its task-type join), `MaintenanceShift` (+ its track and blocking-disconnector collections),
`MaintenanceTaskCheckItem`, `MaintenanceInspection`, `MaintenanceInspectionItem`, `CatenaryDefect`,
`MaintenanceMaterialUsage`. Not audited on purpose: `MaintenanceStatusHistory`
(append-only), `InboxMessage` and `CatenaryAssetSwitch` (written only from master data), `MaintenanceTeam`, `MaintenanceTaskType`,
`InspectionTemplate`/`Item` (catalogues). `JpaEntityModelTest` guards the split. History at
`GET /<resource>/{id}/revisions`. The `asset` of an order, a task, an inspection and a defect is
`@Audited(targetAuditMode = NOT_AUDITED)`, like a shift's disconnectors: an asset that only arrived
by master data has no revision (native SQL), so a revision reads it as it is now instead of looking
for it in `catenary_asset_aud` and failing (`EnversAuditDataJpaTest`).

### Testing

- `PostgreSQLTestContainer` (in `support/`) starts `postgres:17-alpine` with Testcontainers, or uses
  `TEST_DATABASE_URL`/`TEST_DATABASE_USERNAME`/`TEST_DATABASE_PASSWORD` when set (no Docker needed).
- `MtoMaintenanceApplicationTests` boots the whole context against a real PostgreSQL and asserts
  every business service bean is present.
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
  against PostgreSQL, `OutboxWiringTest`, `OutboxRabbitPublisherTest`...). Add a method to the
  matching class instead of a new class; the events of each hook are methods of `BusinessLayerTest`
  (`RecordingEventPublisher`) and the envelope, the actor and the correlation of `MessagingLayerTest`.
