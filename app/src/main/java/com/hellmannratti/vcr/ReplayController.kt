package com.hellmannratti.vcr

import com.hellmannratti.vcr.framework.AndroidTapeLogger
import com.hellmannratti.vcr.sessionkit.SessionPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class ReplayController {

    interface Handler {
        suspend fun emitUiEvent(event: SessionPlayer.RecordedUiEvent)
        suspend fun resetToInitialState()
    }

    private val logger = AndroidTapeLogger()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val handler = MutableStateFlow<Handler?>(null)

    val player: SessionPlayer = SessionPlayer(
        scope = scope,
        logger = logger,
        emitUiEvent = { recorded ->
            val h = handler.value
            if (h == null) {
                logger.w("ReplayController", "No handler attached; dropping replayed UI event")
                return@SessionPlayer
            }
            h.emitUiEvent(recorded)
        },
        resetToInitialState = {
            handler.value?.resetToInitialState()
        }
    )

    fun attachHandler(handler: Handler) {
        this.handler.value = handler
    }

    fun detachHandler(handler: Handler) {
        if (this.handler.value === handler) this.handler.value = null
    }

    fun loadLatestSession(file: File) {
        player.loadLatestSession(file)
    }

    fun play() {
        player.play(stepDelayMs = 0L)
    }

    fun pause() {
        player.pause()
    }

    suspend fun stepForward(): Boolean = player.stepForward()

    suspend fun stepBack(): Boolean = player.stepBack()

    suspend fun seek(position: Int): Boolean = player.seek(position)

    fun stopAndUnload() {
        player.stopAndUnload()
    }
}
