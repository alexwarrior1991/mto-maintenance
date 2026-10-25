# 02 · Domain model

## Assets (`CatenaryAsset`)

| Type | Origin | Key fields |
|---|---|---|
| `TRACK_SECTION` | API (`POST /assets`) | `trackId`, `startKp < endKp`, `trackKind` (`MAIN` / `DIVERTED`), optional `stationId`, `executionPackageId` |
| `PROFILE` | event `profile` of `mto-configuration` | `code = PRF-<sourceId>`, `name` = natural profile id (`12-2.27`, unique per track only: search it with `?name=&trackId=`), `startKp = endKp = kp`, `trackId`, `executionPackageId`, `sectioning` snapshot (`A/S S/A`) |
| `DISCONNECTOR` | event `disconnector` | `code = DSC-<sourceId>`, `stationId` (none for one that belongs to no station), `profileSourceId` and the kp and track of its profile, or its own track when it switches another one than its pole's (`ownTrack`, `V14`); without a pole, its own kp and track (`V26` of `mto-configuration`); the package of its profile or of its track; `connectedTrackId`, the other track of one that puts two in parallel (`V27`) |
| `SECTION_INSULATOR` | event `section-insulator` | `code = SIN-<sourceId>`, `stationId`, `enabled` from the source, `installationType` (`TRACK_CONNECTION` / `IN_TRACK`), `trackId` + `connectedTrackId`, `startKp`/`endKp` spanning its turnouts, and its `switches` (`catenary_asset_switch`: `W31`, kp, `1:9`, track) |

Rules: an asset with an origin cannot be created or renamed through the API (`PUT` only touches
`description`, `enabled`, `preventiveIntervalDays`; the other fields are accepted with the value the
asset already has, so a form can send it back whole, and changing one is 409 `AST-001`); `DELETE` disables, never deletes; no new
order, inspection or task on a disabled asset; `preventiveIntervalDays` +
`lastPreventiveCompletedAt` give `nextPreventiveDueAt` and the `preventiveDueBefore` filter (an
asset never done is due).

Enabled or not has two voices, and `enabled` is what they add up to:

| Field | Who writes it | Meaning |
|---|---|---|
| `enabledAtSource` | only the master-data events (`null` on own sections) | what `mto-configuration` says: `false` after a `DELETED` of the asset or of its track |
| `disabledLocally` | `DELETE`, and `PUT` with `enabled` | maintenance's own decision; no event touches it |
| `enabled` | recomputed by both (a `CHECK` keeps it) | `coalesce(enabledAtSource, true) and not disabledLocally`: what every query and filter reads |

So an asset disabled here stays disabled through any update or republish of the source, and one
disabled at the source cannot be brought back here (`PUT` with `enabled=true` answers 409
`AST-001`); it comes back when the source enables it, unless it is also disabled here. `PUT` with
`enabled=false` is the same decision as `DELETE` and needs `maintenance-delete` too. A track deleted
at the source disables what is on it: at the source for the synchronized assets (advancing their
sequence watermark, so an older event of a profile arriving later does not bring it back), and here
for an own section, which a person can enable again. Assets disabled here before `V10` cannot be
told apart from those disabled at the source, and count as the latter.

## Orders (`MaintenanceOrder`)

Types `PREVENTIVE`, `CORRECTIVE`, `INSPECTION`, `URGENT`; priorities `LOW … CRITICAL`; location
(`executionPackageId`, `trackId`, `stationId`, `startKp`, `endKp`) copied from the asset at
creation; optional `teamId`, `assignedUser`, `plannedDate`, `stockProjectId` (resolved from the
stock project `EP-<executionPackageId>` when present). `estimatedMinutes` and `estimatedShifts`
are computed from the task types (4.5 effective hours per shift), never stored.

**An order of a team of `mto-resources` without a user goes to its leader** (`fillLeader`): at
creation, on `assign` without `assignedUser` and on a `PATCH`/`PUT` that changes the team without
touching `assignedUser`, the order takes the username of the team's leader; one that still had the
leader of the previous team (given by this same rule) moves to the new one; on `assign`, an
`assignedUser` equal to the one the order already has is not a choice (the dialogs send it back) and
follows the same rule. A user given explicitly
wins, and a local team (no leader) or a leader without a Keycloak user change nothing. The
`order.status-changed`/`reassigned` event already carries `assignedUser`, so the
`maintenance-order-assigned` rule of `mto-notification` tells the leader.

| Transition | From | Rules |
|---|---|---|
| `plan` | `DRAFT` | `plannedDate` not in the past; reserves every `NOT_REQUESTED` material in stock |
| `assign` | `PLANNED`, `ASSIGNED` → `ASSIGNED`; `IN_PROGRESS` keeps its status | `teamId` or `assignedUser`; without a user, the leader of a team of `mto-resources` |
| `start` | `PLANNED`, `ASSIGNED`; `DRAFT` only for `URGENT` | sets `actualStartDate` |
| `complete` | `IN_PROGRESS` | no task `PENDING`/`IN_PROGRESS`; at least one `COMPLETED` task or `closingNotes`; `INSPECTION` needs an inspection with `originOrderId`; a `FAILED` or `REJECTED` material line blocks unless `force` (supervise), and the other lines keep what stock did even when that rejects the completion; consumes reservations or registers direct outputs; `PREVENTIVE` updates `asset.lastPreventiveCompletedAt` |
| `cancel` | any non-terminal | reason required; releases reservations; open tasks → `CANCELLED`; linked defect `IN_PROGRESS` → `OPEN` |
| `PUT` | full in `DRAFT`/`PLANNED`; afterwards only `description`, `priority`, `closingNotes` | |

