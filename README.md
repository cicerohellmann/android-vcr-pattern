# Cassete

Cassete records and replays HTTP traffic and UI events so Android bugs can be reproduced without depending on live backend state.

The repository now ships adapter-based modules instead of a monolithic client-owning library:

- `:cassete-core` for tape storage, matching, replay state, diagnostics, and the shared controller
- `:cassete-okhttp` for OkHttp and Retrofit integration
- `:cassete-ktor` for Ktor client integration
- `:app` as the demo Android host app
- `:sessionkit` as a deprecated legacy compatibility module kept in the repo during migration

## What it does

- `RECORD`: real network, append request/response and UI events to an NDJSON tape
- `REPLAY`: serve matching responses from tape with deterministic cursor progression
- `PASSTHROUGH`: real network, no recording or replay

The tape format stays schema-compatible with the existing NDJSON v1 shape. Older tapes without the full envelope are still accepted by the loader.

## Modules

### `:cassete-core`

Core contains:

- `Cassete.create(...)` and the shared `CasseteRuntime` controller
- `Mode`, `TapeLoadResult`, replay-miss and missing-tape policies
- `Event`, `TapeLoader`, `ReplayTape`, `SessionRecorder`, `SessionPlayer`
- transport-neutral request and response snapshots
- URL normalization, header redaction, and body redaction hooks
- deterministic replay cursor handling and tape-load diagnostics

### `:cassete-okhttp`

OkHttp integration is installation-based:

```kotlin
val controller = Cassete.create(
    config = CasseteConfig(
        initialMode = Mode.RECORD,
        tapeFile = File(filesDir, "sessions/events.ndjson")
    ),
    baseDir = filesDir
)

val okHttp = CasseteOkHttp
    .install(OkHttpClient.Builder(), controller)
    .build()
```

The same client can then be reused by Retrofit.

### Retrofit

Retrofit rides on the OkHttp adapter:

```kotlin
val retrofit = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(okHttp)
    .addConverterFactory(ScalarsConverterFactory.create())
    .build()
```

Integration coverage in this repo verifies:

- success-body decoding still works in replay mode
- error responses still expose their recorded status, headers, and body
- the same OkHttp client can switch between `RECORD` and `REPLAY`

### `:cassete-ktor`

Ktor installs as a client plugin:

```kotlin
val client = HttpClient(CIO) {
    install(CasseteKtor) {
        controller = casseteController
    }
}
```

The plugin uses the same controller and tape engine as OkHttp.

## Demo app

The demo app now acts as a host app instead of relying on transport ownership inside `sessionkit`.

`app/src/main/java/com/hellmannratti/vcr/VcrApp.kt` creates:

- one shared `CasseteRuntime`
- one app-owned `OkHttpClient` with `CasseteOkHttp` installed
- one `OkHttpNetworkClient` for the ViewModel layer

The demo still supports:

- runtime mode switching
- tape import and export
- replay controls backed by `SessionPlayer`
- deterministic replay cursor reset when rewinding UI state

## Tape format

Tapes are newline-delimited JSON:

```json
{"schema":1,"seq":0,"type":"SESSION_START","ts":1700000000000,"metadata":{},"appVersion":"1.0","device":"emulator"}
{"schema":1,"seq":1,"type":"UI_EVENT","ts":1700000000100,"metadata":{},"screen":"ApiTest","event":"FetchRandomPokemon","payload":{}}
{"schema":1,"seq":2,"type":"REQUEST","ts":1700000000200,"metadata":{},"requestId":"r1","method":"GET","url":"https://pokeapi.co/api/v2/pokemon/25","bodySha256":null}
{"schema":1,"seq":3,"type":"RESPONSE","ts":1700000000300,"metadata":{},"requestId":"r1","code":200,"headers":{"content-type":"application/json"},"body":"{...}","durationMs":123}
```

Request matching is driven by:

- HTTP method
- normalized URL
- optional request body hash

Repeated identical requests are replayed in FIFO order via per-request cursors.

## Privacy hooks

Core configuration includes hooks for:

- request-header redaction
- response-header redaction
- request-body redaction
- response-body redaction

Example:

```kotlin
val config = CasseteConfig(
    initialMode = Mode.RECORD,
    requestHeaderRedactor = HeaderRedactor.redactAuthTokens(),
    responseHeaderRedactor = HeaderRedactor.redactAuthTokens()
)
```

## Replay policies

Two behaviors are configurable:

- `replayMissPolicy`
  - `THROW` fails fast when a replayed request has no matching response
  - `PASSTHROUGH` falls back to live network
- `missingTapePolicy`
  - `FAIL` fails when replay mode cannot load a tape
  - `PASSTHROUGH` allows live network instead

## Supported versions in this repo

The implementation and tests in this repository are wired against:

- Kotlin `2.2.21`
- OkHttp `4.12.0`
- Retrofit `2.11.0`
- Ktor `3.1.3`
- Android `minSdk 24`

## Build and verification

Useful commands:

```bash
./gradlew :cassete-core:compileKotlin
./gradlew :cassete-okhttp:test
./gradlew :cassete-ktor:test
./gradlew :app:testDebugUnitTest
./gradlew :sessionkit:testDebugUnitTest
./gradlew :app:assembleDebug
```

GitHub Actions runs the same headless verification suite on `push` and `pull_request` via `.github/workflows/ci.yml`.

## Publishing

Each published module applies `maven-publish`.

Current coordinates:

- `com.hellmannratti.cassete:cassete-core:0.1.0-SNAPSHOT`
- `com.hellmannratti.cassete:cassete-okhttp:0.1.0-SNAPSHOT`
- `com.hellmannratti.cassete:cassete-ktor:0.1.0-SNAPSHOT`

Publish to the local Maven cache with:

```bash
./gradlew :cassete-core:publishToMavenLocal \
  :cassete-okhttp:publishToMavenLocal \
  :cassete-ktor:publishToMavenLocal
```

## Current validation coverage

This repo now has automated coverage for:

- tape parsing and legacy NDJSON compatibility
- replay cursor and matching behavior
- malformed-tape diagnostics
- OkHttp record and replay behavior
- Retrofit record and replay compatibility
- Ktor plugin record and replay behavior with a real CIO engine
- demo app replay gating and deterministic rewind behavior
- demo app assembly
