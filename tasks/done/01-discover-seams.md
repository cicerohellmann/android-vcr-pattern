# Task 01: Discover Current Seams

## Summary
Inventory the current app’s interaction points to prepare for event-boundary refactor.

## Tasks
- [x] Trace how Compose UI invokes logic: identify any ViewModels, direct suspend calls, and state holders per screen.
- [x] List all side-effect locations (network, disk, sharing, file ops) and where they are triggered.
- [x] Catalog nondeterministic inputs: time, random/UUID, deep links, sensors, connectivity.
- [x] Document how network is wired (OkHttp/Retrofit) and where interceptors can be inserted.
- [x] Identify the current single source of truth for screen state (StateFlow/MutableStateFlow) or lack thereof.

## Acceptance Criteria
- Written notes enumerating UI→logic call paths, side-effect sites, and nondeterministic sources.
- Clear mapping of where to hook a UiEvent sink and where to inject replayable dependencies.
- Risks/unknowns called out (e.g., irreversible side effects in ViewModels).

---

## Seams & Flows (current app)
- **UI → logic**: `ApiTestScreen` in `MainActivity.kt` owns local `UiState` and dispatches button clicks directly into Activity suspend funcs (`fetchRandomPokemon`, `fetchFromApi`, `fetchData`, `postData`). No ViewModels; work launched via `lifecycleScope`.
- **State sources**: Mode is the only shared observable (`VcrApp.currentMode : StateFlow`). UI screen state is `remember { mutableStateOf(UiState) }`, so no single source beyond the Composable.
- **Network entry**: All HTTP calls go through `VcrApp.okHttp`, configured by `EnvConfig.resolve` + `HttpClients.client`. Replay overrides use `MainActivity.loadTapeFromUri` → `app.updateHttpClientWithTape`.
- **Mode switching**: Mode toggle calls `app.switchMode`, rebuilding OkHttp with Recording or Replayer interceptors based on `AppConfig.mode`.

## Side-Effect Sites (hook targets)
| Area | Location | Notes for UiEvent/replay hooks |
| --- | --- | --- |
| HTTP traffic | `fetchRandomPokemon` / `fetchFromApi` / `fetchData` / `postData` in `MainActivity.kt` | Single funnel for outbound requests; already uses `app.okHttp` so interceptor-based capturing works here. |
| Recording writes | `SessionRecorder.log` → `sessions/events.ndjson` | Appends on single-thread executor; non-idempotent file IO. |
| Tape load | `MainActivity.loadTapeFromUri` | Copies picked file to `selected_tape.ndjson`, builds `ReplayerInterceptor`, swaps client. |
| Mode client wiring | `HttpClients.client` | RECORD adds `RecordingInterceptor`; REPLAY builds `ReplayerInterceptor` with URL patterns; PASSTHROUGH empty. |
| Tape save/share | `shareSessionLog`, `downloadSessionLog` | FileProvider share intent and MediaStore/external copy (API level dependent). |
| File mutation | `clearBuffer`, `deleteSessionFile` | Recreates recorder/client; deletes/creates `sessions/events.ndjson`. |
| UX feedback | Toasts across Activity methods | Side-effectful but safe; would want UiEvent sink if centralizing UX events. |

## Nondeterministic Inputs
- Random IDs: `app.random.nextInt` in `fetchRandomPokemon` and `JsonPlaceholder` fallback IDs; also random when `ApiType.Pokemon`/`JsonPlaceholder` omit an id.
- Time: `SessionRecorder` and `TapeLoader.save` use `System.currentTimeMillis`; file names for downloads use `System.currentTimeMillis`; request timing uses `System.nanoTime`.
- UUIDs: `RecordingInterceptor` generates `UUID.randomUUID()` per request.
- Device/env: Session start logs `android.os.Build.MODEL`; file picker URIs and external storage availability vary by device/OS.

## Network Wiring & Interceptors
- RECORD: `HttpClients.client` adds `RecordingInterceptor(recorder) { config.mode }` as outermost interceptor; writes `REQUEST`/`RESPONSE` events with optional body SHA.
- REPLAY: `HttpClients.client` builds `ReplayerInterceptor(logger) { config.mode }`, loads tape via `TapeLoader.loadLatestSession` with URL patterns for Pokemon, GitHub, JSONPlaceholder. Latency simulated via `Thread.sleep(durationMs)`.
- PASSTHROUGH: bare `OkHttpClient`.
- Runtime swap: `updateHttpClientWithTape` installs a provided `ReplayerInterceptor` (used after file pick) without recorder; `switchMode` rebuilds client based on latest `config.mode`.

## UiEvent & Injection Opportunities
- **UiEvent sink candidates**: Button handlers in `ApiTestScreen` (`Pick Random Pokémon`, GitHub/JSONPlaceholder buttons, Share/Download/Clear/Delete/Load Tape). Each currently triggers coroutine + toast inline; a sink here would centralize action logging before calling Activity funcs.
- **Replayable deps to inject**: Random generator (`VcrApp.random`), clock (`System.currentTimeMillis`/`System.nanoTime`), UUID source in `RecordingInterceptor`, and the `OkHttpClient` builder in `HttpClients`. These can be parameterized for deterministic tests.
- **State store seam**: Replace `mutableStateOf(UiState)` with a ViewModel + `StateFlow` to create a single observable source for UI state and inject a UiEvent processor.

## Risks / Unknowns
- Activity-scoped coroutines tie work to `MainActivity`; any future navigation/screens would need shared scope or ViewModel to avoid leaks/duplication.
- File picker copy and MediaStore write can fail silently (permission/storage errors) and currently only surface via Toast; no retry/telemetry.
- `updateHttpClientWithTape` drops recording capability until `clearBuffer`/`switchMode` rebuilds the client; easy to forget when toggling modes.
- Replay failures (`NoTapeFoundException`) in REPLAY mode throw; callers do not catch, so UI could crash if tape missing or URL pattern mismatch.
