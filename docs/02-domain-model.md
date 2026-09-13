# 02 · Domain model

## Assets (`CatenaryAsset`)

| Type | Origin | Key fields |
|---|---|---|
| `TRACK_SECTION` | API (`POST /assets`) | `trackId`, `startKp < endKp`, `trackKind` (`MAIN` / `DIVERTED`), optional `stationId`, `executionPackageId` |
| `PROFILE` | event `profile` of `mto-configuration` | `code = PRF-<sourceId>`, `name` = natural profile id (`12-2.27`), `startKp = endKp = kp`, `trackId`, `executionPackageId`, `sectioning` snapshot (`A/S S/A`) |
| `DISCONNECTOR` | event `disconnector` | `code = DSC-<sourceId>`, `stationId`, `profileSourceId`, kp of its profile |
| `SECTION_INSULATOR` | event `section-insulator` | `code = SIN-<sourceId>`, `stationId`, `enabled` from the source |

Rules: an asset with an origin cannot be created or renamed through the API (`PUT` only touches
`description`, `enabled`, `preventiveIntervalDays`); `DELETE` disables, never deletes; no new
order, inspection or task on a disabled asset; `preventiveIntervalDays` +
`lastPreventiveCompletedAt` give `nextPreventiveDueAt` and the `preventiveDueBefore` filter (an
asset never done is due).

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
| `complete` | `IN_PROGRESS` | no task `PENDING`/`IN_PROGRESS`; at least one `COMPLETED` task or `closingNotes`; `INSPECTION` needs an inspection with `originOrderId`; a `FAILED` material blocks unless `force` (supervise); consumes reservations or registers direct outputs; `PREVENTIVE` updates `asset.lastPreventiveCompletedAt` |
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
window, `voltageCutoffAt`, `netWorkMinutes` (computed at close), blocking disconnectors A/B,
earthing points, parking place, one `trackId`, kp range, personnel, equipment, observations.
States `PLANNED → IN_PROGRESS → CLOSED`, `CANCELLED` before closing. Closing requires the actual
times and returns to `PENDING` (without shift) the tasks that were not completed.

Window rules: a `PARTIAL` shift only on `MAIN` track; a task whose types require full possession
cannot be assigned to or completed in a `PARTIAL` shift; a `DIVERTED` section only accepts `FULL`
shifts. Teams (`A` Rishpon, `B` Mishmar) carry the execution packages they cover.

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
`stockSyncStatus` (`NOT_REQUESTED`, `RESERVED`, `CONSUMED`, `RELEASED`, `FAILED`) with the last
error. Reservation happens at `plan` (or when the line is added to an order already planned),
consumption at `complete`, release at `cancel`; without a stock project the consumption is a
direct output.

## Status history

Every transition of an order or a defect appends a `MaintenanceStatusHistory` row
(`previousStatus`, `newStatus`, `changedAt`, `changedBy`, `comment`), exposed at
`/orders/{id}/history` and `/defects/{id}/history`.
