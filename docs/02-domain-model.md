# 02 · Domain model

## Assets (`CatenaryAsset`)

| Type | Origin | Key fields |
|---|---|---|
| `TRACK_SECTION` | API (`POST /assets`) | `trackId`, `startKp < endKp`, `trackKind` (`MAIN` / `DIVERTED`), optional `stationId`, `executionPackageId` |
| `PROFILE` | event `profile` of `mto-configuration` | `code = PRF-<sourceId>`, `name` = natural profile id (`12-2.27`, unique per track only: search it with `?name=&trackId=`), `startKp = endKp = kp`, `trackId`, `executionPackageId`, `sectioning` snapshot (`A/S S/A`) |
| `DISCONNECTOR` | event `disconnector` | `code = DSC-<sourceId>`, `stationId`, `profileSourceId`, kp of its profile |
| `SECTION_INSULATOR` | event `section-insulator` | `code = SIN-<sourceId>`, `stationId`, `enabled` from the source, `installationType` (`TRACK_CONNECTION` / `IN_TRACK`), `trackId` + `connectedTrackId`, `startKp`/`endKp` spanning its turnouts, and its `switches` (`catenary_asset_switch`: `W31`, kp, `1:9`, track) |

Rules: an asset with an origin cannot be created or renamed through the API (`PUT` only touches
`description`, `enabled`, `preventiveIntervalDays`); `DELETE` disables, never deletes; no new
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

| Transition | From | Rules |
|---|---|---|
| `plan` | `DRAFT` | `plannedDate` not in the past; reserves every `NOT_REQUESTED` material in stock |
| `assign` | `PLANNED`, `ASSIGNED` → `ASSIGNED`; `IN_PROGRESS` keeps its status | `teamId` or `assignedUser` |
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
measure cannot be left without a result. `POST /orders/{id}/tasks/generate` creates one task per
enabled profile of the section (ordered by kp, idempotent per profile) with the default preventive
types `RG-01, RG-05, RG-08, RG-10, RG-11, RG-12, RG-13, RP-08` or the ones requested.

Completing a task can create defects inline: `workComplete=true` → `RESOLVED` in that shift;
`workComplete=false` + `repairPlannedDate` → `OPEN`. Completing the last task never closes the
order.

## Task types (`MaintenanceTaskType`)

The `RG-01…RG-18` (general revision) and `RP-01…RP-12` (particular revision) catalogue of the OCS
plan, read-only: `functionalGroup` (structural supports, overhead conductors, devices and switches,
anchorage components, turnouts and section insulators, diagnostics, none),
`standardMinutesPerUnit` + `unit` (`UNIT`, `SPAN`, `KM`, `DEFECT`, `PROFILE`), `fixedMinutes`
("60 min + 3/km"), `requiresFullPossession` (groups 3, 5 and `RP-12`), `diagnostic` (group 6).

## Shifts (`MaintenanceShift`) and teams (`MaintenanceTeam`)

A shift is the unit of execution and the source of the daily report: date, team, base, vehicle,
`possessionType` (`PARTIAL` on weekdays on main track, `FULL` on weekends), planned and actual
window, `voltageCutoffAt`, `netWorkMinutes` (computed at close), the set of `blockingDisconnectors`
opened to isolate the work zone (as many `DISCONNECTOR` assets as the cut needs, not a fixed pair),
earthing points, parking place, the `trackIds` it covers (one or more tracks of the same night; a
second execution package is a second shift), kp range, personnel, equipment, observations.
States `PLANNED → IN_PROGRESS → CLOSED`, `CANCELLED` before closing. Closing requires the actual
times and returns to `PENDING` (without shift) the tasks that were not completed.

Window rules: a task's profile must be on one of the shift's tracks; a task whose types require
full possession cannot be assigned to or completed in a `PARTIAL` shift; a `DIVERTED` section only
accepts `FULL` shifts. The profiles reviewed in a shift are the `PROFILE` assets of its `COMPLETED`
tasks (`GET /shifts/{id}/profiles`). Teams (`A` Rishpon, `B` Mishmar) carry the execution packages they cover.

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
  at the first line stock still does not answer, and the next one carries on after it. `REJECTED`
  lines are not retried: repeating what stock refused gives the same answer until something changes.
- The body does not depend on the moment it is sent (stock stamps `reservedAt` and `occurredAt` on
  arrival, and would ignore them in the comparison anyway), and the output of what was used when no
  reservation covers it has the same notes whichever way it is reached — the reservation released in
  this very step because less was used, or already holding nothing.

What is left: a request whose stock call succeeded but whose local transaction then rolled back (a
concurrent edit at commit) leaves no trace here; its retry, if the body changed meanwhile, is
refused with `IDEM-001` and the line says so. `FAILED` lines from before `V12` carry no request in
doubt: nobody knows which request failed.

A line registered by mistake is removed, not cancelled (`DELETE /orders/{id}/materials/{usageId}`):
the row goes and Envers keeps its last state as a DELETED revision. A reserved line is released in
stock first; if stock does not answer, the removal fails with 503 and the line stays, because its
reservation would stay alive there. A reservation stock no longer holds (released or cancelled from
the warehouse, or unknown to stock) does not block it. A consumed line, one whose reservation was
consumed from the warehouse, or a line of a completed or cancelled order, cannot be removed. There is no `CANCELLED` state on purpose: `stockSyncStatus`
describes the conversation with stock, and the unique (order, task, material, warehouse) would
stop the line from being registered again.

## Status history

Every transition of an order or a defect appends a `MaintenanceStatusHistory` row
(`previousStatus`, `newStatus`, `changedAt`, `changedBy`, `comment`), exposed at
`/orders/{id}/history` and `/defects/{id}/history`.
