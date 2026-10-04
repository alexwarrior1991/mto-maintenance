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

GET             /task-types | /task-types/{code}     functionalGroup, requiresFullPossession, diagnostic
GET/POST        /teams        GET/PUT /teams/{id}

GET/POST        /shifts                       date, dateFrom, dateTo, teamId, trackId, executionPackageId, status, possessionType
GET/PUT         /shifts/{id}                  POST /shifts/{id}/start | /close | /cancel
GET             /shifts/{id}/tasks?status= | /profiles?status= | /report?format= | /revisions
                                              /profiles: PROFILE assets of the shift's tasks by kp (default status=COMPLETED = reviewed)
POST            /shifts/{id}/tasks/{taskId}   assigns a task to the shift (track and window rules)

GET/POST        /orders                       status, type, priority, assetId, assetType, trackId, stationId, executionPackageId,
                                              plannedFrom, plannedTo, actualStartFrom, actualStartTo, assignedUser, teamId, code
GET/PUT         /orders/{id}
POST            /orders/{id}/plan | /assign | /start | /complete | /cancel
GET             /orders/{id}/history | /revisions
GET/POST        /orders/{id}/tasks            POST /orders/{id}/tasks/generate
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
```

## Export (`?format=`)

The three reports — `/shifts/{id}/report`, `/reports/progress`, `/reports/monthly` — take `format`
with `json` (the default), `xlsx` or `pdf`. Same route, same security rule, same JSON contract when
the parameter is absent; the download is an ordinary URL, so it can be linked or bookmarked.

| `format` | `Content-Type` | Notes |
|---|---|---|
| `json`, absent | `application/json` | Unchanged |
| `xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | One sheet, header pairs above the table, frozen and filtered header, real dates and numbers |
| `pdf` | `application/pdf` | A4 landscape (shift, progress) or portrait (monthly), header repeated on every page |

The value is case insensitive; an unknown one is `400 VAL-001`. The file arrives as
`Content-Disposition: attachment` with a stable, sortable ASCII name — `shift-report-<date>-<code>`,
`progress-report-<date>[-ep<n>][-track<n>]`, `monthly-report-<yyyy-MM>[-ep<n>]` — so a month of
reports saved in one folder sorts itself.

Two deliberate differences from the JSON:

- The PDF prints a subset of the shift report's columns. Nineteen columns on an A4 would leave about
  six characters each and squeeze `worksPerformed` and `defectsFound`, which are the ones being read.
  The workbook carries all of them, `switches` included — the turnouts of a section insulator with
  their rate (`W31 1:9`, and `W31 1:9 (out of service)` for one that is disabled in
  `mto-configuration`), empty on every other row.
- `photoRefs` are printed as text in both formats. They are references that resolve to nothing until
  the photo storage in `05-development-roadmap.md` exists, but they are the only record that the
  photo was taken, so dropping them would lose what the paper report used to carry.

Transitions are `POST` with a body: `PlanOrderRequest{plannedDate, comment}`,
`AssignOrderRequest{teamId, assignedUser, comment}`, `OrderCommentRequest{comment}`,
`CompleteOrderRequest{closingNotes, force, comment}`, `CancelOrderRequest{reason}`;
`CompleteTaskRequest{shiftId, taskTypeCodes, notes, defectsFound, workComplete, repairPlannedDate,
inlineDefects[], materials[], photoRefs[]}`; `MaintenanceShiftRequest{shiftDate, teamId, possessionType, trackIds[], blockingDisconnectorIds[], …}`,
`StartShiftRequest{actualStart, voltageCutoffAt}`, `CloseShiftRequest{actualEnd,
voltageCutoffAt, netWorkMinutes, observations}`; `ResolveDefectRequest{resolutionNotes, correctionType,
partsReplaced, resolvedInShiftId}`.

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
| Shift | `teamId`, `baseName`, `vehicle`, `plannedStart`, `plannedEnd`, `blockingDisconnectorIds`, `earthingPoints`, `parkingPlace`, `executionPackageId`, `startKp`, `endKp`, `personnel`, `measurementEquipment`, `observations` |
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
  `blockingDisconnectorIds`, a team's `executionPackageIds`) is 400 `REQ-VALIDATION` on that list. It
  was a 500 for task types, defects and materials, a 409 `APP-409` for tracks and packages, and a
  photo stored as the text `null`.

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
| 204 | `DELETE /assets/{id}` (disables the asset), `DELETE /orders/{id}/materials/{usageId}` (removes the line) |
| 400 `REQ-VALIDATION` / `VAL-001` | Bean Validation / domain invariant (kp range, quantity, unknown task type, unknown export `format`, a blank required text, a `PATCH` that empties a required field or has an unknown key, a `null` inside a list, a defect detected in the future, a shift closed with its voltage cut-off after the end) |
| 400 `REQ-400` | Malformed body, a parameter of the wrong type, a missing required parameter (`month` of the monthly report) or a `sort` on a property the entity does not have |
| 401 `AUTH-401` / 403 `AUTH-403` | No token / missing role |
| 404 `<AGG>-404` | Unknown id (`ORD`, `AST`, `TSK`, `SHF`, `TEA`, `INS`, `TPL`, `DEF`, `MAT`); a task type, a check item and an inspection item answer `APP-404` |
| 404 `HTTP-404` | Unknown route |
| 405 `REQ-405` | The route exists but not for that method; the `Allow` header lists the ones it takes |
| 409 `TRN-001` | Invalid transition |
| 409 `AST-001` | Disabled asset; a field of a synchronized asset that only `mto-configuration` changes; `enabled=true` on an asset disabled at the source |
| 409 `SHF-001` | Shift rule (no shift in progress on the track, partial possession, diverted track) |
| 409 `MAT-001` | Over-consumption (also taking `allowOverConsumption` off a line consumed above plan), duplicated line, `FAILED` or `REJECTED` line without `force`, changing or removing a consumed line, removing a line of a completed or cancelled order; while a line has a request to stock without an answer (`stockRequestInDoubt`), changing its planned or consumed quantity, changing the order's `stockProjectId`, or removing it with an output in doubt |
| 409 `STK-001` | `mto-stock` has not enough stock, on an explicit `sync` (the line stays `REJECTED` with the reason) |
| 409 `<AGG>-409` | Duplicated code |
| 409 `CON-001` | The `version` of the request is not the one stored, or two writes crossed: nothing was written |
| 409 `APP-409` | A database constraint caught what the validation did not |
| 415 `REQ-415` | Unsupported media type (a `PATCH` is only `application/merge-patch+json`) |
| 422 `INS-001` | Inconsistent inspection result / checklist |
| 422 `STK-422` | `mto-stock` rejected the step for another reason (a material or warehouse retired, a reservation no longer active...), on an explicit `sync` or when removing a reserved line; its code and message are in `message` |
| 503 `STK-503` | `mto-stock` unreachable on an explicit `sync`, or when removing a reserved line (the line stays) |

## Security

| Verb / action | Role |
|---|---|
| `GET` | `MAINTENANCE_READ` |
| `POST`, `PUT` | `MAINTENANCE_WRITE` |
| `DELETE`, and `PUT /assets/{id}` with `enabled=false` (the same decision) | `MAINTENANCE_DELETE` (method security on the `PUT`, on top of `MAINTENANCE_WRITE`) |
| `cancel` **of an order**, `complete` of an order with `force=true`, `resolve`, `close`, `discard` of a defect | `MAINTENANCE_SUPERVISE` on top of `MAINTENANCE_WRITE` (method security); cancelling a shift or a task only needs `MAINTENANCE_WRITE` |
