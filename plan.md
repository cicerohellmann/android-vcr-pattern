# Cassete Migration Plan

## Goal

Build Cassete as a reusable record/replay library for Android apps that already use an HTTP client.

Primary supported integrations:

- OkHttp: first-class adapter
- Retrofit: supported through the OkHttp adapter, with dedicated tests and sample usage
- Ktor client: first-class plugin

What success looks like:

- A host app can install Cassete into an existing OkHttp client without giving up control of its client configuration.
- A Retrofit app works by using an OkHttp client that already has Cassete installed.
- A Ktor app can install a Cassete plugin directly in its `HttpClient`.
- The tape format remains deterministic and backward compatible with the current NDJSON schema unless a schema bump is explicitly chosen.
- The demo app still works and becomes a consumer of the new modules instead of the place where transport logic lives.

## Current State

The repo already has the core idea implemented, but not in an adapter-friendly shape.

- `:sessionkit` is an Android library, not a transport-neutral core.
- `SessionKit` owns and builds its own `OkHttpClient`.
- The current public API is centered on `SessionKit.networkClient()`, which exposes only `get` and `post`.
- `RecordingInterceptor` and `ReplayerInterceptor` exist, but they are internal implementation details.
- Tape loading, matching, and replay state are mixed with OkHttp-specific types.
- Default URL patterns are hardcoded for demo APIs.
- There is no Ktor module, no Ktor plugin, and no publishing setup.

Repo evidence:

- `settings.gradle.kts` includes only `:app` and `:sessionkit`
- `sessionkit/build.gradle.kts` applies `com.android.library` and depends directly on OkHttp
- `sessionkit/src/main/java/com/hellmannratti/vcr/sessionkit/SessionKit.kt` builds and owns `OkHttpClient`

## The Core Problem To Solve First

The library must stop owning the HTTP client.

That is the main architectural blocker. As long as Cassete builds its own OkHttp client internally, it is not a plugin. It is a wrapped network stack. That prevents clean adoption in:

- existing OkHttp apps with custom interceptors, auth, TLS, caching, and retries
- Retrofit apps that already manage their own OkHttp stack
- Ktor apps, because Ktor needs a plugin, not a hidden OkHttp client

The first major milestone is therefore:

- extract a transport-neutral core
- make OkHttp and Ktor thin adapters on top of that core

## Recommended Target Architecture

Use a layered design.

### Module layout

- `:cassete-core`
- `:cassete-okhttp`
- `:cassete-ktor`
- `:app`

Optional transitional module:

- keep `:sessionkit` temporarily as a compatibility facade while the demo app migrates

### Responsibility of each module

` :cassete-core `

- Tape event model
- NDJSON serialization and parsing
- Session/tape recorder
- Replay tape and cursor state
- Request matching model
- URL normalization and request matching rules
- Mode switching (`RECORD`, `REPLAY`, `PASSTHROUGH`)
- Redaction and normalization contracts
- Diagnostics model
- Generic request/response snapshot types

` :cassete-okhttp `

- Public OkHttp interceptor or install API
- Request body hashing for OkHttp request bodies
- Response capture and replay using OkHttp primitives
- OkHttp-specific tests using MockWebServer

` :cassete-ktor `

- Public Ktor client plugin
- Request/response capture and replay through Ktor pipelines
- Ktor-specific tests using MockEngine plus at least one real engine integration

` :app `

- Demo consumer of the new modules
- Sample UI recording and replay controls
- Import/export workflow for tapes

## Scope Boundaries

The goal needs a strict v1 boundary.

In scope for v1:

- HTTP request/response recording and replay
- UI event and action recording if we want to preserve current demo behavior
- deterministic replay with per-request cursors
- mode switching
- tape import/export
- configurable URL normalization and redaction

Out of scope for v1:

- WebSockets
- Server-Sent Events
- duplex request bodies
- one-shot streaming request bodies
- very large body storage optimizations
- non-HTTP side effects beyond the current clock/random/id hooks
- server-side Ktor

## Public API Direction

The public API should be installation-based, not ownership-based.

### Core API shape

Introduce a runtime/controller object that does not know about OkHttp or Ktor:

