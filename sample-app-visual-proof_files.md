# Sample App Visual Proof

## Goal

Use the sample app as the visual proof harness for Cassete by copying the deterministic screenshot-plus-GIF pattern from `shards-of-manta-tactics`, adapted to this Android app so a feature can be proven through named static and motion artifacts.

## User Request Coverage

- [x] Check whether the sample app can do the whole testing.
  Resolution: use the sample app as the end-to-end and visual proof harness for the supported integration path, while keeping lower-level module tests as the non-visual correctness guard underneath it.
- [x] Inspect `shards-of-manta-tactics`.
  Resolution: read the local repo's screenshot-testing docs, Gradle wiring, and visual-contract test source to copy its practical pattern instead of inventing a new one.
- [x] Copy the way screenshot testing is being used there.
  Resolution: adopt the same core contract here: deterministic named scenarios, stable artifact paths, static PNG checkpoints, and GIF-style motion proof generated from explicit frame sequences.
- [x] Use GIFs and static images to prove a feature works.
  Resolution: add one deterministic sample-app scenario that emits both still PNGs and a motion sequence for the record-to-replay flow.
- [x] Inspect the generated screenshots and fix misleading artifacts instead of leaving cleanup to manual review.
  Resolution: inspect the actual exported screenshots, remove transient toast and transition contamination from the capture path, and regenerate clean reviewable record/replay proof images.
- [x] Keep progress in a durable `*_files.md` tracker with stories, acceptance criteria, and validation.
  Resolution: maintain this tracker as the execution record for the visual-proof slice.

## Scope Guardrails

- Stay on the current branch `sample-test`; do not create branches with the prefix `codex/`.
- Keep this slice focused on visual-proof automation for the sample app.
- Reuse the existing deterministic sample-app network seam instead of broadening into new feature work.
- Do not claim the sample app replaces module tests for all correctness; use it as the integration and visual contract harness.
- Leave the pre-existing unrelated planning files alone.

## Current Snapshot

- Active branch: `sample-test`
- Existing sample-app proof already covers:
  - device-side instrumented end-to-end record/replay tests
  - app JVM regression tests
- Current repo has no screenshot or GIF proof workflow yet.
- `shards-of-manta-tactics` currently uses:
  - named deterministic scenario tests
  - stable artifact output folders under `build/reports`
  - direct PNG writing
  - GIF generation from explicit frame lists
  - publish-style asset bundles that explain the proof artifacts

## Story 1: Map The Reference Pattern To The Sample App

**User story**
As the maintainer, I need the `shards-of-manta-tactics` visual-proof pattern translated into this repo's constraints, so the sample app gains the same kind of inspectable proof artifacts without pretending the platforms are identical.

### Acceptance Criteria

- [x] The reusable parts of the reference workflow are identified.
  Implementation: carry over deterministic scenario naming, stable output paths, still-plus-motion artifact pairing, and feature-proof framing from the reference repo.
- [x] The Android-specific constraints are handled explicitly.
  Implementation: adapt around the fact that this app runs on Android instead of desktop Compose, including how screenshots are captured, where device-side artifacts are written, and how GIFs are packaged on the host.

## Story 2: Emit Deterministic Visual Proof Artifacts

**User story**
As a reviewer, I need a small visual artifact set from the sample app's record/replay flow, so I can verify the feature from images and a motion proof without manually rerunning the whole app.

### Acceptance Criteria

- [x] The sample app emits named static PNG checkpoints for the chosen scenario.
  Implementation: capture deterministic sample-app screenshots for the GitHub record/replay flow and write them to a stable output location.
- [x] The same scenario emits a deterministic frame sequence that can be packaged into a GIF.
  Implementation: capture explicit frame states for the same flow instead of relying on ad hoc screen recording.
- [x] The artifact names and directory layout are readable on their own.
  Implementation: use scenario-based names and a stable reports folder similar in spirit to the reference repo.

## Story 3: Package The Proof Like A Reviewable Bundle

**User story**
As the person reviewing completion, I need the sample app's PNGs and GIFs pulled into a single build-side location, so the artifacts are easy to inspect and share after a test run.

### Acceptance Criteria

- [x] The instrumented proof run writes artifacts that survive the device test pass.
  Implementation: choose a device-side storage location and write the artifacts there from the test harness.
- [x] A host-side step gathers the raw artifacts into `build/reports`.
  Implementation: add a small Gradle-managed pull/package step that copies the sample-app visual proof back into the repo build outputs.
