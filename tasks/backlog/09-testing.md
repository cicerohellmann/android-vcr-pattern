# Task 09: Testing

## Summary
Validate deterministic replay via unit and end-to-end coverage.

## Verified Status

- Status: Partial
- Verified on 2026-04-15 against the current local Gradle suite, app/core tests, and `.github/workflows/ci.yml`.
- The replay/schema/player coverage exists locally and passes.
- The checked-in CI workflow is stale because it still invokes `:sessionkit:testDebugUnitTest`, which is no longer part of the root project.

## Tasks
- Add unit tests for event serialization/deserialization and tape building.
- Add player tests for play/pause/step/rewind behavior and state restoration.
- Add integration test: record a session (UI + nondeterministic inputs), replay it with external inputs disabled, and assert identical state/output.

## Notes (progress)
- Added `:cassete-core` coverage for `SessionPlayer` play/step/step-back behavior and state tracking.
- Added `:cassete-core` characterization tests for `TapeLoader`, including legacy NDJSON envelope backfill, replay-tape validation, latest-session selection, and UI-only tape loading for player replay.
- Added app-level JVM tests covering record-then-replay through `ApiTestViewModel`, including replay gating, deterministic rewind, and proof that replay uses recorded random values and recorded HTTP responses instead of live dependencies.
- Extended the app-level replay tests to assert that player-driven replay also reuses recorded `ND_TIME` and `ND_UUID` values instead of touching the live clock or UUID generator.
- Added focused JVM tests for ordered nondeterminism replay and reset behavior across `ND_RANDOM_INT`, `ND_TIME`, and `ND_UUID`.
- Added GitHub Actions workflow `.github/workflows/ci.yml` to run the headless verification suite on `push` and `pull_request`, covering JVM tests plus `:app:assembleDebug`.
- Current audit note: the workflow still references `:sessionkit:testDebugUnitTest`, which now fails because `settings.gradle.kts` excludes `:sessionkit`.

## Acceptance Criteria
- [x] Tests cover schema correctness, player controls, and deterministic replay.
- [x] End-to-end test fails if live external inputs are accidentally used during replay.
- [ ] CI can run the suite headlessly.
