# Cassete Library Readiness

## Goal

Drive the library modules to a green, repeatable verification baseline and prove they can be consumed by a host app through the supported adapter surfaces. Leave a durable tracker that shows what failed, what was fixed, and what evidence exists for library readiness.

## User Request Coverage

- [x] Test the library.
  Resolution: run the library and host-proof Gradle verification tasks, record failures, and keep the concrete command history in `Validation`.
- [x] Iterate until the library is working.
  Resolution: fix only the defects exposed by the verification path, then re-run the failing and dependent checks until they pass.
- [x] Prove the library is working.
  Resolution: require passing automated verification across the library modules and the sample host-app proof path before calling the work complete.
- [x] Make it usable in any app on the market.
  Resolution: validate the supported integration contract for real host apps through the published library modules, the adapter install paths, and a host-app consumption proof. Treat "any app" as "any app using the supported adapters/contracts" unless the user approves a broader product claim.
- [x] Keep progress in a durable `*_files.md` plan with checkboxes, stories, acceptance criteria, and log notes.
  Resolution: maintain this tracker as the source of truth for execution state, decisions, validation, and next steps.

## Scope Guardrails

- Stay on the current branch `sample-test`; do not create any branch with the prefix `codex/`.
- Do not touch unrelated untracked planning files unless the current task explicitly requires them.
- Do not make adjacent fixes, cleanup, or workflow changes outside the proof/readiness path.
- If a broader product path appears necessary beyond supported adapters and consumer proof, stop and validate that path with the user first.
- Prefer the smallest reversible change set that clears a proven blocker.

## Current Snapshot

- Active branch: `sample-test`
- Pre-existing dirty files:
  - `current-implementation-centers.md`
  - `diff-commit-grouping-files.md`
  - `standalone-library-direction.md`
  - `standalone-library-execution-plan.md`
- Primary product modules:
  - `:cassete-core`
  - `:cassete-okhttp`
  - `:cassete-ktor`
- Proof consumer modules:
  - `:app`
  - `:sessionkit` as deprecated compatibility coverage already present in the repo
- Documented verification path from `README.md`:
  - `./gradlew :cassete-core:compileKotlin`
  - `./gradlew :cassete-okhttp:test`
  - `./gradlew :cassete-ktor:test`
  - `./gradlew :app:testDebugUnitTest`
  - `./gradlew :sessionkit:testDebugUnitTest`
  - `./gradlew :app:assembleDebug`

## Story 1: Establish The Real Baseline

**User story**
As the maintainer of the Cassete library, I need a current verification baseline so we know whether the repository already proves the product or still has concrete breakages to fix.

### Acceptance Criteria

- [x] The requested proof path is translated into a concrete verification sequence.
  Implementation: use the documented Gradle tasks for the library modules and proof consumer path, adjusting only when a failure shows a tighter reproducer is needed. Record each command and outcome in `Validation`.
- [x] The initial failing surfaces are identified before code changes begin.
  Implementation: run the baseline suite, capture which modules or tasks fail, and note the first actionable blocker with file-level context before editing code.
- [x] The readiness claim is bounded to supported adapters instead of an unqualified universal compatibility claim.
  Implementation: keep the proof target tied to `:cassete-core`, `:cassete-okhttp`, `:cassete-ktor`, and the host-app harness. If a failure implies unsupported-client work, log it as out of current scope unless the user approves expansion.

## Story 2: Repair Verified Defects

**User story**
As a library consumer, I need the supported record/replay paths to behave correctly, so the library can be trusted in a real app instead of only compiling in isolation.

### Acceptance Criteria

- [x] Any code change would be tied to a reproduced failure from Story 1, or no code change is made when no library defect is reproduced.
  Implementation: edit only the files implicated by failing behavior. In this execution pass, no code edits were required because the proof path exposed only an environment-level SDK discovery issue and all supported library checks passed once the local SDK path was exported.
- [x] Broken behavior gains or tightens automated coverage only when a verified defect exists.
  Implementation: add or adjust focused tests in the affected module when the existing suite does not already lock the failure mode. No additional coverage was needed in this pass because no supported-library behavior failed under verification.
