# Task 04: Recording Schema

## Summary
Extend NDJSON schema to capture UiEvents and nondeterministic outputs with ordering metadata.

## Tasks

### 1) Event shapes (NDJSON line schema)

Every line in `events.ndjson` is a JSON object with the following **envelope** fields:

- `schema: Int` — schema version (**currently `1`**)
- `seq: Long` — **monotonic, strictly increasing** ordering key for the whole file
- `ts: Long?` — optional wall-clock timestamp (ms since epoch); **not used for ordering**
- `type: String` — event discriminator (kotlinx `classDiscriminator = "type"`)
- `metadata: Map<String, String>` — optional free-form metadata

Event-specific fields are top-level alongside the envelope (no nested `payload` object in code), but conceptually they form the `payload`.

### 2) Event types

The implementation uses a sealed `Event` hierarchy:

- `SESSION_START` — session marker
- `UI_EVENT` — recorded UI inputs (from `ApiTestViewModel.onEvent`)
- `ACTION` — generic action event (used for nondeterminism outputs like `ND_RANDOM_INT`)
- `REQUEST` / `RESPONSE` — HTTP recording events

#### UI event

`type = "UI_EVENT"`

- `screen: String`
- `event: String`
- `payload: Map<String, JsonElement>`

#### Nondeterminism outputs

Uses `type = "ACTION"` with stable `name` and `details` shapes.

- `name = "ND_RANDOM_INT"`
  - `details = {"from": Int, "until": Int, "value": Int}`

(Other nondeterminism shapes from Task 03 remain valid, even if not currently emitted in all paths.)

### 3) Ordering and timestamps

- `seq` is assigned **at write time** by `SessionRecorder`.
- `ts` is optional; current implementation records `ts = clock.nowMs()`.

### 4) Versioning + validation rules

- Only `schema == 1` is supported.
- Loader validation rejects:
  - malformed JSON lines that cannot decode to `Event`
  - `schema != 1`
  - `seq < 0` or non-monotonic `seq`
  - `RESPONSE` referencing a `requestId` that did not appear in a prior `REQUEST`

### 5) Example tape

```json
{"schema":1,"seq":0,"type":"SESSION_START","ts":1700000000000,"metadata":{},"appVersion":"1.0","device":"emulator"}
{"schema":1,"seq":1,"type":"UI_EVENT","ts":1700000000100,"metadata":{},"screen":"ApiTest","event":"FetchRandomPokemon","payload":{}}
{"schema":1,"seq":2,"type":"ACTION","ts":1700000000200,"metadata":{},"name":"ND_RANDOM_INT","details":{"from":1,"until":1026,"value":42}}
{"schema":1,"seq":3,"type":"REQUEST","ts":1700000000300,"metadata":{},"requestId":"r1","method":"GET","url":"https://...","bodySha256":null}
{"schema":1,"seq":4,"type":"RESPONSE","ts":1700000000400,"metadata":{},"requestId":"r1","code":200,"headers":{"Content-Type":"application/json"},"body":"{...}","durationMs":123}
```

## Acceptance Criteria
- Schema doc exists and matches implementation.
- Recorder writes UiEvent and nondeterministic-output events with sequence numbers.
- Basic validation in place (e.g., rejects malformed events, ensures seq increments).
