# Standalone Library Direction

## Goal

Make `android-vcr-pattern` a standalone record/replay library. The app is not the product surface; it is a thin proof that the library can be installed into a host app without taking ownership of the host app's HTTP stack.

## User Items

- [x] Define a path so the current work can be addressed and implemented.
- [x] Validate the core understanding that the repository should center on a standalone library.
- [x] State clearly where I agree and where I would narrow the statement.

## Product Definition

- The library is the primary deliverable.
- The demo app is only a consumer and proof-of-adoption.
- Host apps keep ownership of their HTTP client configuration.
- Cassete provides a transport-neutral core plus thin transport adapters.
- Tape load, replay, diagnostics, and session control belong to the library, not the demo app.
- Dependency injection should happen at the host-app boundary: the host app injects the Cassete controller and installs the appropriate adapter/plugin into its HTTP client.

## Important Refinement

The phrase "any HTTP client" needs to be handled carefully.

- The right target is not "native support for every HTTP client immediately."
- The right target is "a transport-neutral core with official adapters for the clients we support first, plus a clear adapter seam for future clients."
- In the current repo, that means:
  - `:cassete-core` is the product center.
  - `:cassete-okhttp` is the first primary adapter.
  - Retrofit is validated through OkHttp.
  - `:cassete-ktor` is the second primary adapter.
  - Other clients are future adapters, not a promise of zero-work support today.

## What I Agree With

- [x] The standalone library framing is the correct center of the project.
- [x] The demo app should prove integration instead of defining the product architecture.
- [x] The host app should be able to plug Cassete into its existing networking stack instead of giving that stack up.
- [x] The library should be installable, readable from tape, and injectable through normal app composition/DI.

## What I Would Narrow

- [ ] I would not describe the current goal as "works with any HTTP client" without qualification.
- [ ] I would describe the goal as "works with supported adapters now and can be extended to new clients through the same core contracts."

## Story 1: Lock the Product Boundary

### Tasks

- [ ] Treat `:cassete-core`, `:cassete-okhttp`, and `:cassete-ktor` as the product path.
- [ ] Treat `:app` only as a sample consumer and validation harness.
- [ ] Prevent new product behavior from being invented only in the app layer.

### Acceptance Criteria

- All core record/replay semantics live in library modules.
- The app consumes public APIs instead of carrying unique behavior that the library lacks.
- Product documentation describes the library first and the app second.

## Story 2: Restore the Verification Baseline

### Tasks

- [ ] Decide whether the repository baseline remains Java 11 or moves intentionally to Java 17.
- [ ] Align local setup, Gradle toolchains, and CI with that decision.
- [ ] Re-run the full headless verification suite.

### Acceptance Criteria

- Contributors can run the documented verification commands locally.
- CI and local development agree on the Java baseline.
- Verification passes before more feature work is added.

## Story 3: Harden the Public Library Contract

### Tasks

- [ ] Audit the public controller/configuration API against the standalone-library goal.
- [ ] Keep tape loading, replay control, diagnostics, redaction, and matching in library-owned contracts.
- [ ] Remove or avoid app-only assumptions from the public API shape.

### Acceptance Criteria

- A host app can install Cassete without copying app-specific logic.
- Public APIs read like library APIs, not sample-app internals.
- Library behavior is documented independently from the demo app.

## Story 4: Harden the Adapters

### Tasks

- [ ] Keep OkHttp as the primary installation-based adapter.
- [ ] Keep Retrofit as compatibility proven through the OkHttp adapter.
- [ ] Keep Ktor as a first-class plugin adapter.
- [ ] Define future client support as additional adapters, not changes to the core product statement.

### Acceptance Criteria

- OkHttp integration is the main documented path.
- Retrofit and Ktor validation remain green and representative.
- New transport support can be added by implementing an adapter instead of reshaping the core.

## Story 5: Reduce the Demo App to Proof Only

### Tasks

- [ ] Keep the app focused on proving install, record, replay, import/export, and playback flows.
- [ ] Avoid app-only behavior that a real host app could not get from the library.
- [ ] Use the app to verify DI/install patterns, not to own product logic.

### Acceptance Criteria

- The app demonstrates usage rather than compensating for missing library features.
- The sample is small enough to understand quickly.
- A host app developer can copy the integration pattern without depending on demo-only code paths.

## Story 6: Decide the Legacy `:sessionkit` Exit

### Tasks

- [ ] Decide whether `:sessionkit` stays briefly as a frozen compatibility layer or is removed.
- [ ] If it stays, keep it explicitly deprecated and feature-frozen.
- [ ] If it goes, remove it only after the standalone-library path is fully validated.

### Acceptance Criteria

- The repository has one clear primary architecture.
- No new work depends on `:sessionkit`.
- Docs, code, and tests agree on the migration state.

## Story 7: Release the Library, Not the Demo

### Tasks

- [ ] Validate `publishToMavenLocal` for the product modules.
- [ ] Document supported environments and adapter expectations.
- [ ] Define the first release scope around the standalone library contract.

### Acceptance Criteria

- Consumers can use the published modules without depending on the sample app.
- Versioning and support expectations are explicit.
- Release notes describe library capabilities first.

## Recommended Execution Order

- [ ] Story 1: Lock the product boundary.
- [ ] Story 2: Restore the verification baseline.
- [ ] Story 3: Harden the public library contract.
- [ ] Story 4: Harden the adapters.
- [ ] Story 5: Reduce the demo app to proof only.
- [ ] Story 6: Decide the legacy `:sessionkit` exit.
- [ ] Story 7: Release the library, not the demo.

## Decision Log

- `2026-03-17`: Reframed the project around a standalone-library-first definition.
- `2026-03-17`: Confirmed the demo app should be treated as a consumer/proof harness rather than the product center.
- `2026-03-17`: Clarified that "any HTTP client" should mean "extensible through adapters," not "direct first-class support for every client immediately."
