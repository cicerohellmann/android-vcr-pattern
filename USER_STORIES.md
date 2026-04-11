# User Stories — android-vcr-pattern (Cassete Migration)

## US-0: Freeze Current Behavior with Characterization Tests
**As a** developer, **I want** tests that capture the current record/replay semantics, **so that** refactoring preserves existing behavior.

### Acceptance Criteria
- [x] AC-0.1: Write tests for request matching by method + URL + optional body hash
- [x] AC-0.2: Write tests for replay cursor behavior (sequential progression through repeated requests)
- [x] AC-0.3: Write tests for missing tape behavior (what happens when no match found)
- [x] AC-0.4: Write tests for tape load diagnostics (malformed files, empty tapes)
- [x] AC-0.5: Write tests for request/response ordering by `seq` field
- [x] AC-0.6: Write tests for current NDJSON schema compatibility (round-trip serialize/deserialize)

## US-1: Extract Transport-Neutral Core Module
**As a** library author, **I want** a `:cassete-core` module with no OkHttp or Android dependencies, **so that** multiple HTTP client adapters can share the same tape engine.

### Acceptance Criteria
- [x] AC-1.1: Create `:cassete-core` module in `settings.gradle.kts` with pure Kotlin/JVM dependencies only
- [x] AC-1.2: Move/refactor `Event`, `Mode`, `NoTapeFoundException` to core module
- [x] AC-1.3: Move/refactor `SessionRecorder` to core, replacing OkHttp types with transport-neutral request/response models
- [x] AC-1.4: Move/refactor `TapeLoader` and `ReplayTape` to core, removing `okhttp3.Request` and `okhttp3.HttpUrl` dependencies
- [x] AC-1.5: Create `HttpRequestSnapshot` and `HttpResponseSnapshot` data classes in core
- [x] AC-1.6: Create `CasseteController` interface in core with mode switching, tape loading, and UI/action event recording
- [x] AC-1.7: Move URL normalization (`UrlPattern`) and matching logic to core
- [x] AC-1.8: Move clock/random/id port interfaces to core
- [x] AC-1.9: Core module compiles with zero OkHttp/Android imports
- [x] AC-1.10: All characterization tests from US-0 still pass (adapted to use core types)

## US-2: Build OkHttp Adapter Module
**As a** developer using OkHttp, **I want** to install Cassete as an interceptor on my existing `OkHttpClient.Builder`, **so that** I don't give up control of my HTTP client configuration.

### Acceptance Criteria
- [x] AC-2.1: Create `:cassete-okhttp` module depending on `:cassete-core` + OkHttp
- [x] AC-2.2: Implement public `CasseteOkHttp.interceptor(controller)` API that returns an OkHttp `Interceptor`
- [x] AC-2.3: Move OkHttp request-body hashing to adapter module
- [x] AC-2.4: Move OkHttp response reconstruction to adapter module
- [x] AC-2.5: Support dynamic mode switching through shared controller
- [x] AC-2.6: Write integration tests using MockWebServer (record, replay, mode switch)
- [x] AC-2.7: Verify replay without network access works correctly
- [x] AC-2.8: Define and test behavior for replay misses (configurable: throw vs. passthrough)

## US-3: Add Retrofit Integration Support
**As a** developer using Retrofit, **I want** Cassete to work through my Retrofit client's OkHttp layer, **so that** I get full record/replay with my existing Retrofit service interfaces.

### Acceptance Criteria
- [x] AC-3.1: Add Retrofit integration test demonstrating installation via OkHttp client
- [x] AC-3.2: Verify converters (Gson/Moshi/kotlinx.serialization) work correctly with replayed bodies
- [x] AC-3.3: Verify error responses and headers pass through correctly
- [x] AC-3.4: Write sample usage code showing Retrofit + Cassete setup
- [ ] AC-3.5: Document installation for common Retrofit setups

## US-4: Build Ktor Client Plugin Module
**As a** developer using Ktor, **I want** to install Cassete directly in my `HttpClient`, **so that** Ktor apps get the same record/replay capabilities as OkHttp apps.

### Acceptance Criteria
- [x] AC-4.1: Create `:cassete-ktor` module depending on `:cassete-core` + Ktor client
- [x] AC-4.2: Implement `CasseteKtor` plugin with `install(CasseteKtor) { controller = ... }` API
- [x] AC-4.3: Intercept outgoing requests and capture metadata
- [x] AC-4.4: Capture request bodies without consuming content twice
- [x] AC-4.5: Replay responses from tape without network access
- [x] AC-4.6: Write integration tests using MockEngine + at least one real engine
- [x] AC-4.7: Verify repeated request cursor handling matches OkHttp behavior

## US-5: Migrate Demo App to New Modules
**As a** demo app user, **I want** the sample app to use the new public Cassete API, **so that** it proves real host-app integration.

### Acceptance Criteria
- [x] AC-5.1: Update `:app` to depend on `:cassete-core` + `:cassete-okhttp` instead of `:sessionkit`
- [x] AC-5.2: Replace `SessionKit.networkClient()` calls with new interceptor-based setup
- [x] AC-5.3: Verify recording controls still work (start/stop/mode switch)
- [x] AC-5.4: Verify tape import/export workflow still functions
- [x] AC-5.5: Verify replay UI controls work end-to-end
- [x] AC-5.6: Remove or deprecate old `:sessionkit` module once migration is stable

## US-6: Publishing and Release Readiness
**As a** library consumer, **I want** Cassete published as Maven artifacts, **so that** I can depend on it without a source checkout.

### Acceptance Criteria
- [ ] AC-6.1: Add `maven-publish` plugin to all library modules
- [ ] AC-6.2: Choose stable Maven coordinates (group ID, artifact IDs)
- [ ] AC-6.3: Choose stable package names
- [ ] AC-6.4: Add versioning policy (SemVer)
- [ ] AC-6.5: Document supported versions of OkHttp, Retrofit, and Ktor
- [ ] AC-6.6: Add API documentation (KDoc) for all public types
- [ ] AC-6.7: Update README with adapter-based architecture description

## US-7: Security and Privacy Hooks
**As a** library consumer handling sensitive data, **I want** configurable redaction and normalization, **so that** tapes don't leak auth tokens, PII, or other sensitive information.

### Acceptance Criteria
- [x] AC-7.1: Implement `HeaderRedactor` interface and default implementation (strips Authorization, Cookie)
- [x] AC-7.2: Implement `BodyRedactor` interface for request and response body scrubbing
- [x] AC-7.3: Implement `UrlNormalizer` for stripping/replacing query parameters
- [x] AC-7.4: Implement `MissingTapePolicy` sealed class (Throw, Passthrough, Custom)
- [ ] AC-7.5: Document sensitive data handling best practices
- [x] AC-7.6: Write tests verifying redaction is applied during recording
