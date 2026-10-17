# 04 · REST API

Base path `/api/v1/maintenance` (through the gateway: `/api/maintenance`). JSON, UUID ids, pageable
collections (`page`, `size`, `sort`) returning `PageResponse{content, page{number, size,
totalElements, totalPages, first, last}}`. Errors are `ApiErrorResponse{timestamp, status, error,
message, path, method, errorCode, correlationId, validationErrors[{field, message}]}`; `correlationId`
echoes the `X-Correlation-Id` request header.

## Resources

```
GET/POST        /assets                       type, trackId, stationId, executionPackageId, enabled, kpFrom, kpTo, code, name, preventiveDueBefore,
                                              connectedTrackId, switchCode
                                              name: natural name (profile 12-2.27, disconnector HSA-NS5), partial and case insensitive;
                                              unique only together with trackId
                                              connectedTrackId / switchCode: section insulators only — the track it connects with, and
                                              the turnout it sits on (W31, partial and case insensitive)
GET/PUT/DELETE  /assets/{id}                  DELETE disables
GET             /assets/{id}/orders | /revisions
                                              orders, tasks, inspections and defects embed the asset as a summary: id, code, name,
                                              type, trackId, startKp, endKp, sectioning, enabled, connectedTrackId (the other track
                                              of one that joins two, so a client offers the shifts of both)

GET             /task-types | /task-types/{code}     functionalGroup, requiresFullPossession, diagnostic
GET/POST        /teams        GET/PUT /teams/{id}
                                              synchronizedFromResources, sourceEntityId (its unit in mto-resources), leader,
                                              supervisor, members (linemen, by code), defaultVehicleId, executionPackageIds; a
                                              synchronized team has no baseName and its vehicle is the code of its default
                                              vehicle; PUT on one is 409 TEA-001

GET/POST        /shifts                       date, dateFrom, dateTo, teamId, trackId, executionPackageId, status, possessionType
                                              trackId can be repeated (?trackId=12&trackId=13): the shifts working on any of
                                              them, each once — the two tracks of an insulator or a disconnector that joins them
GET/PUT         /shifts/{id}                  POST /shifts/{id}/start | /close | /cancel
GET             /shifts/{id}/tasks?status= | /profiles?status= | /report?format= | /revisions
                                              /profiles: PROFILE assets of the shift's tasks by kp (default status=COMPLETED = reviewed)
                                              a shift answers teamLeader, supervisor, linemen[] (by code), vehicles[] (by code:
                                              vehicleId, code, name, mobility, category, available, parking{siteId, trackId, kp,
                                              description} or null until closed with one) and instruments[] (by code: id, code,
                                              name, kind, calibrationExpiresOn) — "Shifts: crew and vehicles" below
POST            /shifts/{id}/tasks/{taskId}   assigns a task to the shift (track and window rules)
POST            /shifts/{id}/tasks/{taskId}/unassign
                                              takes an open task out of the shift: back to its order, PENDING and without
                                              shift (as on closing); a completed or cancelled one stays (409 TRN-001)

GET/POST        /orders                       status, type, priority, assetId, assetType, trackId, stationId, executionPackageId,
                                              plannedFrom, plannedTo, actualStartFrom, actualStartTo, assignedUser, teamId, code
GET/PUT         /orders/{id}
POST            /orders/{id}/plan | /assign | /start | /complete | /cancel
GET             /orders/{id}/history | /revisions
GET/POST        /orders/{id}/tasks            POST /orders/{id}/tasks/generate {taskTypeCodes, withChecklist, assetTypes,
                                              selections, dryRun} — see "Generating preventive tasks" below
GET/PUT         /orders/{id}/tasks/{taskId}   POST .../start | /complete | /cancel
PUT             /orders/{id}/tasks/{taskId}/check-items/{itemId}
GET/POST        /orders/{id}/materials        PUT .../{usageId}   POST .../{usageId}/sync   DELETE .../{usageId}

GET/POST        /inspections                  result, assetId, assetType, trackId, stationId, executionPackageId, inspectionFrom, inspectionTo, inspector, originOrderId
GET/PUT         /inspections/{id}             PUT /inspections/{id}/items/{itemId}
POST            /inspections/{id}/create-defect | /create-corrective-order
GET             /inspections/{id}/revisions
GET             /inspection-templates | /inspection-templates/{id}

GET/POST        /defects                      severity, status, assetId, trackId, stationId, executionPackageId, detectedFrom, detectedTo, orderId
GET/PUT         /defects/{id}
POST            /defects/{id}/resolve | /close | /discard | /link-order/{orderId}
GET             /defects/{id}/history | /revisions

GET             /reports/progress             executionPackageId, trackId (also the insulators connecting to it), assetType, from, to, format
GET             /reports/monthly              executionPackageId, month=yyyy-MM, format

GET             /operations/{id}              a background operation (202 of a Prefer: respond-async call): to whoever asked for it,
                                              maintenance-supervise or ops-write; anyone else gets 404
```

