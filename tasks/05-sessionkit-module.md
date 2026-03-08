# Task 05: Sessionkit Module

## Summary
Create an internal `:sessionkit` module that owns recording/replay plumbing with a minimal app-facing seam.

## Tasks
- Create module structure with recorder, player, event sink decorator, and dependency providers.
- Keep public surface small: hook for UiEvent recording and factories for replayable clock/random/network.
- Move existing VCR recorder/player code into the module, adapting to new schema.
- Ensure app module depends only on the seam, not internals.

## Notes (current state)
- `:sessionkit` still exists in the repo, but it is now a legacy compatibility module rather than the primary architecture.
- The demo app no longer depends on `:sessionkit`; it uses `:cassete-core` plus `:cassete-okhttp`.
- The new modules (`:cassete-core`, `:cassete-okhttp`, `:cassete-ktor`) own the adapter-based direction described in the migration plan.
- `SessionKit` is now explicitly deprecated in code to discourage new usage while the compatibility surface remains available.

## Acceptance Criteria
- `:sessionkit` builds and exposes only the intended interfaces.
- App compiles using the new seam; no direct references to recorder internals remain.
- Existing VCR functionality works via the module in RECORD/REPLAY modes.
