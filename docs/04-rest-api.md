# 04 · REST API

Base path `/api/v1/maintenance` (through the gateway: `/api/maintenance`). JSON, UUID ids, pageable
collections (`page`, `size`, `sort`) returning `PageResponse{content, page{number, size,
totalElements, totalPages, first, last}}`. Errors are `ApiErrorResponse{timestamp, status, error,
message, errorCode, path, validationErrors[]}`.

## Resources

```
GET/POST        /assets                       type, trackId, stationId, executionPackageId, enabled, kpFrom, kpTo, code, name, preventiveDueBefore
                                              name: natural name (profile 12-2.27, disconnector HSA-NS5), partial and case insensitive;
                                              unique only together with trackId
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
GET/POST        /orders/{id}/materials        PUT .../{usageId}   POST .../{usageId}/sync

GET/POST        /inspections                  result, assetId, assetType, trackId, stationId, executionPackageId, inspectionFrom, inspectionTo, inspector, originOrderId
GET/PUT         /inspections/{id}             PUT /inspections/{id}/items/{itemId}
POST            /inspections/{id}/create-defect | /create-corrective-order
GET             /inspections/{id}/revisions
GET             /inspection-templates | /inspection-templates/{id}

GET/POST        /defects                      severity, status, assetId, trackId, stationId, executionPackageId, detectedFrom, detectedTo, orderId
GET/PUT         /defects/{id}
POST            /defects/{id}/resolve | /close | /discard | /link-order/{orderId}
GET             /defects/{id}/history | /revisions

GET             /reports/progress             executionPackageId, trackId, assetType, from, to, format
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

- The PDF prints a subset of the shift report's columns. Eighteen columns on an A4 would leave about
  six characters each and squeeze `worksPerformed` and `defectsFound`, which are the ones being read.
  The workbook carries all of them.
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

## Status codes

| Code | When |
|---|---|
| 201 + `Location` | Creation |
| 200 | Reads, updates, transitions, idempotent `create-defect`/`create-corrective-order` |
| 400 `REQ-VALIDATION` / `VAL-001` | Bean Validation / domain invariant (kp range, quantity, unknown task type, unknown export `format`) |
| 401 `AUTH-401` / 403 `AUTH-403` | No token / missing role |
| 404 `<AGG>-404` | Unknown id (`ORD`, `AST`, `TSK`, `SHF`, `TEA`, `TTY`, `INS`, `TPL`, `DEF`, `MAT`) |
| 409 `TRN-001` | Invalid transition |
| 409 `AST-001` | Disabled asset |
| 409 `SHF-001` | Shift rule (no shift in progress on the track, partial possession, diverted track) |
| 409 `MAT-001` | Over-consumption, duplicated line, `FAILED` line without `force` |
| 409 `<AGG>-409` | Duplicated code |
| 422 `INS-001` | Inconsistent inspection result / checklist |
| 503 `STK-503` | `mto-stock` unreachable on an explicit `sync` |

## Security

| Verb / action | Role |
|---|---|
| `GET` | `MAINTENANCE_READ` |
| `POST`, `PUT` | `MAINTENANCE_WRITE` |
| `DELETE` | `MAINTENANCE_DELETE` |
| `cancel`, `complete` with `force=true`, `resolve`, `close`, `discard` | `MAINTENANCE_SUPERVISE` (method security) |
