package com.hellmannratti.cassete.core

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
import kotlinx.serialization.json.JsonElement
import java.io.File

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
        val payload: Map<String, JsonElement>,
        val metadata: Map<String, String>
    )

    data class PlayerState(
        val loaded: Boolean = false,
        val playing: Boolean = false,
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
        events = TapeLoader.loadLatestSessionUiEvents(file, logger).map {
            RecordedUiEvent(
                seq = it.seq,
                ts = it.ts,
                screen = it.screen,
                event = it.event,
                payload = it.payload,
                metadata = it.metadata
            )
        }
        _state.value = PlayerState(
            loaded = true,
            playing = false,
            position = 0,
            total = events.size,
            sourceFile = file.absolutePath
        )
    }

    fun play(stepDelayMs: Long = 0L) {
        if (!state.value.loaded || state.value.playing) return

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
        return seek((state.value.position - 1).coerceAtLeast(0))
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
            fastForward(target)
            _state.value = _state.value.copy(position = target)
            true
        } else {
            fastForward(target)
            true
        }
    }

    fun stopAndUnload() {
        pause()
        events = emptyList()
        _state.value = PlayerState()
    }

    private suspend fun stepForwardInternal(): Boolean {
        val index = state.value.position
        if (index >= events.size) return false

        val event = events[index]
        runCatching { emitUiEvent(event) }
            .onFailure { error ->
                logger.e(
                    "SessionPlayer",
                    "Failed to emit UI event at position=$index seq=${event.seq}: ${error.message}"
                )
                throw error
            }

        _state.value = _state.value.copy(position = index + 1)
        return true
    }

    private suspend fun fastForward(toPositionExclusive: Int) {
        val target = toPositionExclusive.coerceIn(0, events.size)
        var index = state.value.position
        while (index < target) {
            emitUiEvent(events[index])
            index++
            _state.value = _state.value.copy(position = index)
        }
    }
}
