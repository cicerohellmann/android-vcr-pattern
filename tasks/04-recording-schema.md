# Task 04: Recording Schema

## Summary
Extend NDJSON schema to capture UiEvents and nondeterministic outputs with ordering metadata.

## Tasks
- Define event shapes: `{seq, ts?, type, payload, metadata}` for UiEvent and external inputs.
- Add monotonic sequence ID and optional timestamp to all writes.
- Update recorder to append new event types alongside HTTP events (if retained).
- Document schema versioning and validation rules.

## Acceptance Criteria
- Schema doc exists and matches implementation.
- Recorder writes UiEvent and nondeterministic-output events with sequence numbers.
- Basic validation in place (e.g., rejects malformed events, ensures seq increments).
