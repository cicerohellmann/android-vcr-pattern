# Current Implementation Centers

## Goal

Resume from the latest repo-side comments and convert the current Cassete implementation into a clear next-step plan that can be continued across sessions.

## User Items

- [x] Continue from where we stopped by reconstructing the latest repo state from local source, task notes, changelog, and recent commits.
- [x] Check the last comments by reviewing the latest documented notes in `CHANGELOG.md`, the latest commit, and the task markdown files.
- [x] Give clear direction on how to move forward with the current implementation per center through the stories below.

## Current Read

- The migration from a client-owning `:sessionkit` architecture to `:cassete-core`, `:cassete-okhttp`, and `:cassete-ktor` is already implemented.
- The latest completed implementation center is the replay nondeterminism follow-up from commit `dd6c403` dated `2026-03-16`.
- The current local verification blocker is environmental: Gradle requests a Java 11 toolchain, while this machine only exposes Java 17.

## Story 1: Restore Verification Baseline

### Why this is first

Without a green local verification path, every other change will be slower and riskier.

### Tasks

- [ ] Decide the Java baseline intentionally: keep Java 11 toolchains or raise the repo baseline to Java 17.
- [ ] If keeping Java 11, document and configure the expected local setup so Gradle can resolve a Java 11 compiler consistently.
- [ ] If raising to Java 17, update build scripts, CI assumptions, and compatibility docs together.
- [ ] Re-run the headless verification suite after the baseline decision.

### Acceptance Criteria

- A contributor can run the documented Gradle verification commands locally without toolchain guesswork.
- CI and local development use a clearly stated Java baseline.
- The full headless verification suite passes after the baseline decision is applied.

## Story 2: Lock the Replay Contract in the Demo App

### Why this is next

The last completed work tightened replay nondeterminism, but the app still needs a firm product decision on how strict replay should be when tape loading or request matching fails.

### Tasks

- [ ] Decide whether demo-app replay should keep `missingTapePolicy = PASSTHROUGH` or fail fast for developer reproduction flows.
- [ ] Decide whether replay misses should surface only as exceptions/logs or also as explicit user-facing diagnostics.
- [ ] Add focused tests for the chosen replay-failure behavior in import and playback flows.

### Acceptance Criteria

- Replay behavior on missing or invalid tapes is deliberate, documented, and tested.
- Developers can tell whether the app is replaying recorded traffic or silently falling back to live traffic.
- Tape-load diagnostics are easy to find and share when replay setup fails.

## Story 3: Finish QA and Developer Tape UX

### Why this matters

The app already imports, exports, and shares tape artifacts, but this area has less explicit completion evidence than the replay core and adapter modules.

### Tasks

- [ ] Validate the tape export flow end-to-end, including naming and saved file expectations.
- [ ] Validate the tape import flow for valid tapes, malformed tapes, and tapes with no replayable HTTP responses.
- [ ] Decide whether `selected_tape.ndjson` lifecycle behavior is sufficient or needs a clearer user-visible status.
- [ ] Add tests or manual verification notes for artifact sharing, import failures, and diagnostics attachment behavior.

### Acceptance Criteria

- QA can export a tape and a developer can import it with predictable results.
- Import failures produce actionable feedback instead of ambiguous toasts.
- The artifact flow is covered either by automated tests or by explicit documented manual verification steps.

## Story 4: Decide the Legacy `:sessionkit` End State

### Why this is a center

The migration goal is met architecturally, but the repo still carries the deprecated compatibility module.

### Tasks

- [ ] Decide whether `:sessionkit` stays for one compatibility cycle or is removed in the next focused cleanup.
- [ ] If it stays, freeze it explicitly and avoid adding new behavior there.
- [ ] If it is removed, plan the deletion with docs updates and test coverage replacement where needed.

### Acceptance Criteria

- The repo has a clear policy for the legacy module.
- Documentation and implementation agree on whether `:sessionkit` is transitional or supported.
- No new primary-path work depends on `:sessionkit`.

## Story 5: Release Readiness

### Why this is later

The modules are publishable in structure, but release confidence depends on the earlier centers being stable first.

### Tasks

- [ ] Validate `publishToMavenLocal` for the new modules once the verification baseline is restored.
- [ ] Document the supported Java, Kotlin, Android, OkHttp, Retrofit, and Ktor baselines in one place.
- [ ] Decide what qualifies as `0.1.0` versus continued snapshot-only iteration.

### Acceptance Criteria

- A consumer can install the published modules locally and use the documented setup.
- Version and environment expectations are explicit.
- Release scope is small, testable, and documented.

## Recommended Order

- [ ] Story 1: Restore verification baseline.
- [ ] Story 2: Lock replay contract in the demo app.
- [ ] Story 3: Finish QA and developer tape UX.
- [ ] Story 4: Decide the legacy `:sessionkit` end state.
- [ ] Story 5: Complete release-readiness work.

## Decision Log

- `2026-03-17`: Reviewed local repo state to resume work without relying on prior chat context.
- `2026-03-17`: The latest repo-side implementation note is the replay nondeterminism follow-up in commit `dd6c403` from `2026-03-16`.
- `2026-03-17`: Confirmed that the architectural migration is already in place across `:cassete-core`, `:cassete-okhttp`, `:cassete-ktor`, and `:app`.
- `2026-03-17`: Local Gradle verification is currently blocked because the repo requests Java 11 compilation while this machine only exposes Java 17.
