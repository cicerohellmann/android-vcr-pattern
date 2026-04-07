# Standalone Library Execution Plan

## Goal

Turn `android-vcr-pattern` into a standalone library-first repository where the library is the product, the adapters are the official integration surface, and the app remains only a proof/consumer.

## User Items

- [x] Elaborate on how to address every listed problem in a clear way.
- [x] Define the right steps to resolve each problem.
- [x] Define acceptance criteria so progress is easy to execute and verify.
- [x] Persist the plan in a session-safe markdown file that can be continued later.

## Working Definition

- The product is the library, not the demo app.
- The library owns tape loading, replay behavior, diagnostics, matching, redaction, and install contracts.
- The app exists only to prove the public APIs in a realistic host-app shape.
- "Any HTTP client" means "support through adapters," not "first-class direct support for every client immediately."

## Recommended Path

- [ ] Story 1: Lock the product boundary.
- [ ] Story 2: Standardize the build and verification baseline.
- [ ] Story 3: Harden the public library contract.
- [ ] Story 4: Harden and document the transport adapters.
- [ ] Story 5: Reduce the app to a proof harness only.
- [ ] Story 6: Decide and execute the `:sessionkit` exit path.
- [ ] Story 7: Validate publishing and release readiness.

## Current Progress

- [x] Made the README explicitly library-first.
- [x] Documented the product-path versus sample-path module boundary.
- [x] Standardized the repository on a Java 17 build baseline in Gradle.
- [x] Verified the pure JVM modules on the Java 17 baseline.
- [ ] Finish Android-side verification once a local Android SDK is configured for this checkout.

## Story 1: Lock the Product Boundary

### Problem To Solve

The repository direction is mostly correct, but the team still needs an explicit rule for what belongs in the library and what belongs only in the sample app.

### Resolution Steps

- [x] Write a short library-first statement in the primary docs.
- [x] Produce a module ownership matrix:
  - `:cassete-core` owns tape model, replay behavior, diagnostics, matching, redaction, player, and controller contracts.
  - `:cassete-okhttp` owns OkHttp installation and OkHttp-specific body/response handling.
  - `:cassete-ktor` owns Ktor installation and Ktor-specific pipeline handling.
  - `:app` owns sample wiring, sample UI, and sample UX only.
- [ ] Audit the app package for any logic that should live in library modules instead.
- [ ] Mark every app behavior as one of:
  - sample-only,
  - product behavior already in library,
  - generic behavior that still needs to move into the library.
- [ ] Record the audit result in the decision log below before moving code.

### Verification Steps

- [x] Review imports in `:app` and confirm it consumes only public module APIs.
- [ ] Review docs and confirm they describe library modules first and the sample second.
- [ ] Review open tasks and confirm no new product behavior is assigned to the app layer.

### Acceptance Criteria

- The repository has one explicit product boundary.
- New behavior is added to library modules by default unless it is clearly sample-only.
- The app is treated as proof-of-usage rather than the architecture center.

## Story 2: Standardize the Build and Verification Baseline

### Problem To Solve

The current local verification path is blocked by a Java toolchain mismatch. Work will stay slow and risky until the baseline is made explicit.

### Recommended Decision

- [ ] Move the repository baseline to Java 17.

Reasoning:

- CI already runs with JDK 17.
- The Android and Kotlin stack in this repo already runs on a modern toolchain.
- Keeping Java 11 adds setup friction without a clear product benefit for this repository.

### Resolution Steps

- [x] Update JVM modules to use Java 17 toolchains.
- [x] Update Android module compile options and Kotlin targets to Java 17.
- [x] Update CI and docs so the required Java version is stated once and consistently.
- [x] Remove ambiguity from local setup instructions.
- [ ] Configure Android SDK discovery for this checkout.
- [ ] Run the full verification suite after the baseline change.

### Verification Steps

- [ ] `./gradlew :cassete-core:test`
- [ ] `./gradlew :cassete-okhttp:test`
- [ ] `./gradlew :cassete-ktor:test`
- [ ] `./gradlew :app:testDebugUnitTest`
- [ ] `./gradlew :sessionkit:testDebugUnitTest`
- [ ] `./gradlew :app:assembleDebug`

### Acceptance Criteria

- A contributor can verify the repo locally with a single documented Java baseline.
- CI and local development agree on the same Java version.
- The full headless verification suite passes after the change.

## Story 3: Harden the Public Library Contract

### Problem To Solve

The standalone-library goal only holds if a host app can use Cassete through clean public contracts instead of sample-specific knowledge.

### Resolution Steps

- [ ] Audit `CasseteConfig`, `CasseteController`, `CasseteRuntime`, and adapter entry points against the library-first goal.
- [ ] Confirm that tape loading, replay control, diagnostics, matching, missing-tape behavior, and redaction are library-owned responsibilities.
- [ ] Remove or avoid public API shapes that only make sense in the sample app.
- [ ] Document the intended host-app install shape for:
  - create controller,
  - install adapter,
  - switch modes,
  - load tape,
  - reset replay,
  - share diagnostics.
- [ ] Add or tighten integration tests where the public contract is the only thing used.

### Verification Steps

- [ ] Review the public surface in `:cassete-core`, `:cassete-okhttp`, and `:cassete-ktor`.
- [ ] Confirm the sample app can be understood as a consumer of those APIs.
- [ ] Confirm there is no required logic hidden only in app classes.

### Acceptance Criteria

- A host app can adopt Cassete without copying sample-app internals.
- The public APIs describe the product clearly enough to document and publish.
- Library features are validated through public usage paths.

## Story 4: Harden and Document the Transport Adapters

### Problem To Solve

The library promise depends on stable adapter behavior. The adapters must be thin, predictable, and explicitly documented.