```kotlin
interface CasseteController {
    val mode: StateFlow<Mode>
    fun switchMode(mode: Mode)
    fun loadTape(file: File): TapeLoadResult
    fun resetReplayCursors()
    fun recordUiEvent(screen: String, event: String, payload: Map<String, JsonElement>)
    fun recordAction(name: String, details: Map<String, JsonElement>)
}
```

Introduce transport-neutral models:

```kotlin
data class HttpRequestSnapshot(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val bodySha256: String?,
    val bodyUtf8: String?
)

data class HttpResponseSnapshot(
    val code: Int,
    val headers: Map<String, String>,
    val bodyUtf8: String,
    val durationMs: Long
)
```

Introduce extension points:

- `RequestMatcher`
- `UrlNormalizer`
- `HeaderRedactor`
- `BodyRedactor`
- `MissingTapePolicy`

### OkHttp API shape

Recommended API:

```kotlin
val controller = Cassete.create(...)

val client = OkHttpClient.Builder()
    .addInterceptor(CasseteOkHttp.interceptor(controller))
    .build()
```

Alternative:

```kotlin
CasseteOkHttp.install(builder, controller)
```

### Retrofit API shape

Retrofit does not need a deep custom adapter first. Retrofit should ride on the OkHttp integration:

```kotlin
val okHttp = OkHttpClient.Builder()
    .addInterceptor(CasseteOkHttp.interceptor(controller))
    .build()

val retrofit = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(okHttp)
    .build()
```

This means Retrofit support is mostly:

- guaranteed compatibility
- sample usage
- integration tests
- documentation

### Ktor API shape

Recommended API:

```kotlin
val client = HttpClient(CIO) {
    install(CasseteKtor) {
        controller = casseteController
    }
}
```

## Refactor Strategy

Do this in phases. Do not try to land everything in one change.

### Phase 0: Freeze behavior with tests

Add tests that capture the current semantics before the refactor changes the module layout.

- request matching by method + URL + optional body hash
- replay cursor behavior
- missing tape behavior
- tape load diagnostics
- request/response ordering by `seq`
- current NDJSON schema compatibility

Deliverable:

- confidence that refactoring preserves existing record/replay behavior

### Phase 1: Extract `:cassete-core`

Create a pure Kotlin/JVM module and move the transport-neutral pieces into it.

Move or refactor these concepts out of `:sessionkit`:

- `Event`
- `Mode`
- `NoTapeFoundException`
- `SessionRecorder`
- `TapeLoader`
- `ReplayTape`
- `TapeLogger`
- `UrlPattern`
- ports for clock/random/id

Important change:

- `ReplayTape` must stop depending on `okhttp3.Request` and `okhttp3.HttpUrl`

Replace those dependencies with transport-neutral request models.

Deliverable:

- a core module with no dependency on Android and no dependency on OkHttp

### Phase 2: Build `:cassete-okhttp`

Make OkHttp a thin adapter over the core runtime.

Required work:

- expose a public interceptor or install helper
- move OkHttp request-body hashing here
- move OkHttp response reconstruction here
- allow dynamic mode switching through the shared controller
- define clear behavior for replay misses

Deliverable:

- an app can install Cassete into an existing `OkHttpClient.Builder`

### Phase 3: Retrofit support

Retrofit support should come immediately after OkHttp, because it is mostly a compatibility milestone rather than a new engine.

Required work:

- add a Retrofit sample or dedicated integration test
- verify converters still behave correctly with replayed bodies
- verify error responses and headers pass through correctly
- document installation for common Retrofit setups

Deliverable:

- a Retrofit app can reuse the same OkHttp client and get full Cassete behavior

### Phase 4: Build `:cassete-ktor`

Create a Ktor client plugin on top of the same controller and tape engine.

Required work:

- intercept outgoing requests
- capture request metadata
- capture or synthesize responses
- replay responses from tape without network access
- map Ktor pipeline concepts to the same record/replay semantics as OkHttp

Design note:

- body capture in Ktor needs careful handling to avoid consuming content twice

Deliverable:

- a Ktor Android app can install Cassete directly in its `HttpClient`

### Phase 5: Migrate the demo app

Update `:app` to consume the new modules instead of depending on current `:sessionkit` internals.

Recommended approach:

- first migrate the app to `:cassete-core` + `:cassete-okhttp`
- keep a temporary facade if needed to reduce churn
- remove old direct transport ownership once the app is stable

Deliverable:

