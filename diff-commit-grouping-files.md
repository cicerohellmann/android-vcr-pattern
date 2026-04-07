# Diff Commit Grouping

## Goal

Review the current working tree diff and infer the smallest sensible set of commits grouped by intent.

## User Items

- [x] Review the diff.
- [x] Infer the smallest sensible set of commits.
- [x] Group changes by intent instead of by file list alone.
- [x] For each proposed commit, list the files or hunks that belong in it.
- [x] For each proposed commit, write a short commit message.
- [x] For each proposed commit, explain the purpose in one sentence.
- [x] Keep tests with the change they validate.
- [x] Say explicitly what should be split out when the diff is mixed badly.
- [x] Avoid PR comments, review comments, and one giant summary unless the diff is truly one concern.

## Stories

- [x] Story 1: Inspect the tracked diff and identify distinct intents.
- [x] Story 2: Detect mixed files and decide whether they should be split by hunk.
- [x] Story 3: Produce the smallest sensible commit plan with message and purpose for each commit.

## Acceptance Criteria

- The proposed commits are separated by concern, not just by touched files.
- Mixed files are split by hunk where the concerns are separable.
- Documentation stays with the behavior it describes only when it is directly tied to that behavior.
- The final output can be used as an actionable commit plan without extra interpretation.

## Decision Log

- `2026-03-23`: The tracked diff contains two concerns: Java 17 build-baseline changes and repository-positioning README edits.
- `2026-03-23`: `README.md` is mixed and should be split by hunk, with Java 17 setup text grouped with the build-script baseline change.
