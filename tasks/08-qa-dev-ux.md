# Task 08: QA → Dev UX

## Summary
Deliver a smooth export/import flow so QA can share sessions and devs can load them for replay.

## Tasks
- Implement export of the session log (share/save) with clear naming/versioning.
- Implement import/picker to load a session file and auto-switch app into replay with the selected tape.
- Add basic validation and error surfacing for bad/mismatched session files.

## Acceptance Criteria
- QA can export a session file from the app; file includes version info.
- Dev can pick/import a session file and immediately replay it; errors are user-friendly.
- Flow aligns with app privacy/sandbox rules (fileprovider, scoped storage).
