# Task 07: Replay Gating

## Summary
Ensure replay mode disables real external inputs and substitutes recorded values.

## Tasks
- Add a replay mode switch that routes clock/random/UUID/network calls to recorded streams.
- Guard side-effect code paths so they don’t hit real services during replay.
- Validate that event ordering during replay matches recorded sequence IDs.

## Acceptance Criteria
- When replay is active, no real network/time/random calls execute; only recorded data is used.
- Logs/metrics confirm calls are sourced from the tape with correct sequencing.
- Replay switch is easy to toggle (e.g., via dev UI or config flag).
