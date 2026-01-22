# Task 05: Sessionkit Module

## Summary
Create an internal `:sessionkit` module that owns recording/replay plumbing with a minimal app-facing seam.

## Tasks
- Create module structure with recorder, player, event sink decorator, and dependency providers.
- Keep public surface small: hook for UiEvent recording and factories for replayable clock/random/network.
- Move existing VCR recorder/player code into the module, adapting to new schema.
- Ensure app module depends only on the seam, not internals.

## Acceptance Criteria
- `:sessionkit` builds and exposes only the intended interfaces.
- App compiles using the new seam; no direct references to recorder internals remain.
- Existing VCR functionality works via the module in RECORD/REPLAY modes.