## Generating preventive tasks

`POST /orders/{id}/tasks/generate`, for a `PREVENTIVE` order in `DRAFT` or `PLANNED` (else 409
`TRN-001`). The body is optional; without one it generates the profiles of the order's section.

```jsonc
{
  "taskTypeCodes": ["RG-01"],          // for everything generated; default: per asset (below)
  "withChecklist": true,               // the template of each asset's type
  "assetTypes": ["PROFILE", "SECTION_INSULATOR", "DISCONNECTOR"],   // only without selections
  "selections": [                      // at most 20; not together with assetTypes
    {"assetType": "PROFILE", "sectionId": "…"},                    // another section of the package
    {"assetType": "SECTION_INSULATOR", "trackId": 13, "fromKp": 12000, "toKp": 14000},
    {"assetType": "DISCONNECTOR", "stationId": 4},                  // the whole package, of a station
    {"assetType": "DISCONNECTOR", "assetIds": ["…"], "taskTypeCodes": ["RP-06"], "withChecklist": false}
  ],
  "dryRun": true                       // create nothing: say what would happen
}
```

- **Without `selections`**: the order must be on a `TRACK_SECTION` (else 409 `TRN-001`). Profiles of
  its track between its kp; with `assetTypes`, also the insulators and disconnectors on its track or
  connecting to it whose kp range overlaps it. `TRACK_SECTION` in `assetTypes` is a 400.
