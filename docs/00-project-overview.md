# 00 · Project overview

## Purpose

`mto-maintenance` is the operational record of the maintenance of the overhead contact system
(catenary) built by the MTO execution packages. It replaces the daily shift spreadsheets and the
per-segment progress workbooks with a single service that:

- keeps the **assets** maintenance is done on (track sections, profiles, disconnectors, section
  insulators), synchronized from the infrastructure master data;
- plans and records **maintenance orders** of four kinds: preventive (profile by profile, following
  the OCS maintenance plan), corrective (on a defect), inspection and urgent;
- organizes the work in **night shifts** per team and track, with partial or full possession, and
  produces the daily report of each shift;
- records **inspections** against checklists per asset type, the **defects** they find and the
  corrective work that closes them;
- reserves and consumes the **materials** each job uses in the warehouse service `mto-stock`;
- computes **progress** (profiles checked per segment and track, km covered, disconnectors and
  section insulators checked) and the **monthly** figures.

## What lives elsewhere, on purpose

| Concern | Owner | How this service uses it |
|---|---|---|
| Stations, tracks, profiles, cantilevers, disconnectors, section insulators | `mto-configuration` | Consumed as events; only the snapshot a job needs is kept in `catenary_asset` |
| Materials, warehouses, projects, reservations, movements | `mto-stock` | Called through its REST API with a service account; lines store the stock ids |
| Users, roles, tokens | Keycloak (`mto-platform`) | Resource server; permissions are client roles of `mto-maintenance-api` |
| Public routing, CORS at the edge | `mto-gateway` | `/api/maintenance/**` → `/api/v1/maintenance/**` |

## Sources of the domain

The OCS Maintenance Plan 2025-2026 (task catalogue `RG-01…RG-18` and `RP-01…RP-12` with functional
groups and standard times, weekday partial possession vs weekend full possession, teams A/Rishpon
and B/Mishmar, the 21:00–05:00 night shift with 4–5 effective hours), the daily shift report (one
row per profile with works performed, defects, materials, complete yes/no, repair shift date,
sectioning, photos) and the progress workbook per execution package (profiles checked per segment
and track, disconnectors and section insulators checked). The service models what those documents
recorded and derives the reports instead of storing them.
