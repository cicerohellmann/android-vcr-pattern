# Project Task Completion Audit

## Goal

Verify the task trackers that already exist in this repository against the current codebase, build graph, tests, and docs, then update the recorded completion levels where the repo state supports a different status.

## User Request Coverage

- [x] Verify the tasks created in this project.
  Resolution: audit the task sources already present in the repo, centered on `tasks/backlog/*.md`, `tasks/done/*.md`, and `USER_STORIES.md`, then compare them against source, build configuration, and validation commands.
- [x] Update the level of completion.
  Resolution: correct stale or incomplete completion markers only after repo evidence is collected, and record the evidence plus decisions in this tracker.
- [x] Keep progress in a durable `*_files.md` tracker.
  Resolution: use this file as the resumable execution record for the audit, decisions, validation, and remaining open bookkeeping.

## Scope Guardrails

- Stay on the current branch `sample-test`; do not create any branch with the prefix `codex/`.
- Do not change product code or build wiring unless the audit exposes a task-status error that can only be resolved by user-approved code work.
- Do not make adjacent fixes, cleanup, or planning changes outside the task-completion audit slice.
- Treat other planning documents as context unless they must be updated because they present stale completion state that conflicts with the verified repo state.
- Prefer the smallest reversible doc edits needed to align task completion with the current repository evidence.

## Current Snapshot

- Active branch: `sample-test`
- Upstream: `origin/sample-test`
- Worktree state when audit began: clean
- Current audit edits:
  - `USER_STORIES.md`
  - `tasks/backlog/04-recording-schema.md`
  - `tasks/backlog/05-sessionkit-module.md`
  - `tasks/backlog/06-player-controls.md`
  - `tasks/backlog/07-replay-gating.md`
  - `tasks/backlog/08-qa-dev-ux.md`
  - `tasks/backlog/09-testing.md`
  - `task-completion-audit_files.md`
- Primary task sources under audit:
  - `USER_STORIES.md`
  - `tasks/backlog/04-recording-schema.md`
  - `tasks/backlog/05-sessionkit-module.md`
  - `tasks/backlog/06-player-controls.md`
  - `tasks/backlog/07-replay-gating.md`
  - `tasks/backlog/08-qa-dev-ux.md`
  - `tasks/backlog/09-testing.md`
- Supporting evidence sources already checked:
  - `README.md`
  - `RETROFIT_SAMPLE.md`
  - `.github/workflows/ci.yml`
  - `settings.gradle.kts`
  - `build.gradle.kts`
  - `cassete-core/build.gradle.kts`
  - `cassete-okhttp/build.gradle.kts`
  - `cassete-ktor/build.gradle.kts`
  - `app/build.gradle.kts`
- Verified mismatch to resolve in task docs:
  - `sessionkit/` still exists on disk, but `settings.gradle.kts` no longer includes `:sessionkit`, and `./gradlew :sessionkit:testDebugUnitTest` now fails with `project 'sessionkit' not found`.

## Story 1: Establish The Live Task Baseline

**User story**
As the maintainer of this repo, I want the task audit grounded in the current repository state, so completion updates reflect what actually exists now instead of older planning assumptions.

### Acceptance Criteria

- [x] The active task sources are identified before any completion markers are changed.
  Implementation: use the existing task markdown files and durable trackers as the audit inputs; avoid inventing a new task system.
- [x] The current build graph is verified instead of assumed from older docs.
  Implementation: run `./gradlew projects` and targeted Gradle commands, then record any mismatch that affects task completion bookkeeping.
- [x] The audit scope is bounded to completion tracking, not product changes.
  Implementation: update only the task status documents unless the user explicitly expands scope beyond auditing.

## Story 2: Compare Task Claims Against Repository Evidence

**User story**
As a collaborator resuming this project, I want each task’s completion state to match code, tests, and docs, so I can trust the backlog and done lists.

### Acceptance Criteria

- [x] Each audited task is checked against concrete repository evidence.
  Implementation: review the relevant source, tests, build files, and docs for every item under `USER_STORIES.md` and `tasks/backlog`.
- [x] False positives and stale incompletions are identified explicitly.
  Implementation: note where tasks are marked done without current evidence, and where implemented work remains marked incomplete.
- [x] The final set of task-doc edits is driven by verified evidence only.
  Implementation: do not mark any item complete just because an older tracker claimed it; require the live repo state to support it.

## Story 3: Update Task Completion Records

**User story**
As the next agent working in this repository, I want the task docs to reflect the verified state after this audit, so planning can continue from accurate completion levels.

### Acceptance Criteria

- [x] The affected task markdown files are updated with corrected completion levels.
  Implementation: edit only the specific task docs whose status is stale or missing relative to the verified evidence.
- [x] The audit leaves a durable explanation of why the completion levels changed.
  Implementation: add dated decisions here summarizing the evidence behind the status corrections.
- [x] Validation evidence is recorded for the updated completion states.
  Implementation: keep the exact commands and outcomes in `Validation`, including the `:sessionkit` project-not-found result.

## Important Decisions Log

- [x] 2026-04-15: This audit treats `USER_STORIES.md` plus `tasks/backlog/*.md` and `tasks/done/*.md` as the primary task system, with the other planning markdown files used only as supporting evidence when they help prove or disprove completion.
- [x] 2026-04-15: The current Gradle build graph is the authority for module-completion claims. Because `settings.gradle.kts` excludes `:sessionkit`, any task or tracker that still treats `:sessionkit` as an active verified module is stale and must be corrected.
- [x] 2026-04-15: Updated the primary task records only. I did not change `README.md`, CI wiring, or the broader historical planning markdown files because the user asked for completion verification, not adjacent cleanup.
- [x] 2026-04-15: Marked `tasks/backlog/05-sessionkit-module.md` as superseded because the legacy source still exists but the live root project no longer includes `:sessionkit`.
- [x] 2026-04-15: Marked `tasks/backlog/09-testing.md` as partial because local verification passes, but the checked-in GitHub Actions workflow still targets the removed `:sessionkit` project and is therefore stale.

## Validation

- [x] Run `./gradlew projects`.
  Result: passed on 2026-04-15 and listed only `:app`, `:cassete-core`, `:cassete-okhttp`, and `:cassete-ktor`.
- [x] Run `./gradlew :cassete-core:test :cassete-okhttp:test :cassete-ktor:test :app:testDebugUnitTest :app:assembleDebug`.
  Result: passed on 2026-04-15.
- [x] Run `./gradlew :sessionkit:testDebugUnitTest`.
  Result: failed on 2026-04-15 with `project 'sessionkit' not found in root project 'cassete'`.

## Commit Plan

- [ ] Keep any task-status edits scoped to the audit files if the worktree remains safe for a clean commit.
- [ ] Do not mix this audit with unrelated product or planning changes.
