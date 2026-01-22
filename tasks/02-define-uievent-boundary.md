# Task 02: Define UiEvent Boundary

## Summary
Establish a single UiEvent/UserAction entry point per screen and refactor UI to dispatch typed events only.

## Tasks
- [x] Design a sealed `UiEvent` for the main screen covering current actions and data changes.
- [x] Add a ViewModel with `onEvent(event: UiEvent)` and state as `StateFlow`.
- [x] Refactor Compose to dispatch `UiEvent`s (no direct suspend calls or state mutation in UI).
- [x] Ensure state updates flow only from the ViewModel.

## Notes (implemented)
- Added `ApiTestViewModel` with `StateFlow<ApiUiState>` as the single source of truth plus `UiEvent`/`UiEffect` sealed types for all user actions (fetches, mode toggle, share/download, buffer/file ops, tape load, modal/dialog control).
- Compose screen now observes ViewModel state/effects; every button and dialog dispatches a `UiEvent` only. Networking, file ops, and mode switches happen inside the ViewModel.
- Activity simply hosts the screen and provides the ViewModel; file picker launch is triggered via `UiEffect`.

## Acceptance Criteria
- A `UiEvent` sealed class exists and is used for all user-driven inputs on the screen.
- Compose code no longer calls networking or mutates state directly; it only emits events.
- ViewModel exposes a single state stream as source of truth.