- [x] GIF packaging is automated from the captured frame sequence.
  Implementation: reuse the same explicit-frame GIF idea as the reference repo, adapted to the host-side packaging step here.

## Important Decisions Log

- [x] 2026-03-26: The right adaptation is conceptual, not literal. This repo should copy the visual-contract pattern from `shards-of-manta-tactics`, but the Android sample app needs Android-specific capture and packaging mechanics.
- [x] 2026-03-26: The first visual-proof slice should stay narrow and prove one deterministic GitHub record-to-replay flow instead of attempting every sample-app feature at once.
- [x] 2026-03-26: Use the sample app as the visual and integration proof harness, but not as a replacement for module-level correctness tests. The sample app now proves the supported record/replay path visually on top of the lower-level tests.
- [x] 2026-03-26: The closest practical adaptation of the reference repo is: device-side instrumented scenario capture to public Downloads, followed by a host-side Gradle pull/package step that writes the final bundle under `app/build/reports/visual-proof/`.
- [x] 2026-03-26: The sample app now mirrors the reference repo's artifact contract with named static checkpoints plus a GIF built from explicit frame PNGs rather than from hand-recorded video.
- [x] 2026-03-26: The first generated artifacts were not reviewable because the visual-proof task ran the whole instrumented suite and the screenshots captured transient toasts plus mid-transition dialog states.
- [x] 2026-03-26: Fixed the artifact quality by isolating `sampleAppVisualProof` to `CasseteVisualProofInstrumentedTest`, suppressing toasts during visual capture, waiting for dialog transitions to settle, and relaunching the activity before the replay-home checkpoint so that frame represents a clean replay-ready state.

## Validation

- [x] Inspect the relevant `shards-of-manta-tactics` docs and source.
  Result: reviewed `docs/SHARDS_OF_MANTA_TACTICS_SCREENSHOT_TESTING_files.md`, `docs/SHARDS_OF_MANTA_TACTICS_GIF_PROOF_WORKFLOW_files.md`, `sample/shared/build.gradle.kts`, and `sample/shared/src/desktopTest/kotlin/com/cicerohellmann/pika/sample/games/tacticfighter/TacticFighterVisualContractScreenshotTest.kt` on 2026-03-26 to copy the deterministic scenario, stable-path, static-plus-GIF pattern.
- [x] Run the sample-app visual proof path and confirm PNG and GIF outputs exist.
  Result: `ANDROID_HOME=/Users/cicerohellmann/Library/Android/sdk ANDROID_SDK_ROOT=/Users/cicerohellmann/Library/Android/sdk ./gradlew :app:sampleAppVisualProof` passed on 2026-03-26 after isolating the visual-proof class and cleaning the capture timing. The final bundle exists under `app/build/reports/visual-proof/`, with assets:
  - `app/build/reports/visual-proof/assets/sample_app_record_home.png`
  - `app/build/reports/visual-proof/assets/sample_app_record_response.png`
  - `app/build/reports/visual-proof/assets/sample_app_replay_home.png`
  - `app/build/reports/visual-proof/assets/sample_app_replay_response.png`
  - `app/build/reports/visual-proof/assets/github_record_to_replay.gif`
- [x] Re-run the existing sample-app device/unit suites after the visual-proof changes.
  Result: `ANDROID_HOME=/Users/cicerohellmann/Library/Android/sdk ANDROID_SDK_ROOT=/Users/cicerohellmann/Library/Android/sdk ./gradlew :app:testDebugUnitTest` passed on 2026-03-26, and `ANDROID_HOME=/Users/cicerohellmann/Library/Android/sdk ANDROID_SDK_ROOT=/Users/cicerohellmann/Library/Android/sdk ./gradlew :app:connectedDebugAndroidTest` also passed on 2026-03-26 on the attached `SM-G998B` after the screenshot-quality fixes.
- [x] Inspect the final regenerated screenshots directly.
  Result: reviewed `sample_app_record_home.png`, `sample_app_record_response.png`, `sample_app_replay_home.png`, and `sample_app_replay_response.png` on 2026-03-26 and confirmed the artifacts now show coherent RECORD and REPLAY states without stale toasts or mid-transition overlays.

## Commit Plan

- [ ] Keep commits scoped to the sample-app visual proof slice if the worktree state makes that safe.
- [ ] Do not mix this slice with the unrelated planning markdown files already present in the worktree.
