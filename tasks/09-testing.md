# Task 09: Testing

## Summary
Validate deterministic replay via unit and end-to-end coverage.

## Tasks
- Add unit tests for event serialization/deserialization and tape building.
- Add player tests for play/pause/step/rewind behavior and state restoration.
- Add integration test: record a session (UI + nondeterministic inputs), replay it with external inputs disabled, and assert identical state/output.

## Acceptance Criteria
- Tests cover schema correctness, player controls, and deterministic replay.
- End-to-end test fails if live external inputs are accidentally used during replay.
- CI can run the suite headlessly.