`URGENT` means "emergency corrective without planning": born `CRITICAL`, it is the only type that
can start from `DRAFT`.

## Tasks (`MaintenanceTask`)

One unit of work of an order, usually one profile: `sequence`, `assetId` (the profile),
`taskTypes` (N codes of the catalogue), `shiftId`, `status` (`PENDING`, `IN_PROGRESS`, `COMPLETED`,
`CANCELLED`), `notes` (works performed), `defectsFound`, `photoRefs`, check items when a task type
is diagnostic. Added while the order is at most `IN_PROGRESS`; started and completed only with the
order `IN_PROGRESS` **and** a shift `IN_PROGRESS` on the same track; a check item that requires a
measure cannot be left without a result.

`POST /orders/{id}/tasks/generate` creates the tasks of a preventive order asset by asset
(`DRAFT` or `PLANNED`), idempotent per asset. Without `selections` it does what it always did: one
task per enabled profile of the order's section (ordered by kp) and, with `assetTypes`, one per
enabled `SECTION_INSULATOR` or `DISCONNECTOR` touching it — on its track or on another track it
connects with (`connectedTrackId`), its kp range overlapping the section's — between the profiles
around it. With `selections` an order **mixes** work on several tracks: each selection is an asset
type (`PROFILE`, `SECTION_INSULATOR`, `DISCONNECTOR`) in a scope — another section of the package
(`sectionId`), a track with an optional kp range (`trackId`, `fromKp`, `toKp`; what is on it or
connects with it), chosen assets (`assetIds`) or the whole execution package — optionally of a
station. The order's own asset is then only its anchor: an order on an insulator can generate only
insulators, one on a disconnector only disconnectors.

- **One order, one execution package.** Its stock project (`EP-<package>`) is the one every material
  line is reserved against, and the monthly report counts its tasks there; it may cover several
  tracks of its package. Selections need an order with a package. A section or chosen assets of
  another package are a 400; what a track or a kp range finds of another package is skipped
  (`skippedOtherPackage`) — also without selections when the order has a package. An asset without a
  package (a hole in the master data) is not skipped: its package is unknown.
- **Default task types per asset**: groups 1, 2 and 4 on a profile (`RG-01, RG-05, RG-08, RG-10,
  RG-11, RG-12, RG-13, RP-08`), `RG-03` and `RP-02` on a section insulator plus `RG-04` and `RP-03`
  when it has turnouts (its `switches`, also the out-of-service ones, which are still on the
  drawing: most insulators connect two tracks through one, and its turnouts are checked with it),
  `RP-07` (catenary isolators: the disconnector itself) on every disconnector, plus `RP-06` (its
  remote drive test) when it is driven by a motor (`driveType`, `V16`) and `RG-06` (catenary automatic
  valves) when it is a surge arrester (`disconnectorFunction` `SurgeA`, `V17`), both from
  `mto-configuration`. No generated asset is left without a type, so `NO_TASK_TYPES` /
  `skippedWithoutTaskTypes` no longer happen with the defaults (they stay in the contract). Types
  chosen in the request go to everything generated, instead of the defaults; a selection's own ones
  win over them. The insulator types, `RP-06` and `RP-07` need full possession, so those tasks wait
  for a `FULL` shift; `RG-06` does not.
- **No duplicates.** An asset found by two selections gets one task (the first selection decides);
  one that already has a task in the order, even cancelled, is skipped (`skippedAssets`,
  `skippedProfiles` for profiles, as before); and so is one with an open task (`PENDING` or
  `IN_PROGRESS`) in another preventive order that is not finished (`skippedInOtherOrders`,
  `otherOrderCodes`). Generations in one package queue behind a PostgreSQL advisory lock per
  package, taken also by a task added by hand, so two at once never take the same asset as free.
  Before it every generation takes one lock shared by all of them, which an order without a package
  takes exclusively: it may take assets of any package, so it waits for no other generation to be
  running and the rest wait for it. An asset without a package (a hole of the master data) is not
  skipped by package, so two generations of two packages could both take it: each locks it on its
  own key, after the package's, and selects again to see what the other committed. Every key is the
  64-bit `AdvisoryLockKeys.of(name)` (the first 8 bytes of its SHA-256), never `String.hashCode()`:
  all the advisory locks of the database share one keyspace, and several are taken in key order. In one instance a generation waits for its turn before opening its
  transaction, on a `ReentrantLock` per scope (`GenerationLocks`), so without a connection taken, and
  opens it once the previous one has committed, reading the order as it was left. Inside the
  transaction it takes the advisory lock (`pg_advisory_xact_lock`), already free in that instance:
  that is what keeps two instances apart, and a generation waiting for another instance waits there,
  with its connection. A task added by hand waits on the same lock inside its transaction: it is one
  row, not a package. A preview takes neither.
