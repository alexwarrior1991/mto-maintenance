# MTO Maintenance

Spring Boot 4 / Java 25 REST API for the maintenance of railway catenary (OCS): preventive work
profile by profile, corrective work on defects, inspections with checklists, night shifts with
their daily report and the materials each job takes from the warehouse (`mto-stock`).

It is the third service of the MTO domain, next to
[`mto-configuration`](https://github.com/alexwarrior1991/mto-configuration) (infrastructure master
data) and [`mto-stock`](https://github.com/alexwarrior1991/mto-stock) (warehouse), published through
[`mto-gateway`](https://github.com/alexwarrior1991/mto-gateway) and deployed with
[`mto-platform`](https://github.com/alexwarrior1991/mto-platform).

Functional and technical documentation lives in [`docs/`](docs/README.md).

## Requirements

- JDK 25
- PostgreSQL 17 (any 16+ works; `mto-platform` runs 17)
- Docker, for the Testcontainers-backed tests and for the local environment
- RabbitMQ, Keycloak and `mto-stock`, all provided by `mto-platform`

## Configuration

Everything is read from the environment; `.env.example` lists every variable with its default.
The ones without a default:

| Variable | What |
|---|---|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Application database |
| `KEYCLOAK_ISSUER_URI` | Realm that issues the tokens (`http://auth.mto.local:8082/realms/mto`) |
| `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_AUDIENCE` | `mto-maintenance-api` |
| `KEYCLOAK_SERVICE_CLIENT_SECRET` | Secret of the service account `mto-maintenance-svc` used to call `mto-stock` |
| `MTO_STOCK_URL` | Base URL of `mto-stock` (`http://localhost:8080` from the IDE) |
| `APP_CORS_ALLOWED_ORIGIN` | Browser origin allowed by CORS |

Switches worth knowing: `APP_RABBITMQ_ENABLED=false` starts without a broker,
`APP_STOCK_ENABLED=false` starts without `mto-stock` (material lines stay `NOT_REQUESTED`),
`APP_SECURITY_EXPOSE_API_DOCS=true` publishes Swagger without a token.

## Spring profiles

`dev` (port 8083, SQL logging, Swagger open), `test` (used by the suite: broker and stock off,
audience validation off) and `prod` (graceful shutdown, health details hidden).

## Run locally

```bash
cd ../mto-platform && docker compose --profile stock up -d && ./keycloak/apply-partials.sh
cd ../mto-maintenance
export SPRING_PROFILES_ACTIVE=dev
export DATABASE_URL=jdbc:postgresql://localhost:5432/mto_maintenance
export DATABASE_USERNAME=mto_maintenance_user DATABASE_PASSWORD=mto_maintenance_password
export KEYCLOAK_ISSUER_URI=http://auth.mto.local:8082/realms/mto
export KEYCLOAK_TOKEN_URI=http://auth.mto.local:8082/realms/mto/protocol/openid-connect/token
export KEYCLOAK_SERVICE_CLIENT_SECRET=<Clients -> mto-maintenance-svc -> Credentials>
export SPRING_RABBITMQ_USERNAME=mto SPRING_RABBITMQ_PASSWORD=mto
./mvnw spring-boot:run
```

`auth.mto.local` must resolve to the host (`127.0.0.1 auth.mto.local` in `/etc/hosts`): the token
`iss` carries that name and the application fetches the JWK Set from it.

The whole stack, this service included, also runs from `mto-platform` with
`docker compose --profile all up -d` (published image). `compose.yaml` here builds and runs a local
image against that infrastructure:

```bash
cp .env.example .env    # fill DATABASE_*, KEYCLOAK_SERVICE_CLIENT_SECRET
docker compose up -d --build
```

## Database migrations

Flyway, `src/main/resources/db/migration`. Hibernate validates the schema on boot, so every change
is a new `V<n>__*.sql`. `V1` creates the schema, `V2` the inbox, `V3` seeds the catalogues (task
types `RG-01…RP-12`, teams A/B, inspection templates), `V4` the Envers tables. Details in
[`docs/03-database.md`](docs/03-database.md).

## Master data from `mto-configuration`

Profiles, disconnectors and section insulators appear as assets here without anyone creating them:
the service consumes the master-data events of `mto-configuration` through an idempotent inbox
(`mto.maintenance.master-data.queue`). A deleted track disables every asset on it. Only track
sections (`TRACK_SECTION`) are created through the API. See
[`docs/06-messaging.md`](docs/06-messaging.md).

## Materials and `mto-stock`

A material line references a material and a warehouse of `mto-stock` by id. Planning an order
reserves every line, completing it consumes the reservations (or registers a direct output when
the order has no stock project), cancelling releases them. The call goes out with the service
account `mto-maintenance-svc`, which needs `stock-read` and `stock-write` on `mto-stock-api`
(`mto-platform/keycloak/apply-partials.sh` grants them locally). When `mto-stock` is down the line
is left `FAILED` and `POST /orders/{id}/materials/{usageId}/sync` retries.

## Security

Keycloak resource server, audience `mto-maintenance-api`. Permissions are client roles, profiles are
realm composites (`mto-maintenance-viewer`, `-technician`, `-manager`); see
[`keycloak/README.md`](keycloak/README.md).

| Verb / action | Permission |
|---|---|
| `GET` | `maintenance-read` |
| `POST`, `PUT` | `maintenance-write` |
| `DELETE /assets/{id}` | `maintenance-delete` |
| `cancel`, `complete` with `force`, `resolve`, `close`, `discard` | `maintenance-supervise` |
| `/actuator/**` (except health/info) | `ops-metrics` / `ops-write` |

```bash
TOKEN=$(curl -s -X POST http://auth.mto.local:8082/realms/mto/protocol/openid-connect/token \
  -d grant_type=password -d client_id=mto-frontend \
  -d username=mantenimiento.tecnico -d password=local | jq -r .access_token)
curl -s http://localhost:8083/api/v1/maintenance/orders -H "Authorization: Bearer $TOKEN"
```

## API documentation

Swagger UI at `/swagger-ui.html`, OpenAPI at `/v3/api-docs` (open in `dev`, behind
`APP_SECURITY_EXPOSE_API_DOCS` elsewhere). The resources and the transition rules are summarised in
[`docs/04-rest-api.md`](docs/04-rest-api.md); `http/maintenance-api.http` walks through a complete
preventive shift.

## Actuator and tracing

`/actuator/health` and `/actuator/info` are public; `metrics` and `prometheus` need `ops-metrics`.
Traces go to the OTLP collector of `mto-platform` (`OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`), including
the trace that arrives in the RabbitMQ headers.

## Running tests

```bash
./mvnw test                     # Testcontainers: postgres:17-alpine
./mvnw verify                   # + KeycloakAuthorizationIT (skipped without Docker)
```

Without Docker, point the suite at any PostgreSQL:

```bash
TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/mto_maintenance_test \
TEST_DATABASE_USERNAME=mto_maintenance TEST_DATABASE_PASSWORD=mto_maintenance ./mvnw test
```
