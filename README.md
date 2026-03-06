### SessionKit (cassete)

This repository contains:

1. **`sessionkit/`** — a small, reusable library (“the whole enchilada”) that records and replays sessions.
2. **`app/`** — a demo Android app that wires SessionKit into a UI, adds a player overlay, and supports export/import of tapes.

The goal is to make **hard-to-reproduce, stateful bugs** reproducible by capturing what the app *observed* (HTTP + UI events) into a **tape**, then replaying it deterministically later.

---

### The VCR pattern (idea)

Think “old-school video recorder,” but for side effects:

- **RECORD**: run against the real backend; every HTTP request/response (and optionally UI events) is appended to a tape file.
- **REPLAY**: run without the backend; responses are served from the tape instead of the network.
- **PASSTHROUGH**: real network, no recording/replay.

Why it helps:

- **Deterministic debugging**: same inputs → same outputs.
- **Stable reproduction of transient states**: you don’t need the backend to be in “the same state again”; you replay the exact payloads the app saw when the bug happened.
- **Offline / fast**: no network latency, works in airplane mode.

---

### Project structure

#### `sessionkit/` (library)
SessionKit is the minimal app-facing API. The app should not have to know about tape parsing, pairing requests/responses, cursor handling, etc.

Key concepts:

- `SessionKit` — one entry point that gives you:
  - a `NetworkClient` backed by OkHttp that records/replays
  - `recordUiEvent(...)` and `recordAction(...)` helpers
  - `switchMode(...)`, `loadTape(...)`, and `resetReplayCursors()`
- `Mode` — `RECORD`, `REPLAY`, `PASSTHROUGH`
- `SessionRecorder` — writes NDJSON events with a monotonic `seq`
- `TapeLoader` + `ReplayTape` — load/build the in-memory replay tape with validation
- `RecordingInterceptor` / `ReplayerInterceptor` — OkHttp interceptors that implement record/replay
- `UrlPattern` — Retrofit-style URL matching (`/pokemon/{id}`)

#### `app/` (demo)
The demo app:

- exposes mode switching in UI
- can share/download `events.ndjson`
- can import an external tape and immediately replay it
- includes a **SessionPlayer** + overlay controls (play/pause/step/back/seek)
- gates live user inputs while the player is active (“Player active: live inputs are gated”)

---

### Tape format (NDJSON)

The tape is **newline-delimited JSON** (NDJSON): one JSON object per line. It’s append-only and easy to inspect.

Important fields:

- `schema: Int` — currently `1`
- `seq: Long` — **strictly increasing ordering key** (this is the determinism anchor)
- `ts: Long?` — optional wall clock timestamp (not used for ordering)
- `type: String` — event discriminator

Event types used by SessionKit:

- `SESSION_START`
- `UI_EVENT` — “inputs” to your ViewModel / reducer
- `ACTION` — generic actions; can represent nondeterminism outputs (time/uuid/random)
- `REQUEST` / `RESPONSE` — HTTP traffic

Example (shortened):

```json
{"schema":1,"seq":0,"type":"SESSION_START","ts":1700000000000,"metadata":{},"appVersion":"1.0","device":"emulator"}
{"schema":1,"seq":1,"type":"UI_EVENT","ts":1700000000100,"metadata":{},"screen":"ApiTest","event":"FetchRandomPokemon","payload":{}}
{"schema":1,"seq":2,"type":"REQUEST","ts":1700000000300,"metadata":{},"requestId":"r1","method":"GET","url":"https://pokeapi.co/api/v2/pokemon/25","bodySha256":null}
{"schema":1,"seq":3,"type":"RESPONSE","ts":1700000000400,"metadata":{},"requestId":"r1","code":200,"headers":{"Content-Type":"application/json"},"body":"{...}","durationMs":123}
```

---

### Quickstart (run the demo)

Prereqs:

- Android Studio (recent)
- Android minSdk 24
- JDK 11+

Build:

```bash
./gradlew :app:assembleDebug
```

---

### Using SessionKit in your own app

SessionKit is designed to be a seam: your UI/business logic should depend on **ports** (clock/random/id/network) and be able to swap “live” vs “replay” behavior.

#### 1) Create SessionKit

Create one instance (e.g., in `Application`):

```kotlin
import com.hellmannratti.vcr.sessionkit.Mode
import com.hellmannratti.vcr.sessionkit.SessionKit
import com.hellmannratti.vcr.sessionkit.SessionKitConfig

val sessionKit = SessionKit(
    config = SessionKitConfig(
        mode = Mode.RECORD,
        tapeFile = null,
        appVersion = BuildConfig.VERSION_NAME,
        device = android.os.Build.MODEL
    ),
    baseDir = filesDir
)
```

#### 2) Get a network client

```kotlin
val network = sessionKit.networkClient()
// use `network` from your ViewModel / repository (not OkHttp directly)
```

#### 3) Switch modes

```kotlin
sessionKit.switchMode(Mode.REPLAY) // or Mode.RECORD / Mode.PASSTHROUGH
```

#### 4) Load an imported tape (REPLAY)

```kotlin
// Pick a file (DocumentProvider / file picker) and pass it in
val uniqueRequests = sessionKit.loadTape(importedNdjsonFile)
```

If the tape fails to load, SessionKit writes diagnostics to:

- `files/sessions/last_tape_load_error.txt`

This file is safe to share with support (but still review it if you recorded sensitive data).

#### 5) Record UI events (optional but recommended)

```kotlin
sessionKit.recordUiEvent(
    screen = "Checkout",
    event = "SubmitOrder",
    payload = emptyMap()
)
```

UI events are what enable “time travel” style replay (step/seek/back) when combined with a player.

#### 6) Rewind support: reset replay cursors

HTTP replay is consumed via per-request cursors. If you implement rewind/seek-back, reset cursors when you reset UI state:

```kotlin
sessionKit.resetReplayCursors()
```

---

### QA → Dev workflow (why this exists)

1. QA reproduces a bug on a real device in **RECORD** mode.
2. QA exports/shares the tape (`events.ndjson`).
3. Dev imports the tape and switches to **REPLAY**.
4. Dev can now reproduce the bug deterministically (even if the backend/account state has changed).

This is particularly valuable for **transient states** (e.g., a vehicle “on the road” during the bug report, but “parked” later). You don’t try to recreate the backend state; you replay what the app saw.

---

### Matching and determinism notes

- Requests are matched using:
  - method + URL (with optional `UrlPattern` placeholders)
  - optional request body hash (`bodySha256`) for distinguishing same-URL POSTs
- Ordering is driven by `seq` to avoid relying on wall-clock time.

---

### Drawbacks / things to watch

- **Privacy & security**: tapes can contain PII, auth tokens, or proprietary payloads. Add redaction/filters before sharing beyond your team.
- **Stale recordings**: a tape is a snapshot; if backend behavior changes, tapes can become misleading.
- **Coverage is only what you recorded**: you still need new recordings for new scenarios.
- **Matching can be hard**: dynamic headers/nonces/timestamps may require normalization rules.

---

### License

No license is currently specified. Contact the project owner for licensing information.
