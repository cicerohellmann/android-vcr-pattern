# Task 07: Replay Gating

## Summary
Ensure replay mode disables real external inputs and substitutes recorded values.

## Tasks
- Add a replay mode switch that routes clock/random/UUID/network calls to recorded streams.
- Guard side-effect code paths so they don’t hit real services during replay.
- Validate that event ordering during replay matches recorded sequence IDs.

## Notes (progress)
- Manual screen inputs are gated while the replay player is loaded; only player-driven events are accepted.
- `ND_RANDOM_INT` values are now consumed from the loaded tape during player-driven replay, so replay no longer falls back to live randomness for `FetchRandomPokemon`.
- Replay nondeterminism is now loaded through a shared ordered stream that also understands recorded `ND_TIME` and `ND_UUID` events for future replay call sites.
- Network replay remains mediated by `CasseteOkHttp`, which serves recorded responses before the backend interceptor is reached.
- Recorded time/UUID substitution is still not wired into player-driven replay.

## Acceptance Criteria
- When replay is active, no real network/time/random calls execute; only recorded data is used.
- Logs/metrics confirm calls are sourced from the tape with correct sequencing.
- Replay switch is easy to toggle (e.g., via dev UI or config flag).
