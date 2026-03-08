# Task 09: Testing

## Summary
Validate deterministic replay via unit and end-to-end coverage.

## Tasks
- Add unit tests for event serialization/deserialization and tape building.
- Add player tests for play/pause/step/rewind behavior and state restoration.
- Add integration test: record a session (UI + nondeterministic inputs), replay it with external inputs disabled, and assert identical state/output.

## Notes (progress)
- Added `:cassete-core` coverage for `SessionPlayer` play/step/step-back behavior and state tracking.
- Added `:cassete-core` characterization tests for `TapeLoader`, including legacy NDJSON envelope backfill, replay-tape validation, latest-session selection, and UI-only tape loading for player replay.
- Added app-level JVM tests covering record-then-replay through `ApiTestViewModel`, including replay gating, deterministic rewind, and proof that replay uses recorded random values and recorded HTTP responses instead of live dependencies.
- Added focused JVM tests for ordered nondeterminism replay and reset behavior across `ND_RANDOM_INT`, `ND_TIME`, and `ND_UUID`.
- Added GitHub Actions workflow `.github/workflows/ci.yml` to run the headless verification suite on `push` and `pull_request`, covering JVM tests plus `:app:assembleDebug`.

## Acceptance Criteria
- Tests cover schema correctness, player controls, and deterministic replay.
- End-to-end test fails if live external inputs are accidentally used during replay.
- CI can run the suite headlessly.
