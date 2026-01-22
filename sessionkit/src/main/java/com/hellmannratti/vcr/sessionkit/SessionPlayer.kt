package com.hellmannratti.vcr.sessionkit

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Deterministically replays recorded UI events from an NDJSON session log.
 *
 * Rewind/seek strategy: reducer recomputation.
 * - The caller provides [resetToInitialState].
 * - On backward seek, we reset then fast-forward by re-emitting events up to the target position.
 */
class SessionPlayer(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val logger: TapeLogger = TapeLogger.NoOp,
    private val emitUiEvent: suspend (RecordedUiEvent) -> Unit,
    private val resetToInitialState: suspend () -> Unit
) {
    data class RecordedUiEvent(
        val seq: Long,
        val ts: Long?,
        val screen: String,
        val event: String,
        val payload: Map<String, kotlinx.serialization.json.JsonElement>,
        val metadata: Map<String, String>
    )

    data class PlayerState(
        val loaded: Boolean = false,
        val playing: Boolean = false,
        /** Cursor points to the next event to be emitted. */
        val position: Int = 0,
        val total: Int = 0,
        val sourceFile: String? = null
    )

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var events: List<RecordedUiEvent> = emptyList()
    private var playJob: Job? = null

    fun loadLatestSession(file: File) {
        pause()

        val uiEvents = TapeLoader.loadLatestSessionUiEvents(file, logger)
            .map {
                RecordedUiEvent(
                    seq = it.seq,
                    ts = it.ts,
                    screen = it.screen,
                    event = it.event,
                    payload = it.payload,
                    metadata = it.metadata
                )
            }

        events = uiEvents
        _state.value = PlayerState(
            loaded = true,
            playing = false,
            position = 0,
            total = events.size,
            sourceFile = file.absolutePath
        )
    }

    fun play(stepDelayMs: Long = 0L) {
        if (!state.value.loaded) return
        if (state.value.playing) return

        _state.value = _state.value.copy(playing = true)
        playJob = scope.launch(dispatcher) {
            while (isActive && state.value.playing) {
                val didStep = stepForwardInternal()
                if (!didStep) {
                    _state.value = _state.value.copy(playing = false)
                    break
                }
                if (stepDelayMs > 0) delay(stepDelayMs)
            }
        }
    }

    fun pause() {
        _state.value = _state.value.copy(playing = false)
        playJob?.cancel()
        playJob = null
    }

    suspend fun stepForward(): Boolean {
        pause()
        return stepForwardInternal()
    }

    suspend fun stepBack(): Boolean {
        pause()
        val target = (state.value.position - 1).coerceAtLeast(0)
        return seek(target)
    }

    suspend fun seek(targetPosition: Int): Boolean {
        pause()

        if (!state.value.loaded) return false
        val target = targetPosition.coerceIn(0, events.size)
        val current = state.value.position

        if (target == current) return true

        return if (target < current) {
            resetToInitialState()
            _state.value = _state.value.copy(position = 0)
            fastForward(toPositionExclusive = target)
            _state.value = _state.value.copy(position = target)
            true
        } else {
            fastForward(toPositionExclusive = target)
            true
        }
    }

    fun stopAndUnload() {
        pause()
        events = emptyList()
        _state.value = PlayerState()
    }

    private suspend fun stepForwardInternal(): Boolean {
        val idx = state.value.position
        if (idx >= events.size) return false

        val e = events[idx]
        runCatching {
            emitUiEvent(e)
        }.onFailure { t ->
            logger.e("SessionPlayer", "Failed to emit UI event at position=$idx seq=${e.seq}: ${t.message}")
            throw t
        }

        _state.value = _state.value.copy(position = idx + 1)
        return true
    }

    private suspend fun fastForward(toPositionExclusive: Int) {
        val target = toPositionExclusive.coerceIn(0, events.size)
        var i = state.value.position
        while (i < target) {
            val e = events[i]
            emitUiEvent(e)
            i++
            _state.value = _state.value.copy(position = i)
        }
    }
}