- [x] Any repaired slice passes targeted verification before moving on, or the no-fix outcome is explicitly recorded.
  Implementation: rerun the smallest relevant Gradle task after each fix, then expand outward to dependent module checks when green. Here, the no-fix outcome is the validated result.

## Story 3: Prove Host-App Usability

**User story**
As a host-app developer, I need evidence that the published Cassete modules can be installed and used through their public contracts, so I can trust the library outside this repository's internal source dependencies.

### Acceptance Criteria

- [x] The publishable modules build and publish locally.
  Implementation: run `publishToMavenLocal` for `:cassete-core`, `:cassete-okhttp`, and `:cassete-ktor`, then record the result in `Validation`.
- [x] A consumer-oriented proof path uses only the supported public integration surfaces.
  Implementation: validate the existing sample host-app path and/or another local consumer proof that depends on the published artifacts or public APIs without reaching into internal implementation details.
- [x] The final readiness statement is backed by explicit evidence and bounded assumptions.
  Implementation: summarize what is proven, what environments/adapters were verified, and what remains outside proof scope in the decisions log and final report.

## Important Decisions Log

- [x] 2026-03-26: Interpreting "any app on the market" as "any host app using the supported Cassete adapters and public contracts" for this execution pass. Expanding that to unsupported HTTP clients requires explicit user approval because it broadens the product claim.
- [x] 2026-03-26: The existing standalone planning markdown files are treated as context only; this tracker is the durable execution record for the current proof/readiness request.
- [x] 2026-03-26: The first full-suite blocker was Android SDK discovery, not a library defect. This checkout succeeds by exporting `ANDROID_HOME` and `ANDROID_SDK_ROOT` to `/Users/cicerohellmann/Library/Android/sdk` without modifying repo configuration.
- [x] 2026-03-26: No supported-library defect reproduced in this pass. The current repository state is already green for the documented library tests, Android host-app proof path, local publication, and an external standalone consumer smoke test using `mavenLocal()`.

## Validation

- [x] Run the baseline verification suite.
  Result: `./gradlew :cassete-core:test :cassete-okhttp:test :cassete-ktor:test :app:testDebugUnitTest :sessionkit:testDebugUnitTest :app:assembleDebug` failed first on 2026-03-26 because `:app:testDebugUnitTest` could not find an Android SDK.
- [x] Re-establish the library-only baseline.
  Result: `./gradlew :cassete-core:test :cassete-okhttp:test :cassete-ktor:test` passed on 2026-03-26.
- [x] Re-run the Android proof path with the locally installed SDK exported.
  Result: `ANDROID_HOME=/Users/cicerohellmann/Library/Android/sdk ANDROID_SDK_ROOT=/Users/cicerohellmann/Library/Android/sdk ./gradlew :app:testDebugUnitTest :sessionkit:testDebugUnitTest :app:assembleDebug` passed on 2026-03-26.
- [x] Record the no-fix repair outcome.
  Result: no library defect required a code change in this pass, so no targeted repair-only verification slice was necessary.
- [x] Run the final library proof suite.
  Result: the combination of the green library-module tests, Android proof path, local publication, and external consumer smoke test constitutes the final proof set for 2026-03-26.
- [x] Run `publishToMavenLocal` for the product modules.
  Result: `./gradlew :cassete-core:publishToMavenLocal :cassete-okhttp:publishToMavenLocal :cassete-ktor:publishToMavenLocal` passed on 2026-03-26.
- [x] Verify external consumer installation from published artifacts.
  Result: `./gradlew -p /tmp/cassete-consumer-proof.Ma9aTK test` passed on 2026-03-26 using only `mavenLocal()` dependencies on `cassete-core`, `cassete-okhttp`, and `cassete-ktor`. The temporary proof project was removed after the successful run because it was created only for this verification slice.

## Commit Plan

- [ ] Keep commits scoped to repaired acceptance-criteria slices if the worktree state makes that safe.
- [ ] Do not mix this task with the pre-existing untracked planning documents.
- [x] Leave source untouched when the proof path does not justify a product-code change.