### Resolution Steps

- [ ] Keep OkHttp as the primary install path and verify record/replay/body-hash/replay-miss behavior.
- [ ] Keep Retrofit as compatibility coverage through the OkHttp adapter instead of inventing a separate Retrofit integration layer.
- [ ] Keep Ktor as a first-class plugin with parity on the important replay semantics.
- [ ] Define the adapter extension rule for future HTTP clients:
  - new transport support should be a new adapter module,
  - not a rewrite of `:cassete-core`,
  - not sample-app logic.
- [ ] Document the install pattern for each supported transport.

### Verification Steps

- [ ] OkHttp tests cover record, replay, repeated requests, and replay miss behavior.
- [ ] Retrofit tests prove the same OkHttp client works in record and replay modes.
- [ ] Ktor tests prove record/replay behavior through a real engine path.
- [ ] README examples match the tested installation patterns.

### Acceptance Criteria

- OkHttp is the first-class documented adapter.
- Retrofit compatibility is proven without becoming a separate integration model.
- Ktor is a real first-class adapter, not a placeholder.
- Future client support has a clear extension rule.

## Story 5: Reduce the App to a Proof Harness Only

### Problem To Solve

If the app carries behavior the library does not, the repository stops being library-first and becomes sample-driven again.

### Resolution Steps

- [ ] Audit the app for any generic behavior that should be moved down into library modules.
- [ ] Keep only sample concerns in the app:
  - demo UI,
  - sample mode toggles,
  - tape import/export UX,
  - player controls that demonstrate the product,
  - host-app wiring.
- [ ] Remove or avoid app-specific fallback behavior that changes product semantics.
- [ ] Keep the sample small enough that new adopters can understand the install flow quickly.

### Verification Steps

- [ ] Review the app package and list every behavior as sample-only or product-owned.
- [ ] Confirm the app uses only public module APIs.
- [ ] Confirm the app still demonstrates install, load tape, replay, and diagnostics sharing.

### Acceptance Criteria

- The app proves the library instead of compensating for it.
- A developer reading the sample can copy the integration pattern into a real app.
- Product behavior is not trapped in the app layer.

## Story 6: Decide and Execute the `:sessionkit` Exit Path

### Problem To Solve

The repository still carries a deprecated compatibility module. That is acceptable only if the exit path is explicit.

### Recommended Decision

- [ ] Freeze `:sessionkit` immediately.
- [ ] Remove it only after the standalone-library path is fully validated.

### Resolution Steps

- [ ] Mark `:sessionkit` as compatibility-only in docs and comments.
- [ ] Do not add new product behavior to `:sessionkit`.
- [ ] Keep only the minimum tests required to protect the transition period.
- [ ] Once the library-first path is stable, remove `:sessionkit` in a dedicated cleanup.

### Verification Steps

- [ ] Review recent changes and confirm no new features land in `:sessionkit`.
- [ ] Review docs and confirm the primary path no longer points to `:sessionkit`.
- [ ] Confirm removal can be done without breaking the sample's primary path.

### Acceptance Criteria

- The repository has one primary architecture.
- `:sessionkit` is either clearly frozen or cleanly removed.
- No ambiguity remains about where new work belongs.

## Story 7: Validate Publishing and Release Readiness

### Problem To Solve

The repository is only truly library-first when the modules can be consumed outside the sample app.

### Resolution Steps

- [ ] Validate `publishToMavenLocal` for `:cassete-core`, `:cassete-okhttp`, and `:cassete-ktor`.
- [ ] Confirm coordinates, versioning, and supported environments are documented.
- [ ] Confirm the sample app can act as a consumption proof, not the source of truth.
- [ ] Define the first release scope around the standalone library contract only.

### Verification Steps

- [ ] `./gradlew :cassete-core:publishToMavenLocal :cassete-okhttp:publishToMavenLocal :cassete-ktor:publishToMavenLocal`
- [ ] Verify docs for setup, versions, and supported adapters.
- [ ] Review release notes draft and confirm it describes library capabilities first.

### Acceptance Criteria

- Consumers can use the published modules without depending on sample-specific internals.
- Versioning and support expectations are explicit.
- Release scope is tight, testable, and library-first.

## Execution Order

- [ ] Complete Story 1 before code movement decisions.
- [ ] Complete Story 2 before adding more feature work.
- [ ] Complete Stories 3 and 4 as the product hardening phase.
- [ ] Complete Story 5 after the public contract is stable.
- [ ] Complete Story 6 once the library-first path is validated.
- [ ] Complete Story 7 last.

## Definition of Done

- [ ] The repository can be explained as a standalone library with supported adapters.
- [ ] The app is only a proof harness and sample consumer.
- [ ] The build baseline is explicit and reproducible.
- [ ] The public library contract is stable enough to document and publish.
- [ ] The adapter story is clear: OkHttp first-class, Retrofit through OkHttp, Ktor first-class, future clients through new adapters.
- [ ] `:sessionkit` is no longer an architectural question.
- [ ] Publishing works for the product modules.

## Decision Log

- `2026-03-17`: Reframed the repository as library-first, sample-second.
- `2026-03-17`: Clarified that future client support should happen through adapters, not by making app logic more central.
- `2026-03-17`: Recommended Java 17 as the single repository baseline to remove current verification friction.
- `2026-03-17`: Implemented the Java 17 baseline change and confirmed `:cassete-core:test`, `:cassete-okhttp:test`, and `:cassete-ktor:test` pass locally.
- `2026-03-17`: Android-side verification remains blocked on this machine because no Android SDK location is configured for the checkout.
- `2026-03-17`: Confirmed the app depends on `:cassete-core` and `:cassete-okhttp` only and does not reference `:sessionkit`.
