# 05 · Development roadmap

## Done

- Schema, seeds and Envers tables (`V1`–`V4`).
- Assets synchronized from `mto-configuration` (profile, disconnector, section insulator, track
  deletion) through the idempotent inbox with sequence watermark.
- Orders with the full state machine, status history, workload estimate.
- Preventive tasks profile by profile (`tasks/generate`), task types catalogue, check items for
  diagnostic tasks, inline defects at task completion.
- Shifts with possession rules, daily report, net work minutes.
- Inspections with templates, defect and corrective order creation (idempotent).
- Defects with their own lifecycle and history.
- Materials reserved/consumed/released in `mto-stock` with circuit breaker and manual `sync`.
- Progress and monthly reports.
- Export of the three reports to Excel and PDF on the same endpoints (`?format=json|xlsx|pdf`), the
  format the client receives.
- Keycloak partials, Docker image, CI, gateway route, platform compose.
- Own events for `mto-notification` through an outbox (`V13`): orders, defects, inspections,
  shifts, material lines, assets disabled here and the daily `preventive.due-soon`, with the
  actor and the correlation of the request (`06-messaging.md`).
- Teams, crews and vehicles of `mto-resources` (`V19`, `V20`): its people, teams and vehicles
  consumed through the inbox (`mto.maintenance.resources.queue`), local teams adopted by code and
  read-only once synchronized (`TEA-001`), the crew and the vehicles of each shift with the
  `CrewComposition` and `ShiftCrew` rules, where each vehicle was left at close, the crew in the
  shift events and report, and `shift.cancelled`.
- Virtual threads and background operations (`V21`): Tomcat, `@Scheduled` and the listeners on
  virtual threads with the Hikari pool as the limit, `BudgetedOperations` with a deployment-wide cap
  (`OPS-429`), `Prefer: respond-async` on `tasks/generate` (202, `GET /operations/{id}`), and the
  generation of a package waiting for its lock without holding a connection.
- Order transitions talk to `mto-stock` for all their lines at once, on virtual threads, under one
  deadline; a line cut by it is `FAILED` and retried right after commit.
- Qualifications and expiries (`V23`): the qualifications of the people, the documents of the
  vehicles and the instruments of `mto-resources` in the snapshots; nothing expired at the shift's
  date gets into a shift (`ShiftQualifications`, 409 `SHF-001`), each self-propelled vehicle starts
  with a different qualified driver, and the instruments of a shift (`instrumentIds`, `MSI-404`) in
  its events and in the `Instruments` appendix of its report.

## Next

- Photo storage (`photoRefs` are references today; an object store and upload endpoint are missing).
- Inspection template management through the API (today: Flyway seeds, new version per change).
- Automatic preventive planning: create the preventive order of a section when
  `nextPreventiveDueAt` approaches (today the daily `preventive.due-soon` event only tells it).
- Revisions of `mto-configuration` snapshots: the `sectioning` and kp of a profile are refreshed on
  each event, but an order already created keeps the location it copied.
