# Task 05: Sessionkit Module

## Summary
Create an internal `:sessionkit` module that owns recording/replay plumbing with a minimal app-facing seam.

## Verified Status

- Status: Superseded
- Verified on 2026-04-15 against `settings.gradle.kts`, `app` module wiring, and the deprecated `sessionkit` source tree.
- The legacy `sessionkit/` code still exists and is explicitly deprecated.
- The current root build no longer includes `:sessionkit`, so this is not an active delivery path in the live Gradle project graph.

## Tasks
- Create module structure with recorder, player, event sink decorator, and dependency providers.
- Keep public surface small: hook for UiEvent recording and factories for replayable clock/random/network.
- Move existing VCR recorder/player code into the module, adapting to new schema.
- Ensure app module depends only on the seam, not internals.

## Notes (current state)
- `:sessionkit` still exists in the repo, but it is now a legacy compatibility module rather than the primary architecture.
- `settings.gradle.kts` no longer includes `:sessionkit`, so `./gradlew :sessionkit:testDebugUnitTest` currently fails with `project 'sessionkit' not found`.
- The demo app no longer depends on `:sessionkit`; it uses `:cassete-core` plus `:cassete-okhttp`.
- The new modules (`:cassete-core`, `:cassete-okhttp`, `:cassete-ktor`) own the adapter-based direction described in the migration plan.
- `SessionKit` is now explicitly deprecated in code to discourage new usage while the compatibility surface remains available.

## Acceptance Criteria
- [ ] `:sessionkit` builds and exposes only the intended interfaces in the current root project.
- [x] App compiles using the new adapter-based seam; no direct references to recorder internals remain on the primary app path.
- [ ] Existing VCR functionality is verified via `:sessionkit` in the current root project.
