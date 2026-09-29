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

## Next

- Photo storage (`photoRefs` are references today; an object store and upload endpoint are missing).
- Inspection template management through the API (today: Flyway seeds, new version per change).
- Automatic preventive planning: create the preventive order of a section when
  `nextPreventiveDueAt` approaches (today the daily `preventive.due-soon` event only tells it).
- Revisions of `mto-configuration` snapshots: the `sectioning` and kp of a profile are refreshed on
  each event, but an order already created keeps the location it copied.
