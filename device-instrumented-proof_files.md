# Device Instrumented Proof

## Goal

Add deterministic end-to-end Android instrumented coverage that runs on a real device or emulator and proves the app-owned Cassete integration records and replays through the actual UI and app wiring.

## User Request Coverage

- [x] Test on a device.
  Resolution: run the instrumented suite against the attached Android device instead of stopping at JVM-only verification.
- [x] Automate practical tests.
  Resolution: convert the proof path into repeatable `androidTest` coverage that exercises the real app UI, network wiring, and tape file behavior.
- [x] Add end-to-end instrumented tests.
  Resolution: implement focused device-side tests that drive user actions through `MainActivity`, record traffic, switch to replay, and verify the app reuses recorded responses deterministically.
- [x] Keep progress in a durable `*_files.md` tracker with stories, acceptance criteria, and validation.
  Resolution: maintain this tracker as the execution record for the device/instrumented slice.

## Scope Guardrails

- Stay on the current branch `sample-test`; do not create branches with the prefix `codex/`.
- Limit code changes to the minimum needed for deterministic instrumented proof.
- Do not broaden into unrelated app UX or library refactors.
- Avoid live-internet assertions in instrumented tests; use deterministic local test infrastructure instead.
- Leave the pre-existing untracked planning files alone.

## Current Snapshot

- Active branch: `sample-test`
- Existing dirty files before this slice:
  - `cassete-library-readiness_files.md`
  - `current-implementation-centers.md`
  - `diff-commit-grouping-files.md`
  - `standalone-library-direction.md`
  - `standalone-library-execution-plan.md`
- Current instrumented coverage is only `app/src/androidTest/java/com/hellmannratti/vcr/ExampleInstrumentedTest.kt`.
- A real device is attached over `adb`:
  - `SM_G998B` with serial `R3CRA020H1X`
- The app currently hardcodes live API endpoints inside `ApiTestViewModel`, so deterministic on-device end-to-end tests need a controlled network override seam.

## Story 1: Establish The Device Proof Baseline

**User story**
As a maintainer, I need to know whether the repository can currently run meaningful device-side tests, so I can distinguish missing infrastructure from product failures.

### Acceptance Criteria

- [x] Device availability and runner baseline are verified.
  Implementation: confirm `adb` connectivity, the target package, and the current `androidTest` setup before adding new tests. Record the commands and outcomes in `Validation`.
- [x] The missing deterministic seam is identified before changing code.
  Implementation: inspect the app wiring to confirm where live endpoints enter the flow and note the smallest override point needed for stable device tests.

## Story 2: Add A Deterministic Test Seam

**User story**
As an instrumented-test author, I need the app to target a local deterministic backend during tests, so record/replay flows can be proven without flaky public internet dependencies.

### Acceptance Criteria

- [x] The app can receive test-only endpoint overrides without changing the production path.
  Implementation: add the narrowest override mechanism that leaves the default production behavior unchanged while allowing instrumented tests to point the app to a local `MockWebServer`.
- [x] Cassete URL normalization still matches the overridden endpoints.
  Implementation: keep the configured URL patterns aligned with the effective endpoints used during testing so replay matching remains representative.
- [x] The seam is documented in code or test setup where another agent can reuse it.
  Implementation: keep the test-only contract obvious from the modified files and update this tracker once the final shape is chosen.

## Story 3: Automate End-To-End Record/Replay Proof

**User story**
As a host-app developer evaluating Cassete, I need a device-side test that proves the app records a real UI-driven request and then replays it after the backend disappears, so I know the integration works beyond unit tests.

### Acceptance Criteria

- [x] Instrumented tests drive the real UI through a record-then-replay flow.
  Implementation: use Compose or activity test APIs to launch `MainActivity`, trigger a deterministic API action, switch modes, and re-trigger the action through the on-device UI.
- [x] The replay leg proves recorded data is used instead of the live backend.
  Implementation: shut down or otherwise remove the local backend after recording, then assert the replayed UI still renders the recorded response and that the tape file exists.
- [x] The suite runs successfully on the attached device.
  Implementation: execute the targeted connected test task with the local SDK path exported, then record the exact command and result in `Validation`.

## Important Decisions Log

- [x] 2026-03-26: The attached physical device is available, so this slice will target a real device first instead of waiting for emulator setup.
- [x] 2026-03-26: Live public APIs are not an acceptable basis for practical end-to-end automation; a deterministic local backend seam is required.
- [x] 2026-03-26: The smallest stable seam is an app-level endpoint override plus `VcrApp.reinitializeForTesting()`, which lets instrumented tests swap deterministic URLs before launching `MainActivity` without changing the default production path.
- [x] 2026-03-26: The connected tests use a local cleartext `MockWebServer`, so the debug manifest now explicitly allows cleartext traffic for the debug/test build variant only.
- [x] 2026-03-26: Adding `apiEndpoints` to `VcrApp` required updating the existing `ApiTestViewModelReplayTest` helper so the app fixture still initializes correctly in JVM tests.

## Validation

- [x] Confirm attached-device visibility through `adb`.
  Result: `ANDROID_HOME=/Users/cicerohellmann/Library/Android/sdk ANDROID_SDK_ROOT=/Users/cicerohellmann/Library/Android/sdk adb devices -l` showed `SM_G998B` (`R3CRA020H1X`) on 2026-03-26.
- [x] Inspect the current instrumented baseline and app wiring.
  Result: confirmed the repo only had `ExampleInstrumentedTest.kt`, and `ApiTestViewModel` was still hardcoding live public API URLs before this slice.
- [x] Run the targeted instrumented suite on device.
  Result: `ANDROID_HOME=/Users/cicerohellmann/Library/Android/sdk ANDROID_SDK_ROOT=/Users/cicerohellmann/Library/Android/sdk ./gradlew :app:connectedDebugAndroidTest` passed on 2026-03-26. The connected result XML at `app/build/outputs/androidTest-results/connected/debug/TEST-SM-G998B - 14-_app-.xml` reports 3 passing tests, including the new `githubFlowRecordsResponseIntoTapeOnDevice` and `githubFlowReplaysRecordedResponseAfterBackendShutdown`.
- [x] Re-run the app JVM regression suite after the seam changes.
  Result: `ANDROID_HOME=/Users/cicerohellmann/Library/Android/sdk ANDROID_SDK_ROOT=/Users/cicerohellmann/Library/Android/sdk ./gradlew :app:testDebugUnitTest` passed on 2026-03-26 after updating the `ApiTestViewModelReplayTest` app fixture for the new `apiEndpoints` field.

## Commit Plan

- [ ] Keep commits scoped to the test seam and instrumented proof slice if the worktree state makes that safe.
- [ ] Do not mix this slice with the unrelated planning markdown files already present in the worktree.
