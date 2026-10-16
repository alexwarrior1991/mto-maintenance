# 01 · Architecture

## Stack

Java 25, Spring Boot 4.1, Spring Data JPA + Hibernate Envers, Flyway, PostgreSQL, MapStruct,
Lombok, Spring AMQP, Spring Security (OAuth2 resource server + OAuth2 client for the outgoing
service account), Spring `RestClient` + Spring Cloud CircuitBreaker (Resilience4j), springdoc,
Micrometer/OpenTelemetry, Testcontainers.

## Layers

```
com.alejandro.mtomaintenance
├── domain.model            state machines, kp ranges, quantities, workload estimate (no Spring, no JPA)
├── application
│   ├── dto.<resource>      request/response records with Bean Validation
│   ├── dto.export          format-neutral report document + ReportFormat (not API types, like dto.messaging)
│   ├── service             public interfaces (one per resource + StockClient, StatusHistoryService,
│   │                       MaintenanceCodeGenerator, WorkloadEstimator, ReportExportService, ...)
│   ├── service.impl        package-private implementations and shared helpers
│   ├── mapper              MapStruct, entity -> response only
│   └── exception           business exceptions mapped by GlobalExceptionHandler
├── infrastructure
│   ├── persistence.entity | .repository | .specification | .audit
│   ├── web.controller | web.exception
│   ├── messaging.rabbitmq  consumer of the master-data channel; names of the own exchange
│   ├── messaging.outbox    the outbox that publishes the own events (copy of mto-configuration's)
│   ├── export              XlsxReportExporter (POI) and PdfReportExporter (OpenPDF)
│   └── stock               RestClientStockClient
└── configuration           security, rabbitmq, messaging signature, outbox, events (the daily preventive check), stock client, JPA auditing, OpenAPI
```

Rules that keep the layers honest:

- Controllers talk to service interfaces and return DTOs; entities never cross the API boundary.
- Services build entities with their Lombok builders and validate invariants through the domain
  package; `DomainGuard.domain(...)` converts an `IllegalArgumentException` into a 400.
- Repositories with native SQL (`upsertFromMasterData`, `deactivateFromMasterData`,
  `deactivateByTrack`, the inbox) are the only place where a watermark or an idempotency key is
  checked, and always inside the writing statement.
- Every state change of an order or a defect goes through `StatusHistoryService`, which records
  who changed what and why.
- The three reports are exported by turning them into one `ReportDocument` and handing it to the
  `ReportExporter` of the requested format. The exporters are `@Component`s indexed by format at
  startup, the same registry-by-key the master-data handlers use, so a new format is one class and
  one enum constant: the controllers only choose between the DTO and a file.

## Integrations

| Direction | Peer | Mechanism |
|---|---|---|
| Inbound | `mto-configuration` | RabbitMQ master-data events → inbox → asset upserts |
| Outbound | `mto-stock` | REST, service account `mto-maintenance-svc`, circuit breaker `stock` |
| Inbound | `mto-gateway` / browser | JWT of the `mto` realm with audience `mto-maintenance-api` |

## Cross-cutting

- **Security**: `SecurityConfiguration` maps verbs to `maintenance-*` roles under
  `/api/v1/maintenance/**`; `@PreAuthorize` adds `maintenance-supervise` on the supervision actions.
- **Errors**: `GlobalExceptionHandler` → `ApiErrorResponse` with a stable `errorCode`
  (`BusinessErrorCodeResolver`): `ORD-404`, `TRN-001`, `AST-001`, `MAT-001`, `INS-001`, `SHF-001`,
  `STK-001`, `STK-422`, `STK-503`, `VAL-001`, `REQ-VALIDATION`, `AUTH-401/403`.
- **Pagination**: `PageResponse<T>` with `PageMetadataResponse`.
- **Observability**: Actuator health/info public; metrics and prometheus behind `ops-metrics`;
  traces exported by OTLP, including the trace propagated in the RabbitMQ headers.
- **Threads**: virtual threads (`spring.threads.virtual.enabled`, `SPRING_THREADS_VIRTUAL_ENABLED`,
  `true` by default) for Tomcat's requests (`tomcat-handler-N`), the `@Scheduled` jobs (Boot's
  `SimpleAsyncTaskScheduler`: the fixed-delay ones still run one after another on its thread, the
  daily `preventive.due-soon` cron on its own), the RabbitMQ consumer (`rabbit-simple-N`) and the
  `stock` circuit breaker (`StockCircuitBreakerExecutorConfiguration`, `stock-circuit-N`: Spring Cloud
  CircuitBreaker runs each call on its own executor, a platform pool the flag does not reach). The
  `outbox-dispatch` thread stays a single platform thread on purpose: `OutboxDispatchTrigger`
  coalesces wake-ups on it. A single request is no faster; what changes is how many can wait at once
  without an OS thread each. The limit is the connection pool (`DATABASE_POOL_MAX_SIZE`, 10 by
  default), and the pools of every service share the `max_connections` (100) of the PostgreSQL of
  `mto-platform`. A connection is held while a stock call runs inside an order transition.
