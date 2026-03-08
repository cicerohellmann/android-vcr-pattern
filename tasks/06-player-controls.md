# Task 06: Player & Controls

## Summary
Implement SessionPlayer with transport controls (play/pause/step/seek/back) and feed events into `onEvent`.

## Tasks
- Build a player that reads the NDJSON log and replays events through the ViewModel `onEvent`.
- Implement controls: play/pause, step forward, step back, seek to position.
- Decide and implement rewind strategy: (a) checkpoints + fast-forward, or (b) reducer recomputation.
- Surface basic dev UI for transport controls and session position display.

## Notes (progress)
- `SessionPlayer` is implemented in `:cassete-core` and replays `UI_EVENT` entries through the screen `onEvent` path.
- The app exposes play/pause, step forward, step back, seek-to-position, start, end, and stop controls through the floating/player controller UI.
- Rewind uses reducer recomputation: on backward seek/step-back, the screen resets to initial state and the player fast-forwards from the start.
- JVM tests now cover player stepping and rewind behavior in both `:cassete-core` and the app-level replay flow.

## Acceptance Criteria
- Player can load a session file and drive the UI deterministically via `onEvent`.
- Transport controls function: play, pause, step fwd/back, seek; rewind restores prior state per chosen strategy.
- Live external inputs are gated off while player is active.
