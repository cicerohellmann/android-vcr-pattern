# Task 08: QA → Dev UX

## Summary
Deliver a smooth export/import flow so QA can share sessions and devs can load them for replay.

## Verified Status

- Status: Complete
- Verified on 2026-04-15 against `ApiTestViewModel`, `AndroidManifest.xml`, and the current app integration surfaces.

## Tasks
- Implement export of the session log (share/save) with clear naming/versioning.
- Implement import/picker to load a session file and auto-switch app into replay with the selected tape.
- Add basic validation and error surfacing for bad/mismatched session files.

## Acceptance Criteria
- [x] QA can export a session file from the app; file includes version info in the recorded tape content.
- [x] Dev can pick/import a session file and immediately replay it; failures surface via toast plus persisted tape-load diagnostics.
- [x] Flow aligns with app privacy/sandbox rules (`FileProvider`, scoped storage / `MediaStore`).