- the sample app proves real host-app integration using the new API

### Phase 6: Publishing and release readiness

Once the modules are stable, make them consumable outside this repo.

Required work:

- add `maven-publish`
- choose stable coordinates
- choose stable package names
- add versioning policy
- document supported versions of OkHttp, Retrofit, and Ktor

Deliverable:

- a host app can consume the artifacts without using a source checkout

## Repo Changes Needed

These are the concrete repo-level changes that will likely happen.

- `settings.gradle.kts`: include new modules
- root build logic: apply shared plugin and dependency configuration for new modules
- split current `sessionkit/` sources into new module directories
- keep `app/` as demo consumer
- update README to describe adapters instead of a monolithic SessionKit client

Likely target module list:

- `:cassete-core`
- `:cassete-okhttp`
- `:cassete-ktor`
- `:app`

Optional temporary module:

- `:sessionkit` as compatibility facade during migration

## Specific Technical Work Items

### Matching and normalization

Replace the current hardcoded sample URL patterns with host-provided configuration.

Needed features:

- explicit URL normalization rules
- optional request body hash matching
- configurable ignored headers
- configurable auth-token redaction
- deterministic fallback strategy for repeated identical requests

### Recording storage

Keep NDJSON for now unless performance forces a change.

Needed features:

- append-only writer
- schema compatibility checks
- tape load diagnostics
- clear error messages for malformed tape files

### Replay behavior

Needed policies:

- what happens when no match is found in REPLAY mode
- whether to fail fast or fall back to live network
- whether to simulate recorded latency
- whether to preserve recorded headers exactly or sanitize some transport headers

### Security and privacy

This is mandatory for real adoption.

Needed features:

- request-header redaction
- response-header redaction
- body redaction hooks
- clear documentation about sensitive data in tapes

## Biggest Risks

- Streaming and one-shot bodies are difficult to support generically.
- Ktor request/response pipeline behavior differs from OkHttp and will need adapter-specific care.
- Dynamic auth headers, timestamps, and nonces can make replay matching brittle without normalization hooks.
- Retries and concurrency can create subtle mismatches if request identity is not defined carefully.
- A too-large public API will be hard to stabilize later.

## Decisions To Make Early

These decisions should be made before code churn gets large.

- final module names: keep `sessionkit` or rename to `cassete-*`
- package names: keep `com.hellmannratti.vcr.*` or move to a new stable namespace
- schema strategy: keep current schema as v1 or introduce a v2 migration
- replay miss behavior: throw, passthrough, or configurable
- whether `SessionPlayer` stays in scope for the first adapterization milestone

## Validation Strategy

Every phase needs automated checks.

### Core validation

- tape parsing tests
- schema compatibility tests
- replay cursor tests
- matching tests
- diagnostics tests

### OkHttp validation

- recording with MockWebServer
- replay without network
- POST body hash matching
- repeated request cursor progression
- header preservation and sanitization behavior

### Retrofit validation

- converter compatibility
- success and error response decoding
- replay behavior through a real Retrofit service interface

### Ktor validation

- record mode request capture
- replay mode response synthesis
- repeated request handling
- request body capture edge cases

### App validation

- `:app:assembleDebug`
- import/export tape flow still works
- demo replay controls still work

## Definition Of Done

The goal is achieved when all of the following are true:

- OkHttp integration is public, documented, and tested
- Retrofit works through the OkHttp integration and has dedicated integration coverage
- Ktor client plugin exists, is public, documented, and tested
- the core module has no OkHttp dependency
- the demo app consumes the new public API
- tape behavior remains deterministic
- privacy/redaction hooks exist
- artifacts can be published and consumed by another Android app

## Recommended Execution Order

This is the practical order to implement the work in this repo.

1. Add characterization tests around the current tape behavior.
2. Create `:cassete-core` and move transport-neutral logic into it.
3. Build `:cassete-okhttp` on top of the new core.
4. Migrate `:app` to the new OkHttp adapter.
5. Add Retrofit integration tests and sample usage.
6. Build `:cassete-ktor`.
7. Add publishing, docs, and release polish.

## Short Version

If we want this goal to succeed, we should not start with Retrofit or Ktor code.

We should first extract a transport-neutral core, then make OkHttp the first real adapter, let Retrofit ride on that adapter, and only then add a Ktor plugin.
