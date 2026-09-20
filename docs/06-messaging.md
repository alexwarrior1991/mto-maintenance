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
| `section-insulator` | `SectionInsulatorMasterDataHandler` | Upsert `SECTION_INSULATOR` (`SIN-<id>`, `station.id`, `enabled`, `installationType`, `track.id`, `connectedTrack.id`, kp range) **plus its `switches[]` into `catenary_asset_switch`** / deactivate |
| `track` | `TrackMasterDataHandler` | `DELETED` only: `deactivateByTrack(trackId)` |
| `execution-package`, `station`, `cantilever`, `steady-arm` | none | logged and ignored |

Upserts and deactivations are native SQL in `CatenaryAssetRepository` with the `sequenceNumber`
watermark compared inside the `WHERE`: an older event returns 0 rows and is discarded; a missing
sequence applies and keeps the stored watermark; a deletion advances it even on an already
disabled row. Payloads are read tolerantly (`MasterDataPayload`): numbers as strings or numbers,
nested objects, lists of nested objects (`nestedList`), missing keys → `null`; an
`installationType` this side does not know is stored as nothing rather than sent to the DLQ. A
handler runs inside the inbox transaction and must not open its own.

### The switches of a section insulator

A section insulator normally sits where **two tracks connect**, that is on a turnout; sometimes it
sits in the middle of a single track. Each connection is identified by a turnout labelled `W` and a
number, at a kilometric point, with its turnout rate beside it: `W31 1:9`, `W57 1:8`. They land in
`catenary_asset_switch`, one row per turnout — **not** a new `CatenaryAssetType`: a turnout is part
of the insulator, not something an order is opened on.

The rate travels as `turnoutDenominator` (the `9` of `1:9`) and not as text: the numerator is always
1, and the integer can be ordered and compared. `turnoutRate` also arrives in the payload and is
recomputed here rather than stored.

`enabled` arrives per turnout and **is stored**. A turnout taken out of service still travels in the
event (the source collection filters soft-deleted rows, not disabled ones), and dropping it here
would tell a crew the turnout does not exist instead of that it cannot be used; the shift report
prints it as `W31 1:9 (out of service)`. With no `enabled` key the turnout counts as enabled, like
the asset's own flag.

`start_kp`/`end_kp` are the **minimum and maximum** of the insulator's own kp and its turnouts',
sorted, so `chk_catenary_asset_kp_range` can never fire and the insulator shows up in `?kpFrom/kpTo`
and in the preventive task generation of a track section.

The block is replaced whole after an applied upsert: the emitter always sends the complete list, so
what arrives is the final state. Two things follow from the order of operations, and both are
tested in `MasterDataAssetSyncDataJpaTest`:

- **A late event does not touch the switches.** The upsert returns 0 rows and the handler returns
  before reaching them, so a stale delivery cannot undo what a newer one applied.
- **Only the section-insulator handler may clear them** (`ownsSwitches()`): a handler that knows
  nothing about turnouts cannot delete the ones that are there, while an empty list from the one
  that does mean the insulator has none left.

## Known gaps

- An asset changed by an event leaves no Envers revision (native SQL). `catenary_asset_aud`
  covers the REST path only.
- Orders already created keep the location they copied from the asset; a later kp change of the
  profile is not propagated.
