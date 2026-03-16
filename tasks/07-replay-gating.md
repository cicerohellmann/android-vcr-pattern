# Task 07: Replay Gating

## Summary
Ensure replay mode disables real external inputs and substitutes recorded values.

## Tasks
- Add a replay mode switch that routes clock/random/UUID/network calls to recorded streams.
- Guard side-effect code paths so they don’t hit real services during replay.
- Validate that event ordering during replay matches recorded sequence IDs.

## Notes (progress)
- Manual screen inputs are gated while the replay player is loaded; only player-driven events are accepted.
- `ND_RANDOM_INT`, `ND_TIME`, and `ND_UUID` values are now consumed from the loaded tape during player-driven replay, so replayed fetch flows do not fall back to live randomness, wall clock time, or UUID generation.
- Replay nondeterminism is loaded through a shared ordered stream that preserves the original cross-type action ordering from the tape.
- Network replay remains mediated by `CasseteOkHttp`, which serves recorded responses before the backend interceptor is reached.

## Acceptance Criteria
- When replay is active, no real network/time/random calls execute; only recorded data is used.
- Logs/metrics confirm calls are sourced from the tape with correct sequencing.
- Replay switch is easy to toggle (e.g., via dev UI or config flag).
