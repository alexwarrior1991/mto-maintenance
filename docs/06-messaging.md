# 06 · Messaging

`mto-maintenance` consumes the master-data change events that `mto-configuration` publishes. It
publishes nothing. The contract (envelope, headers, routing keys, payloads) is owned by
`mto-configuration` — see its `README_MESSAGING.md` — and mirrored here in
`application/dto/messaging`.

## Topology

| | Value |
|---|---|
| Exchange (publisher's) | `mto.master-data.exchange` (topic) |
| Routing key pattern | `mto.master-data.#` (`mto.master-data.<entity>.<created|updated|deleted>`) |
| Queue (own) | `mto.maintenance.master-data.queue` |
| Dead letter | `mto.maintenance.master-data.queue.dlx` → `mto.maintenance.master-data.queue.dlq` |

Gated by `app.rabbitmq.enabled` (topology) and `app.rabbitmq.master-data.listener-enabled`
(consumer). With the first off the application starts without a broker (what the tests do).

## Envelope

`MasterDataChangedMessage{operationId, referenceId, origin, creationDate, eventType,
data{entityName, entityId, operation, values}, messageHash}` plus the headers `sequenceNumber`
(global, increasing), `messageSignature` and `messageSignatureAlgorithm`. The signature is
verified over the received bytes (`app.messaging.signature.{secret, mode}`, same secret as the
publisher; `OPTIONAL` by default).

## Inbox

`MasterDataEventConsumer` → `IdempotentMasterDataEventProcessor` → `InboxMessageService` → handler.
The idempotency key is `operationId` (fallback: AMQP `message_id`), guaranteed by the unique
constraint `(message_id, source_service)` of `inbox_message` with conditional native updates —
never read-then-write. Recording, claiming, the handler and the `PROCESSED` mark share one
transaction; `recordFailure` runs in its own after it. A message without `data`, `entityName` or
`operation` goes to the DLQ.

## Handlers

| Entity | Handler | Effect |
|---|---|---|
| `profile` | `ProfileMasterDataHandler` | Upsert `PROFILE` (`PRF-<id>`, name = `profileId`, kp, `track.id`, `track.executionPackageId`, `sectionings[].code` joined) / deactivate on `DELETED` |
| `disconnector` | `DisconnectorMasterDataHandler` | Upsert `DISCONNECTOR` (`DSC-<id>`, `station.id`, `profile.id`, `profile.kp`) / deactivate |
| `section-insulator` | `SectionInsulatorMasterDataHandler` | Upsert `SECTION_INSULATOR` (`SIN-<id>`, `station.id`, `enabled`) / deactivate |
| `track` | `TrackMasterDataHandler` | `DELETED` only: `deactivateByTrack(trackId)` |
| `execution-package`, `station`, `cantilever`, `steady-arm` | none | logged and ignored |

Upserts and deactivations are native SQL in `CatenaryAssetRepository` with the `sequenceNumber`
watermark compared inside the `WHERE`: an older event returns 0 rows and is discarded; a missing
sequence applies and keeps the stored watermark; a deletion advances it even on an already
disabled row. Payloads are read tolerantly (`MasterDataPayload`): numbers as strings or numbers,
nested objects, missing keys → `null`. A handler runs inside the inbox transaction and must not open
its own.

## Known gaps

- An asset changed by an event leaves no Envers revision (native SQL). `catenary_asset_aud`
  covers the REST path only.
- Orders already created keep the location they copied from the asset; a later kp change of the
  profile is not propagated.
