# Changelog

All notable changes to this project are documented here.

## Unreleased (2026-03-16)

### Added
- Replay-safe request lifecycle tracking in `ApiTestViewModel` using per-request IDs and completion metadata (`activeRequestId`, `lastCompletedRequestId`, `lastUpdatedAtMs`).
- Deterministic nondeterminism replay for full fetch flows:
  - `ND_UUID` is consumed for request IDs during replay.
  - `ND_TIME` is consumed for completion timestamps and session-log export naming during replay.
- App-level replay test coverage to assert replay does not call live clock/UUID providers and reuses recorded nondeterminism values.

### Changed
- Replay state reset now clears request lifecycle metadata to avoid stale values after rewind/step-back.
- Task docs updated to mark `ND_TIME`/`ND_UUID` replay consumption as implemented and covered by tests.

## 2026-03-08

### Added
- Replay nondeterminism coverage and CI (`3c68597`).
- `cassete-core` extraction and adapter modularization (`3ba848f`).
- Cassete migration plan documentation (`cbecd24`).

## 2026-03-06

### Changed
- Renamed `GOAL` file to Markdown format (`1f78c85`).
- Renamed project to Cassete (`7df2e91`).