- **A task added by hand follows the same rules** (`POST /orders/{id}/tasks`): an asset of another
  package than the order is a 400 (an order is of one package), and in a preventive order so is an
  asset with an open task in another open preventive order (it would be done twice). A corrective or
  an inspection takes any asset. And an order holding tasks (not cancelled) on assets of its package
  does not move to another package (`PATCH`, 400 on `executionPackageId`).
- **`Prefer: respond-async`** turns a generation that does not finish in time into a background
  operation (202, see [Background operations](#background-operations)); without the header, or with
  `dryRun`, it stays synchronous.
- **Order of the tasks**: track by track, in the order the tracks appear in what was asked, and by kp
  and code inside each: an insulator of another track that connects goes with the track it was found
  on, between the profiles around it.
- **Another section must be of the same kind** (`MAIN`/`DIVERTED`) as the order's: the possession a
  diverted track needs is decided by the order (`ShiftRules.requireCompatiblePossession`). An order
  that is not on a section is a main-track one.
- **`dryRun: true`** creates nothing and takes no lock: `assets` says, asset by asset in the order it
  would be generated, its track, kp, task types and outcome (`CREATE`, `ALREADY_IN_ORDER`,
  `OTHER_PACKAGE`, `IN_OTHER_ORDER`, `NO_TASK_TYPES`), and the counters and the estimate are those of
  generating it.
- Each task keeps the checklist of its own asset type when asked (`withChecklist`, per request or per
  selection) or when a type is diagnostic, and is worked from a shift on its own track or the
  connected one: nothing of the shift rules changes. The estimate counts the per-kilometre types once
  per track: on the order's track over its section, on another over what is done on it.

Completing a task can create defects inline: `workComplete=true` → `RESOLVED` in that shift;
`workComplete=false` + `repairPlannedDate` → `OPEN`. Completing the last task never closes the
order.

## Task types (`MaintenanceTaskType`)

The `RG-01…RG-18` (general revision) and `RP-01…RP-12` (particular revision) catalogue of the OCS
plan: `functionalGroup` (structural supports, overhead conductors, devices and switches,
anchorage components, turnouts and section insulators, diagnostics, none),
`standardMinutesPerUnit` + `unit` (`UNIT`, `SPAN`, `KM`, `DEFECT`, `PROFILE`), `fixedMinutes`
("60 min + 3/km"), `requiresFullPossession` (groups 3, 5 and `RP-12`), `diagnostic` (group 6). The
minutes per unit are those of the plan's task sheet (`V18`, see `03-database.md`).

The only thing that changes through the API is the **equipment a type requires** (`requiredEquipment`,
`V24`): `INSULATED_PLATFORM` (a vehicle of the shift whose model has that capability in `mto-resources`)
and `EARTHING` (a vehicle with the capability `EARTHING`, or an active `EARTHING_POLE` among the shift's
instruments). Nothing is seeded: whoever supervises sets it with `PATCH /task-types/{code}` (merge-patch
with the `version` read). A task whose types require something is assigned to a shift, started and
completed only in a shift that carries it (`ShiftRules.requireEquipment`, 409 `SHF-001`), and a `PATCH`
of a shift that takes away the vehicle or the instrument an assigned open task needs is refused the same
way. What was already done is not re-checked.

## Shifts (`MaintenanceShift`) and teams (`MaintenanceTeam`)

A shift is the unit of execution and the source of the daily report: date, team, base and vehicle as
free text (what a local team had), its crew, its vehicles and its instruments from `mto-resources`
([below](#shift-crew-and-vehicles)), `possessionType` (`PARTIAL` on weekdays on main track, `FULL`
on weekends), planned and actual window, `voltageCutoffAt`, `netWorkMinutes` (computed at close),
the set of `blockingDisconnectors` opened to isolate the work zone (as many `DISCONNECTOR` assets as
the cut needs, not a fixed pair), earthing points, parking place, the `trackIds` it covers (one or
more tracks of the same night; a second execution package is a second shift), kp range, personnel,
equipment, observations. States `PLANNED → IN_PROGRESS → CLOSED`, `CANCELLED` before closing;
starting, closing and cancelling publish `shift.started`, `shift.closed` and `shift.cancelled`.
Closing requires the actual times, says where each vehicle was left, and returns to `PENDING`
(without shift) the tasks that were not completed. While the shift is `PLANNED` or `IN_PROGRESS` one
open task can be taken out the same way (`POST /shifts/{id}/tasks/{taskId}/unassign`): a wrong
assignment, or one that would stop the shift from losing a track or going partial; a completed or
cancelled task stays in the shift it was worked in.

Window rules: a task's profile must be on one of the shift's tracks (an insulator or a disconnector
that joins two tracks is on both, `connectedTrackId`, as in the reports by track; the asset summary
embedded in a task carries it, and `GET /shifts?trackId=a&trackId=b` finds the shifts of either); a task whose types require
full possession cannot be assigned to or completed in a `PARTIAL` shift; a `DIVERTED` section only
accepts `FULL` shifts. The same rules hold when a shift with tasks already assigned is changed:
removing a track one of its open tasks is on, or making it `PARTIAL` under a task that needs full
possession or a `DIVERTED` section, is 409 `SHF-001` naming the task. The profiles reviewed in a shift are the `PROFILE` assets of its `COMPLETED`
tasks (`GET /shifts/{id}/profiles`). An inspection can be recorded in a shift (`shiftId`): the shift
must be `IN_PROGRESS` or `CLOSED` (one done after midnight is often written down at the end) and work
on the asset's track or the one it connects with, or it is 409 `SHF-001`. The shift report lists those
inspections (`inspectionsRecorded`, `inspections[]`) after its task rows, its crew, its vehicles with
where each was left and its instruments with their calibration (the `Crew`, `Vehicles` and
`Instruments` appendices of the .xlsx and the PDF; the JSON carries them in `shift`).

### Teams

A team is either **local**, created and edited here (`POST`/`PUT /teams`) with its base and vehicle
as text and the execution packages it covers (`V3` seeds `A` Rishpon and `B` Mishmar, without
packages), or **synchronized** from `mto-resources` (`V19`, `synchronizedFromResources`,
`sourceService = mto-resources` + `sourceEntityId`), which owns the people, the organization tree and
the fleet. A synchronized team carries its leader (the team leader, foreman of its vehicle), its
supervisor (the head of the nearest supervision above it there), its linemen (`members`; the leader
apart), its execution packages and its default vehicle (`defaultVehicleId`; `vehicle` is that
vehicle's code), and **no base**: a vehicle is left wherever the night allows, and closing a shift
says where. Only teams of discipline `MAINTENANCE` arrive; construction ones are ignored.

- **Adopted by code.** The first event of a team of `mto-resources` takes over the local team of the
  same code, which becomes synchronized and loses its base, so the shifts and orders that pointed at
  it still do; without one, the team is created. Only a team that is not synchronized yet is
  adopted, and only if that team of `mto-resources` is not here already (a later code change renames
  its row).
- **Read-only here.** `PUT /teams/{id}` on a synchronized team is 409 `TEA-001`
  (`SynchronizedTeamException`): its name, leader, linemen, packages and vehicle change in
  `mto-resources` and arrive by event. A local team is edited as before until it is adopted.
- What arrives is a snapshot, written only with native SQL and the sequence watermark
  (`docs/06-messaging.md`): `crew_person` for the people, `fleet_vehicle` for the vehicles, and the
  team's columns, linemen and packages, the last two replaced whole by every applied event.

## Shift crew and vehicles

A shift carries who works that night and in what, all from `mto-resources` (`V20`): its `teamLeader`
(the foreman), its `supervisor` (who has it in charge; one supervisor has several shifts at once), its
`linemen` and its `vehicles`. They may differ from the team's (a substitute, someone on leave). At
creation, what the request does not say of a synchronized team is copied from it: leader,
supervisor, linemen, and its default vehicle if that vehicle has arrived; a local team brings nothing.
They change with the rest of the shift (`vehicleIds`, `teamLeaderPersonId`, `supervisorPersonId`,
`linemanPersonIds`): the two sets are replaced whole (a vehicle that stays keeps its parking), a
`PATCH` may empty any of the four, but a leader is replaced, not emptied, while the shift has linemen
(400 `VAL-001`). An unknown person or vehicle is 404 `CRW-404` / `FLT-404`. When nothing else gave
the free `vehicle` text, it is filled with the codes of the shift's vehicles.

`ShiftCrew` checks the rules on create, update and start, all 409 `SHF-001`:

| Rule | When |
|---|---|
| At most 6 linemen; the leader is not one of them; a `WORKER` does not lead (any other category does, also one this service does not know yet: `mto-resources` decides); leader and linemen active there | Always |
| Every vehicle active and `available`; one that neither moves by itself (`selfPropelled`) nor is carried (`portable`), a wagon or a trailer, goes with a vehicle of the shift that tows (`towing`: the `TOWING` capability of its model) | Always |
| The crew (leader and linemen; the supervisor does not ride) fits in the seats of the self-propelled vehicles, checked only when every one of them declares its seats: a missing figure is not taken as zero | Always |
| No vehicle, leader, lineman or instrument in another open shift (`PLANNED`/`IN_PROGRESS`) of the same `shiftDate` (the windows are often empty, so the date is compared; the supervisor is not checked). One transaction-scoped advisory lock per vehicle, person and instrument, taken in a fixed order, so two shifts saved at once queue and the second sees the first | On an open shift |
| A complete crew: a leader and 2 to 6 linemen (`CrewComposition`, the copy of the rule `mto-resources` applies to its teams: 3 to 7 people in one vehicle) | To start, and on any change while `IN_PROGRESS`; only on a shift with a crew (`hasCrew()`: a leader, linemen, or a synchronized team). A shift of a local team without a crew starts as before |
| Nothing expired at the shift's date: a qualification of the leader, the supervisor or a lineman, a document of a vehicle, the calibration of an instrument; and no instrument retired (`ShiftQualifications`, [below](#qualifications-and-expiries)) | Always |
| A different qualified driver for each self-propelled vehicle whose model requires a qualification | To start, and on any change while `IN_PROGRESS` |

Closing says where each vehicle was left (`parkings[]` of `POST /shifts/{id}/close`, one per
vehicle): a parking site of `mto-resources` (`parkingSiteId`), a track and kp (`trackId` of
`mto-configuration` + `kp`), or a free description; at least one of them, and only for a vehicle of
the shift (400 `VAL-001` otherwise). A rail-only vehicle (`mobility = RAIL_ONLY`) left by
description only is 409 `SHF-001`: it stays on a track or at a site with rail access. Without
`parkings` the shift's `parkingPlace` stands, as before. `mto-resources` reads it from `shift.closed`,
and from `shift.cancelled` of a shift that had started, to move each vehicle's position.

The instruments taken that night (`V23`: earthing poles, voltage detectors, measuring equipment of
`mto-resources`, `maintenance_instrument`) are a set chosen in the shift (`instrumentIds`), replaced
whole like the linemen, never copied from the team; an unknown one is 404 `MSI-404`. The free
`measurementEquipment` text stays for the shifts of before.

The response carries `teamLeader`, `supervisor`, `linemen` (by code), `vehicles` (by fleet code,
`vehiclesByCode()`) with their `parking` (`null` until closed with one) and `instruments` (by code,
`instrumentsByCode()`: `id`, `code`, `name`, `kind`, `calibrationExpiresOn`); the shift events and the
report carry the same (the events, only the instruments' ids and codes).

## Qualifications and expiries

What expires belongs to `mto-resources`: the qualifications of a person (a driving licence `LIC-B`, the
rail auxiliary vehicle driver `RAIL-DRIVER`, the road-rail machine operator `RR-OPERATOR`...), the
documents of a vehicle (road or rail inspection, insurance, crane or platform certificate) and the
calibration of an instrument. Its events bring them into the snapshots (`V23`, `03-database.md`):
`crew_person.qualifications` (`code`, `kind`, `drivingLicence`, `expiresOn`; all of them, also the
expired ones), `fleet_vehicle.documents` (`kind`, `reference`, `expiresOn`), the qualification a
vehicle's model requires of its driver (`fleet_vehicle.required_driver_qualification`) and
`maintenance_instrument.calibration_expires_on`. Warning about what is about to expire is
`mto-notification`'s (its `qualification.*`, `vehicle-document.*` and `calibration.*` events); here
**what has expired does not get into a shift**.

`ShiftQualifications` (`domain/model`, framework-free; it returns the reason, `ShiftCrew.check` turns it
into a 409 `SHF-001`) reads everything at the shift's `shiftDate`, the night it is worked and not
today: a shift planned for next week does not take what expires tomorrow. Expired is `expiresOn <
shiftDate` (the last day still counts); a missing date never expires.

- **Always** (create, any change, start): no expired qualification on the leader, the supervisor or a
  lineman; no expired document on a vehicle of the shift; no instrument retired or with its
  calibration expired. One message lists everything that has expired, so it is fixed at once.
- **To start, and on any change while `IN_PROGRESS`**: each self-propelled vehicle whose model requires
  a driver qualification gets a **different** person of the crew (the leader or a lineman; the
  supervisor does not ride) holding it, valid that day. It is a bipartite matching (an augmenting path
  per vehicle), not a loop that hands each vehicle the first who can drive it: a leader who could drive
  the tower car and the road-rail drives one, and a lineman who only drives tower cars takes the
  other. A shift still `PLANNED` may lack its drivers; a vehicle that does not move by itself, or whose
  model requires nothing, needs none.

### Availability and rest (`V24`)

When a person or a vehicle is **not available** is `mto-resources`' too: the absences of a person
(holidays, sick leave, training...) and the repairs that immobilise a vehicle, planned or open. Its events
carry them as `unavailable: [{from, to}]` (both dates included, `to` null when the end is unknown),
without the reason: a sick leave never leaves `mto-resources`. They land in `crew_person.unavailable` and
`fleet_vehicle.unavailable`, and `ShiftAvailability` (`domain/model`) refuses, always (create, any change,
start), a shift whose leader, supervisor, lineman or vehicle has a period that covers its `shiftDate`
(409 `SHF-001`, one message for everything).

A person also **rests between two nights**: from the end of one shift (its `actualEnd`, else its
`plannedEnd`) to the start of the next where the same person goes as leader or lineman (`actualStart`,
else `plannedStart`) there are at least `app.shifts.minimum-rest` (12 h by default; `0` switches it off).
The night before and the night after are read (`findShiftsOfCrewOn`, planned, in progress or closed; a
cancelled one does not count), under the same advisory locks `person:<id>` as the double booking. A
shift without hours cannot be measured and is not refused; the supervisor, who oversees several, is not
measured.

A shift without crew, vehicles or instruments of `mto-resources` has nothing to check, so the shifts
of before behave as they did. Renewing something in `mto-resources` reaches the snapshot by event and
the next check sees it. Nothing re-checks a shift by itself; a change made after midnight is still
weighed at the shift's date, and closing and cancelling never check.

## Scope by execution package (`MaintenanceScope`)

Who writes may be limited to the execution packages of their teams; **reading is never limited**. It is
computed here, without calling anybody: the person of the token's `preferred_username` in `crew_person`
(case-insensitive, as Keycloak) and the synchronized, active teams where that person is the leader, the
supervisor, the chief supervisor (`team.*.chiefSupervisor`, `V24`) or a lineman.

- **No limit**: whoever has no record with that username (the managers of always, the service accounts
  such as `mto-field-svc`, the consumers of messages, which carry no token), the categories
  `CHIEF_MAINTENANCE`, `CHIEF_CONSTRUCTION` and `PROJECT_MANAGER`, and everybody with
  `app.scope.enabled=false`.
- **Limited**: any other category, to the packages of their active teams; without teams, to none.

What is checked, at the start of every write of the services (create, `PUT`/`PATCH`, every transition):

| Resource | Its package |
|---|---|
| Order, and its tasks, check items and material lines | the order's `executionPackageId`; a change of package is checked on the old and on the new one |
| Inspection, defect | the package of its asset (copied when created); the order it is linked to (a defect's `orderId` or `link-order`, an inspection's `originOrderId`) is checked too |
| Asset | its own; a change of package too |
| Shift | its `executionPackageId`; without one, its team must be one of the person's teams. Assigning, unassigning, starting or completing a task checks the shift it is bound to and the task's order |

Something without a package is only written by who has no limit (save that shift of one's own team).
Local teams are not limited. Out of scope is 403 `SCP-403`, whose message says which package it is and
which ones are the person's; it is not the `AUTH-403` of a missing role.

## Inspections (`MaintenanceInspection`) and templates

An inspection copies the items of the active `InspectionTemplate` of the asset type (profile,
disconnector, section insulator): each item has `code`, `label`, `unit`, `[minValue, maxValue]`,
`requiresMeasure`, and records `measuredValue`, `adjusted`, `valueAfterAdjustment`, `itemResult`
(`OK`, `DEFECT`, `NOT_APPLICABLE`), `notes`. Results `OK`, `MINOR_DEFECT`, `MAJOR_DEFECT`, `UNSAFE`.

Rules: `OK` is inconsistent with an item in `DEFECT`; a measure out of range that was not adjusted
cannot be `OK`; `create-defect` needs `MAJOR_DEFECT`/`UNSAFE` (or `MINOR_DEFECT` + `force`);
`create-corrective-order` creates a `CORRECTIVE` order in `DRAFT` (`URGENT` when `UNSAFE`) linked
to the defect. Both are idempotent and return what already exists.

## Defects (`CatenaryDefect`)

Severity `LOW … CRITICAL`, status `OPEN → IN_PROGRESS → RESOLVED → CLOSED`, `DISCARDED` from
`OPEN`. Fields from the daily report: `correctionType`, `partsReplaced`, `resolvedInShiftId`,
`repairPlannedDate`, `foundInTaskId`, `photoRefs`. `link-order` moves it to `IN_PROGRESS`;
`resolve` needs `resolutionNotes` and the linked order `COMPLETED`; `close` only from `RESOLVED`;
`discard` needs a reason.

## Materials (`MaintenanceMaterialUsage`)

A line per (order, optional task, material, warehouse) with `plannedQuantity`, `consumedQuantity`
(≤ planned unless `allowOverConsumption`), `unit`, the stock reservation id and
`stockSyncStatus` (`NOT_REQUESTED`, `RESERVED`, `CONSUMED`, `RELEASED`, `FAILED`, `REJECTED`) with
the last error. Reservation happens at `plan` (or when the line is added to an order already
planned), consumption at `complete`, release at `cancel`; without a stock project the consumption is a
direct output. The project is the one of the order's execution package (`EP-<id>` in stock), looked
up once per plan or start; if stock is down at that moment the lines are left `FAILED`.

The materials sent with a task completion (`materials[]`) become lines of their own (with the task;
planned = consumed) and are reserved **last**, once the lines, the inline defects and the task are
stored: a reservation made before another material of the list failed (unknown to stock, repeated)
used to stay `ACTIVE` in stock with its line rolled back, and the retry made a second one, because
the idempotency key comes from the line and the line was new. A `PUT`/`PATCH` of a reserved line
that sends the same `plannedQuantity` is not a change and is accepted.

A step that does not go through leaves the line in one of two states, both blocking `complete`
unless `force`:

- `FAILED`: stock did not answer (network, timeout, 5xx, circuit open, the service account refused).
  `POST .../sync` retries, and answers 503 `STK-503` while stock is still down.
- `REJECTED`: stock answered no, and the reason (its code and message) is in `stockSyncError`. Retrying
  alone changes nothing: first something must change (more stock, another warehouse, a smaller
  quantity). An explicit `sync` answers 409 `STK-001` when there is not enough stock and 422
  `STK-422` for any other rejection.

`mto-stock` publishes nothing, so before consuming or releasing a line that has a reservation the
service reads it (`GET /reservations/{id}`). The reservation may have changed from the warehouse, or
in an earlier attempt that failed halfway:

| Reservation in stock | Completing (consume) | Cancelling (release) |
|---|---|---|
| `ACTIVE` | consume if used = reserved; release and output what was used if less; consume and output the excess if more | release |
| `CONSUMED` | output only the excess over what was reserved | stays `CONSUMED`: the material left the warehouse |
| `RELEASED`, `CANCELLED`, or unknown to stock (404) | direct output of what was used, or `RELEASED` if nothing was | `RELEASED` |

The reserved quantity is the one stock holds, which the warehouse may have changed. So repeating a
step never consumes or releases the same thing twice. A line that never got a reservation and used
nothing ends `NOT_REQUESTED` instead of staying `FAILED` for good. In an order in progress, `sync`
also checks a `RESERVED` line: if the warehouse released its reservation, it asks for another one.
A consumed reservation remains the line's until the order completes. A line already `CONSUMED`
cannot change any more (409 `MAT-001`): completing only settles what is not consumed yet.

A reservation or an output that reached stock but whose answer was lost (a timeout, a 5xx) cannot
be asked about this way: the line never learnt what was created. Those two requests carry an
`Idempotency-Key` (see `mto-stock`'s `docs/04-rest-api.md`), and the line remembers the one left
without an answer in `stockRequestInDoubt` (`RESERVATION` or `OUTPUT`) until stock answers it:

- The key is the line, the request and, for a reservation, the reservation the line had before:
  `mto-maintenance:<line>:reserve:first`, `mto-maintenance:<line>:reserve:<previous>`,
  `mto-maintenance:<line>:output`. It does not depend on the body, so a retry always sends the same
  key, and stock gives back what it already did. Asking for another reservation after the warehouse
  released the previous one is a new request, hence the previous one in the key; a line gives at most
  one output (what was used, or the excess over its reservation).
- Whatever is done next with the line against stock starts by repeating the request in doubt, with
  the same key and the same body: `sync`, `start` (which reserves again), `complete` (the reservation
  in doubt is confirmed and then consumed, never replaced by a direct output), `cancel` (confirmed and
  then released; an output in doubt is finished, and the line ends `CONSUMED` like in stock) and
  removing the line (a reservation in doubt is confirmed to release it; with an output in doubt the
  line cannot be removed, 409 `MAT-001`, because the material may have left already). If stock now
  answers no, the request never reached it — it would have handed it back without validating anything
  — and the line carries on as if it had not been sent.
- Until stock answers, what travels in that request does not change: the line's planned and consumed
  quantities (409 `MAT-001`; the same value, or `allowOverConsumption`, are accepted) and the order's
  stock project (409 `MAT-001`). With another body under the same key stock would answer 409
  `IDEM-001`; if that ever happens the line is left `REJECTED` with the reason, and whatever the lost
  request created is for the warehouse to look at.
- Stock remembers a key for 30 days (`app.idempotency.retention` there). `FAILED` lines are retried on
  their own every 5 minutes (`app.stock.sync-retry.*`, `StockSyncRetryService`), exactly like a
  `sync`, so a request in doubt is settled within minutes of stock answering again; each round stops
  at the first line stock still does not answer, and the next one carries on after it. A round leaves
  out the lines changed in the last minute (`QUIET_PERIOD`): a line cut by a transition's deadline may
  still have its consume or release in the air, here or in another replica, and its deferred retry
  waits up to 30 s for it; repeating it meanwhile would get it refused by stock and leave the line
  `REJECTED` without being so. The next round takes them. With several
  instances the round runs in one of them: it holds a PostgreSQL advisory lock of session for its
  whole length (`SessionAdvisoryLock`: a connection of its own, outside any transaction, since each
  line goes in its own), and the others skip it. Two instances retrying the same line would still be
  harmless (same key, the line's version), but stock would get the call once per instance. `REJECTED`
  lines are not retried: repeating what stock refused gives the same answer until something changes.
- The body does not depend on the moment it is sent (stock stamps `reservedAt` and `occurredAt` on
  arrival, and would ignore them in the comparison anyway), and the output of what was used when no
  reservation covers it has the same notes whichever way it is reached — the reservation released in
  this very step because less was used, or already holding nothing.

What is left: a request whose stock call succeeded but whose local transaction then rolled back (a
concurrent edit at commit) leaves no trace here; its retry, if the body changed meanwhile, is
refused with `IDEM-001` and the line says so. `FAILED` lines from before `V12` carry no request in
doubt: nobody knows which request failed.

An order transition talks to stock for all its lines at once: `plan` and `start` reserve
(`reserveAll`), `complete` consumes (`consumeAll`), `cancel` releases (`releaseAll`). Each line's
conversation runs on a virtual thread, at most `app.stock.sync.parallelism` (4) at a time, under one
deadline for all of them, `app.stock.sync.deadline` (10 s): four slow lines take the time of one. The
deadline starts before the order's stock project is resolved (`findProjectIdByCode` is a stock call of
the same transition), so the whole conversation stays below the gateway's 15 s read timeout with room
for the transaction; a longer one would make the gateway answer 504 while the transition commits.

- A conversation works on a detached copy of its line (`StockLine`), never on the entity nor the
  database (the Hibernate session is not that thread's, and a lazy proxy would go to the database from
  it). The transaction thread waits, applies each copy back to its line and publishes the
  `material.*` events, in the order of the lines. The order's stock project is resolved once, before
  copying, inside the deadline.
- A line not finished at the deadline is cut: it is left `FAILED` with whatever it knew, its request
  in doubt included (`deferred: mto-stock did not answer within …`), and the order changes state
  without waiting for it (`complete` refuses it like any `FAILED` line, 409 `MAT-001` unless `force`,
  and keeps what stock did with the others). From then on its thread sends nothing more to stock (the
  copy's gate, `StockLine.cancel`/`checkOpen`): only the call already in the air can still land, and
  it is either in doubt (a reservation or an output, repeated with the same key) or found out by the
  next attempt asking stock for the reservation (a release or a consume).
- After commit, `DeferredStockSyncListener` retries those lines at once (`StockLinesDeferred`,
  `StockSyncRetryService.retryLines`: a `sync` per line, each in its own transaction, stopping at the
  first one stock still does not answer) instead of waiting for the 5-minute round. It first lets the
  calls in the air finish, for at most 30 s, so the retry never crosses one of them.
  `app.stock.sync.immediate-retry=false` leaves them to the round.
- A step of one line stays on the caller's thread, one line after another and without deadline:
  `sync` (`syncNow`), removing a line (`releaseNow`), reserving a line added to an order already
  planned or the materials of a task completion (`reserve(usage)`), and `consume`/`release` of one
  line.

A line registered by mistake is removed, not cancelled (`DELETE /orders/{id}/materials/{usageId}`):
the row goes and Envers keeps its last state as a DELETED revision. A reserved line is released in
stock first; if stock does not answer, the removal fails with 503 and the line stays, because its
reservation would stay alive there. A reservation stock no longer holds (released or cancelled from
the warehouse, or unknown to stock) does not block it. A consumed line, one whose reservation was
consumed from the warehouse, or a line of a completed or cancelled order, cannot be removed. There is no `CANCELLED` state on purpose: `stockSyncStatus`
describes the conversation with stock, and the unique (order, task, material, warehouse) would
stop the line from being registered again.

## Background operations

What may take longer than a request should wait becomes asynchronous when the client accepts it
(`Prefer: respond-async`, RFC 7240); today only `POST /orders/{id}/tasks/generate` without `dryRun`.
`BudgetedOperations` (copied from `mto-resources`) runs the work on a virtual thread of
`AsyncConfiguration`'s executor and the request waits for it up to its budget: `Prefer: wait=N`,
capped at `app.operations.max-wait` (10 s, below the gateway's 15 s read timeout), or
`app.operations.default-wait` (5 s) without it; `wait=0` answers as soon as it is registered. If the
work ends in time the answer is the synchronous one, errors included; if not, 202 with the operation,
and `GET /operations/{id}` gives the same body when it ends.

- **The scarce resource is the connection, not the thread.** Tomcat, `@Scheduled` and the RabbitMQ
  listeners run on virtual threads (`spring.threads.virtual.enabled`), so every request waiting on the
  database holds a Hikari connection and the pool (`DATABASE_POOL_MAX_SIZE`, 10)
  is the limit of concurrency. A running operation holds one too, so there is a cap of live
  operations (`QUEUED` or `RUNNING` with a fresh beat) for the whole deployment,
  `app.operations.max-active` (4), chosen with the pool in view and decided inside the `insert` that
  registers the operation, under an advisory lock: with three replicas an in-memory semaphore would
  be three times the cap. Beyond it, 429 `OPS-429` with `Retry-After`.
- **One at a time per scope.** Operations of one scope (`EP-<package>`, or `order-<id>` without a
  package) start in order of arrival. One waiting is `QUEUED` and holds no connection: in one instance
  it waits on a fair lock per scope (`OperationScopeLocks`, apart from the generation's
  `GenerationLocks`), without asking the database, and with that turn a short transaction starts it.
  Only when another replica has the scope (the turn in `background_operation` is what holds between
  replicas) does it let the lock go and look again with growing backoff (`poll-initial` to `poll-max`).
- **Abandoned ones.** A live operation beats (`heartbeat_at`, every `app.operations.heartbeat`, 5 s);
  one silent for `app.operations.stale-after` (1 min), because its replica died, is failed by any
  replica (`app.operations.abandon-check`, 30 s) with the error `OPS-410`, and stops counting for the
  cap and its scope. Finished ones are purged after `app.operations.retention` (7 days). The three
  schedules are off in the tests (`app.operations.scheduling-enabled=false`), which call them.
- **The request stays the author.** The work runs with the caller's `SecurityContext` and MDC, and
  what the request can no longer give once it has answered (its `X-Correlation-Id`, method, URI,
  address, user agent) is copied when the operation is registered (`BackgroundOperationContext`): a
  revision written in the background still says `HTTP` with that URI and correlation, and its events
  carry the same `correlationId`.
- **Visibility.** An operation is shown to whoever asked for it, to `maintenance-supervise` and to
  `ops-write`; to anyone else it does not exist (404). Its `result` is the body the synchronous call
  would have given, with its `resultStatus`; its `error`, the status, code and message that call would
  have answered (`ApiOperationErrorTranslator`, the same table as `GlobalExceptionHandler`).

## Status history

Every transition of an order or a defect appends a `MaintenanceStatusHistory` row
(`previousStatus`, `newStatus`, `changedAt`, `changedBy`, `comment`), exposed at
`/orders/{id}/history` and `/defects/{id}/history`.
