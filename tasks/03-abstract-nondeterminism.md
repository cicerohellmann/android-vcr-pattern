# Task 03: Abstract Nondeterminism

## Summary
Make time/random/UUID/network inputs injectable so they can be recorded and replayed deterministically.

## Tasks
- [x] Define interfaces for clock, random/UUID generator, and network client/provider.
- [x] Provide live implementations and replay/test doubles.
- [x] Inject these dependencies into ViewModel instead of using globals/system calls.
- [x] Map each nondeterministic output to a recordable payload shape (documented).
- [ ] (Optional / follow-up) Emit nondeterminism events during RECORD and consume them during REPLAY.

## Notes (implemented)

- Ports added in `com.hellmannratti.vcr.replay.NondeterminismPorts`:
  - `Clock` (`SystemClock`, `FixedClock`)
  - `IdGenerator` (`UuidGenerator`, `SequenceIdGenerator`)
  - `RandomProvider` (`KotlinRandomProvider`, `SequenceRandomProvider`)
- Network port added in `com.hellmannratti.vcr.replay.NetworkPort`:
  - `NetworkClient` + `NetworkResponse`
  - OkHttp adapter: `com.hellmannratti.vcr.framework.OkHttpNetworkClient`
- Screen logic updated:
  - `ApiTestViewModel` receives `Clock`, `RandomProvider`, `NetworkClient` via constructor/factory and no longer calls `Random.Default` or direct `OkHttpClient.newCall()`.
- Recorder/interceptor updated:
  - `SessionRecorder` uses injected `Clock` for `SESSION_START` timestamps.
  - `RecordingInterceptor` uses injected `Clock` for timestamps/durations and injected `IdGenerator` for `requestId`.
  - `TapeLoader.save(...)` uses injected `Clock` + `IdGenerator`.
- Nondeterminism payload shapes documented in `Event.kt` as stable `ActionEvent` schemas:
  - `ND_TIME` -> `{ nowMs: Long }`
  - `ND_UUID` -> `{ value: String }`
  - `ND_RANDOM_INT` -> `{ from: Int, until: Int, value: Int }`

## Acceptance Criteria
- All uses of time/random/UUID/network in the screen logic route through interfaces.
- Live vs replay implementations can be swapped via DI/constructor without code changes.
- Recorded payload shapes are documented for the recorder.