- **With `selections`**: the order (on any asset) must have an execution package (400 on
  `selections`); each selection is one asset type in at most one scope — `sectionId` (a
  `TRACK_SECTION` of the package and of the same kind, `MAIN`/`DIVERTED`, as the order's),
  `trackId` with an optional `fromKp`/`toKp` (what is on that track or connects with it, overlapping
  the range), `assetIds` (at most 500, of the type, enabled and of the package) or none (the whole
  package). `stationId` filters any scope but `assetIds`. Every mistake is a 400 on its field
  (`selections[i].sectionId: …`, `selections[i].assetIds: …`): two scopes, a kp range without a
  track, `fromKp > toKp`, a section or an asset of another package, of another type, disabled or
  unknown.
- **Default task types**: groups 1, 2 and 4 on a profile; `RG-03` and `RP-02` on a section
  insulator, and `RG-04` and `RP-03` too when it has turnouts (`switches`); `RP-07` on every
  disconnector, plus `RP-06` with `driveType: MOTOR` and `RG-06` when its `disconnectorFunction` is
  `SurgeA` (a surge arrester). Chosen types replace the
  defaults, they are not added to them. A selection's `taskTypeCodes`/`withChecklist` win over the
  request's.
- **Skipped, never duplicated**: what already has a task in the order (also cancelled), what belongs
  to another package than the order's, what has an open task in another open preventive order, and
  what has no task type. Generations in one package run one after another.

```jsonc
{
  "createdTasks": 5, "skippedProfiles": 0, "totalTasks": 5, "estimatedMinutes": 610.5, "estimatedShifts": 2,
  "skippedAssets": 0,                                  // already in the order (skippedProfiles: the profiles among them)
  "createdByAssetType": {"PROFILE": 2, "SECTION_INSULATOR": 2, "DISCONNECTOR": 1},
  "skippedInOtherOrders": 1, "otherOrderCodes": ["MO-000042"],
  "skippedOtherPackage": 0, "skippedWithoutTaskTypes": 1,
  "dryRun": true,
  "assets": [                                          // only with dryRun, in the order they would be generated
    {"assetId": "…", "code": "PRF-…", "name": "3-1.01", "type": "PROFILE", "trackId": 3, "connectedTrackId": null,
     "kp": 13000.000, "taskTypeCodes": ["RG-01", "RG-12"], "withChecklist": true, "outcome": "CREATE", "otherOrderCode": null}
  ]
}
```

`outcome` is `CREATE`, `ALREADY_IN_ORDER`, `OTHER_PACKAGE`, `IN_OTHER_ORDER` or `NO_TASK_TYPES`. With
`dryRun` the counters and the estimate are those of generating it.

### `Prefer: respond-async`

A generation of a whole package can take longer than a client wants to hold a request. With
`Prefer: respond-async` (RFC 7240; `wait=N` chooses how many seconds to wait first) it runs as a
background operation (`02-domain-model.md`):

- It finishes within the wait: the usual 200 with the body above, or the usual error.
- It does not: `202 Accepted`, `Location: /api/v1/maintenance/operations/{id}`,
  `Preference-Applied: respond-async`, and the operation as body. The generation goes on.
- The wait is `wait=N` capped at `app.operations.max-wait` (10 s, below the gateway's 15 s read
  timeout), `app.operations.default-wait` (5 s) without it; `wait=0` answers 202 at once. Anything
  else in `Prefer` is ignored, as the RFC says.
- Without the header, or with `dryRun: true`, the call is synchronous as always.
- Live operations are capped for the whole deployment (`app.operations.max-active`, 8): beyond it,
  429 `OPS-429` with `Retry-After` (the default wait in seconds). Generations of one package run one
  after another, the later ones `QUEUED`.

```jsonc
// GET /operations/{id}
{
  "id": "…", "kind": "GENERATE_TASKS", "scope": "EP-14", "status": "SUCCEEDED",   // QUEUED, RUNNING, SUCCEEDED, FAILED
  "requestedBy": "mantenimiento.responsable",
  "createdAt": "2026-10-16T21:00:00Z", "startedAt": "2026-10-16T21:00:00Z", "finishedAt": "2026-10-16T21:00:12Z",
  "resultStatus": 200, "result": {"createdTasks": 412, "…": "…"}                  // the body of the synchronous call
  // or, FAILED: "error": {"status": 409, "errorCode": "TRN-001", "message": "…"}   the error it would have answered
}
```

Absent fields are not sent. An operation whose instance stopped beating for
`app.operations.stale-after` (1 min) is `FAILED` with `error.errorCode` `OPS-410` (abandoned; its
`status` is 500). `GET /operations/{id}` needs `maintenance-read` like any read, and shows the
operation only to whoever asked for it, to `maintenance-supervise` and to `ops-write`; to anyone else,
and once purged (`app.operations.retention`, 7 days), it is 404 `APP-404`.

## Shifts: crew and vehicles

`POST /shifts` and `PUT`/`PATCH /shifts/{id}` take, besides the fields of before, the crew, the
vehicles and the instruments of that night from `mto-resources` (`02-domain-model.md`, "Shift crew and
vehicles" and "Qualifications and expiries"):

```jsonc
{
  "shiftDate": "2026-01-27", "teamId": "…", "possessionType": "PARTIAL", "trackIds": [2], "…": "…",
  "vehicleIds": ["…(DR-02)"],                  // a set, replaced whole; a vehicle that stays keeps its parking
  "teamLeaderPersonId": "…",                   // TEAM_LEADER or above (anything but WORKER)
  "supervisorPersonId": "…",
  "linemanPersonIds": ["…", "…", "…"],        // a set, replaced whole; 2 to 6 to start
  "instrumentIds": ["…(EP-07)", "…(VD-02)"]    // a set, replaced whole (V22); none retired nor with its calibration expired
}
```

With a synchronized team, what `POST` does not say is copied from the team (leader, supervisor,
linemen, default vehicle); instruments are never copied, a team has none. A `PATCH` may empty the
five (`null` in `application/merge-patch+json`; a `PUT` without the key leaves the set as it is);
emptying `teamLeaderPersonId` while the shift has linemen is 400 `VAL-001` (replace it instead). An
unknown person, vehicle or instrument (one that has not arrived from `mto-resources` yet) is 404
`CRW-404` / `FLT-404` / `MSI-404`, and nothing is replaced; a broken crew rule (more than 6 linemen,
the leader among them, a `WORKER` as leader, someone deactivated, a vehicle not available, one that
needs towing without a towing vehicle, not enough seats, a vehicle or a person already in another open
shift of the same date) is 409 `SHF-001`, and so is starting, or changing a shift `IN_PROGRESS`,
without a leader and 2 to 6 linemen when the shift has a crew.

Nothing expired gets into a shift (`V22`): on create, on any change and on start, an expired
qualification of the leader, the supervisor or a lineman, an expired document of one of its vehicles,
an instrument retired in `mto-resources` or whose calibration expired, all of them at the shift's
`shiftDate` (`expiresOn < shiftDate`; the last day still counts), is 409 `SHF-001` whose message lists
everything that has expired, to renew it or take it out at once:

```text
Shift SH-000077 on 2026-10-17 cannot take what has expired: Yossi Cohen (PER-000003): qualification
RAIL-DRIVER expired on 2026-09-30; vehicle RR-07: ROAD_INSPECTION ITV-2026-118 expired on 2026-10-01;
instrument VD-02: calibration expired on 2026-10-07. Renew it in mto-resources or take it out of the shift
```

To start, and on any change while `IN_PROGRESS`, each self-propelled vehicle whose model requires a
driver qualification needs a different person of the crew (the leader or a lineman, not the
supervisor) holding it, valid that day; otherwise 409 `SHF-001`: `Shift SH-000077 has no qualified
driver for RR-07 (RR-OPERATOR) on 2026-10-17: …`. A shift still `PLANNED` may lack its drivers.

`POST /shifts/{id}/close` takes `parkings`, where each vehicle was left:

```jsonc
{"actualEnd": "2026-01-28T02:30:00Z", "parkings": [
  {"vehicleId": "…(DR-02)", "trackId": 31, "kp": 4.250},                        // a track and kp (mto-configuration)
  {"vehicleId": "…(RR-07)", "parkingSiteId": "…", "description": "Gate 3"}       // a site of mto-resources, a text
]}
```

Each needs a site, a track or a description, and a vehicle of the shift (400 `VAL-001`); a rail-only
vehicle with only a description is 409 `SHF-001`. Without `parkings` the shift's `parkingPlace`
stands.

## Export (`?format=`)

The three reports — `/shifts/{id}/report`, `/reports/progress`, `/reports/monthly` — take `format`
with `json` (the default), `xlsx` or `pdf`. Same route, same security rule, same JSON contract when
the parameter is absent; the download is an ordinary URL, so it can be linked or bookmarked.

| `format` | `Content-Type` | Notes |
|---|---|---|
| `json`, absent | `application/json` | Unchanged |
| `xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | One sheet, header pairs above the table, frozen and filtered header, real dates and numbers; the shift report adds the `Inspections`, `Crew`, `Vehicles` and `Instruments` sheets |
| `pdf` | `application/pdf` | A4 landscape (shift, progress) or portrait (monthly), header repeated on every page; the shift report's inspections, crew, vehicles and instruments follow its totals |

The value is case insensitive; an unknown one is `400 VAL-001`. The file arrives as
`Content-Disposition: attachment` with a stable, sortable ASCII name — `shift-report-<date>-<code>`,
`progress-report-<date>[-ep<n>][-track<n>]`, `monthly-report-<yyyy-MM>[-ep<n>]` — so a month of
reports saved in one folder sorts itself.

Times are printed in the zone of `app.reports.time-zone` (`APP_REPORT_TIME_ZONE`, `Asia/Jerusalem` by
default): a shift that started at `2026-01-27T21:10:00Z` reads 23:10 in its workbook and its PDF, and
the generation time at the bottom too. The same zone cuts the days and months that count — the month
of `/reports/monthly`, the inspection dates of `/reports/progress` — so a profile completed at 01:30
local time on the first of the month is counted in the month its row says. The JSON is unchanged:
its instants stay UTC with their `Z`. A misspelt zone stops the application from starting.

Two deliberate differences from the JSON:

- The PDF prints a subset of the shift report's columns. Nineteen columns on an A4 would leave about
  six characters each and squeeze `worksPerformed` and `defectsFound`, which are the ones being read.
  The workbook carries all of them, `switches` included — the turnouts of a section insulator with
  their rate (`W31 1:9`, and `W31 1:9 (out of service)` for one that is disabled in
  `mto-configuration`), empty on every other row.
- The shift report's inspections are an appendix: in the workbook a sheet of their own (`Inspections`,
  with its frozen and filtered header, and the note when there are none), in the PDF a titled table
  after the totals without the asset name and type, which the code already says. The totals carry
  `Inspections recorded`. The task table's `Profile` column reads `Asset`, because a row can be a
  section insulator; the JSON keeps `profileCode`/`profileName`. The crew (`Role`, `Code`, `Name`,
  `Username`, `Category`: the leader, the supervisor, each lineman) and the vehicles (`Code`, `Name`,
  `Mobility`, `Category`, `Parking`, as `track 31, kp 4.250 — site … — description`) are two more
  appendices after it, and the header adds `Team leader`, `Supervisor`, `Linemen` (the count) and
  `Vehicles` (the codes); the JSON carries them in `shift`. The instruments of `mto-resources` (`V22`:
  `Code`, `Name`, `Kind`, `Calibrated until`, a real date, empty without a calibration) are the last
  appendix; the header's `Measurement equipment`, the free text of before, stays for the shifts that
  carry no instruments.
- `photoRefs` are printed as text in both formats. They are references that resolve to nothing until
  the photo storage in `05-development-roadmap.md` exists, but they are the only record that the
  photo was taken, so dropping them would lose what the paper report used to carry.

Transitions are `POST` with a body: `PlanOrderRequest{plannedDate, comment}`,
`AssignOrderRequest{teamId, assignedUser, comment}`, `OrderCommentRequest{comment}`,
`CompleteOrderRequest{closingNotes, force, comment}`, `CancelOrderRequest{reason}`;
`CompleteTaskRequest{shiftId, taskTypeCodes, notes, defectsFound, workComplete, repairPlannedDate,
inlineDefects[], materials[], photoRefs[]}`; `MaintenanceShiftRequest{shiftDate, teamId, possessionType, trackIds[], blockingDisconnectorIds[], …,
vehicleIds[], teamLeaderPersonId, supervisorPersonId, linemanPersonIds[], instrumentIds[]}`,
`StartShiftRequest{actualStart, voltageCutoffAt}`, `CloseShiftRequest{actualEnd,
voltageCutoffAt, netWorkMinutes, observations, parkings[{vehicleId, parkingSiteId, trackId, kp,
description}]}`; `ResolveDefectRequest{resolutionNotes, correctionType, partsReplaced,
resolvedInShiftId}`.

## Changing a resource: `PUT`, `PATCH` and `version`

Every resource that is edited — assets, orders, tasks and their check items, shifts, inspections and
their items, defects, material lines — takes the same body two ways:

- `PUT` with `application/json`: a key that is absent or `null` is not touched. It cannot empty a
  field.
- `PATCH` with `application/merge-patch+json` (RFC 7396): absent is not touched, `null` empties it,
  a value changes it as in the `PUT`. Only the optional fields can be emptied; emptying a required
  one is 400 `VAL-001` naming it, and so is a key the body does not have (a misspelt `null` would
  otherwise empty nothing without saying so). Another content type is 415. The same state rules
  apply: an order after `PLANNED` only empties `description` and `closingNotes`, a synchronized
  asset only `description` and `preventiveIntervalDays`.

| Resource | What a `PATCH` can empty |
|---|---|
| Order | `description`, `plannedDate`, `teamId`, `assignedUser`, `closingNotes`, `executionPackageId`, `trackId`, `stationId`, `startKp`, `endKp`, `stockProjectId` |
| Task | `assignedUser`, `taskTypeCodes`, `notes`, `defectsFound`, `photoRefs` |
| Check item (task or inspection) | `measuredValue`, `valueAfterAdjustment`, `itemResult`, `notes` |
| Shift | `teamId`, `baseName`, `vehicle`, `plannedStart`, `plannedEnd`, `blockingDisconnectorIds`, `earthingPoints`, `parkingPlace`, `executionPackageId`, `startKp`, `endKp`, `personnel`, `measurementEquipment`, `observations`, `vehicleIds`, `teamLeaderPersonId` (only with no linemen left), `supervisorPersonId`, `linemanPersonIds` (not below 2 while `IN_PROGRESS`), `instrumentIds` |
| Inspection | `inspector`, `description`, `detectedDefects`, `recommendedActions`, `kp` |
| Defect | `technicalNotes`, `correctionType`, `partsReplaced`, `repairPlannedDate`, `photoRefs` |
| Asset | `description`, `preventiveIntervalDays`, and on an own section `executionPackageId` and `stationId` |
| Material line | nothing: the `PATCH` is there for the `version` |

Each of those resources returns its `version` (a check item its own), and both bodies accept it,
optional. With it, a request made on an older version answers 409 `CON-001` and writes nothing: read
it again and decide. Without it everything works as before, last write wins. Two writes that cross
inside the service are also 409 `CON-001` (optimistic locking), and a master-data event bumps the
version of the asset it changes, so an edit read before the event does not overwrite it unnoticed.

A text that is required and arrives blank (`"title": " "`) is 400 `VAL-001`, and lowering the planned
quantity of a line below what was consumed without `allowOverConsumption` is 409 `MAT-001`: both used
to reach a database constraint at commit and answer 500. So did these, which now answer for
themselves:

- Taking `allowOverConsumption` off a line that already consumed more than planned is 409 `MAT-001`
  (it was 409 `APP-409`).
- Closing a shift with a `voltageCutoffAt` after `actualEnd` (the one sent, or the one recorded at
  `start`) is 400 `VAL-001`: the net time came out negative and the shift could not be saved. A
  cut-off exactly at the end is a net time of zero.
- A defect cannot be detected in the future: a `detectedAt` after now is 400 `REQ-VALIDATION` on
  `POST /defects`, the defect `create-defect` makes is detected at the start of the inspection date
  in UTC but never later than now (on a night shift in Madrid the local date is already the next
  UTC day for an hour or two), and resolving one that was still dated in the future is 400 `VAL-001`
  (it was 409 `APP-409`).
- A `null` inside a list (`taskTypeCodes`, `inlineDefects`, `materials`, `photoRefs`, `trackIds`,
  `blockingDisconnectorIds`, a shift's `vehicleIds`, `linemanPersonIds`, `instrumentIds` and `parkings`, a team's
  `executionPackageIds`) is 400 `REQ-VALIDATION` on that list. It was a 500 for task types, defects
  and materials, a 409 `APP-409` for tracks and packages, and a photo stored as the text `null`.

Anything else a constraint catches is 409 `APP-409` rather than 500.

A material line answers, besides `stockSyncStatus` and `stockSyncError`, `stockRequestInDoubt`:
`RESERVATION` or `OUTPUT` when the line sent that request to `mto-stock` and got no answer (it is
`FAILED`), `null` otherwise. Until stock answers, the next thing done with the line repeats that
request first, and what travels in it cannot change: its planned and consumed quantities (the same
value is accepted) and the order's `stockProjectId` are 409 `MAT-001`, and a line with an output in
doubt cannot be removed (the material may have left already). `FAILED` lines are retried on their own
every 5 minutes, the same as a `sync`; `sync` still does it on demand. See `02-domain-model.md`.

## Status codes

| Code | When |
|---|---|
| 201 + `Location` | Creation |
| 200 | Reads, updates, transitions, idempotent `create-defect`/`create-corrective-order` |
| 202 + `Location` + `Preference-Applied` | `POST /orders/{id}/tasks/generate` with `Prefer: respond-async` that did not finish within its wait: the body is the operation, `Location` its `GET /operations/{id}` |
| 204 | `DELETE /assets/{id}` (disables the asset), `DELETE /orders/{id}/materials/{usageId}` (removes the line) |
| 400 `REQ-VALIDATION` / `VAL-001` | Bean Validation / domain invariant (kp range, quantity, unknown task type, unknown export `format`, a blank required text, a `PATCH` that empties a required field or has an unknown key, a `null` inside a list, a defect detected in the future, a shift closed with its voltage cut-off after the end) |
| 400 `REQ-400` | Malformed body, a parameter of the wrong type, a missing required parameter (`month` of the monthly report) or a `sort` on a property the entity does not have |
| 401 `AUTH-401` / 403 `AUTH-403` | No token / missing role |
| 404 `<AGG>-404` | Unknown id (`ORD`, `AST`, `TSK`, `SHF`, `TEA`, `INS`, `TPL`, `DEF`, `MAT`, and `CRW` for a person, `FLT` for a vehicle or `MSI` for an instrument of `mto-resources` named in a shift); a task type, a check item, an inspection item and a background operation (unknown, purged or someone else's) answer `APP-404` |
| 404 `HTTP-404` | Unknown route |
| 405 `REQ-405` | The route exists but not for that method; the `Allow` header lists the ones it takes |
| 409 `TRN-001` | Invalid transition |
| 409 `AST-001` | Disabled asset; changing a field of a synchronized asset that only `mto-configuration` changes (sending it with the value it already has is accepted); `enabled=true` on an asset disabled at the source |
| 409 `SHF-001` | Shift rule (no shift in progress on the track, partial possession, diverted track), also when changing the tracks or the possession of a shift would leave one of its open tasks outside them, and an inspection recorded in a shift that is not in progress or closed, or that does not work on its asset's track nor on the one it connects with; a crew or vehicle rule (more than 6 linemen, the leader among them or a `WORKER`, someone deactivated, a vehicle not available or needing a tow, not enough seats, a vehicle or a person in another open shift of the same date, starting or changing a shift in progress without a leader and 2 to 6 linemen), something expired at the shift's date (a qualification of its crew, a document of its vehicles, the calibration of its instruments) or an instrument retired, starting or changing a shift in progress without a different qualified driver for each self-propelled vehicle that requires one, and a rail-only vehicle left by description only on close |
| 409 `TEA-001` | `PUT /teams/{id}` of a team synchronized from `mto-resources`: it is changed there |
| 409 `MAT-001` | Over-consumption (also taking `allowOverConsumption` off a line consumed above plan), duplicated line, `FAILED` or `REJECTED` line without `force`, changing or removing a consumed line, removing a line of a completed or cancelled order; while a line has a request to stock without an answer (`stockRequestInDoubt`), changing its planned or consumed quantity, changing the order's `stockProjectId`, or removing it with an output in doubt |
| 409 `STK-001` | `mto-stock` has not enough stock, on an explicit `sync` (the line stays `REJECTED` with the reason) |
| 409 `<AGG>-409` | Duplicated code |
| 409 `CON-001` | The `version` of the request is not the one stored, or two writes crossed: nothing was written |
| 409 `APP-409` | A database constraint caught what the validation did not |
| 415 `REQ-415` | Unsupported media type (a `PATCH` is only `application/merge-patch+json`) |
| 422 `INS-001` | Inconsistent inspection result / checklist |
| 422 `STK-422` | `mto-stock` rejected the step for another reason (a material or warehouse retired, a reservation no longer active...), on an explicit `sync` or when removing a reserved line; its code and message are in `message` |
| 429 `OPS-429` + `Retry-After` | As many background operations live as the deployment admits (`app.operations.max-active`): try again in the seconds `Retry-After` says |
| 503 `STK-503` | `mto-stock` unreachable on an explicit `sync`, or when removing a reserved line (the line stays) |

## Security

| Verb / action | Role |
|---|---|
| `GET` | `MAINTENANCE_READ`; `GET /operations/{id}` also needs to be who asked for it, `MAINTENANCE_SUPERVISE` or `OPS_WRITE` (404 otherwise) |
| `POST`, `PUT` | `MAINTENANCE_WRITE` |
| `DELETE`, and `PUT /assets/{id}` with `enabled=false` (the same decision) | `MAINTENANCE_DELETE` (method security on the `PUT`, on top of `MAINTENANCE_WRITE`) |
| `cancel` **of an order**, `complete` of an order with `force=true`, `resolve`, `close`, `discard` of a defect | `MAINTENANCE_SUPERVISE` on top of `MAINTENANCE_WRITE` (method security); cancelling a shift or a task, and taking a task out of a shift (`POST .../unassign`), only need `MAINTENANCE_WRITE` |
