package com.hellmannratti.vcr

import android.app.Application
import android.content.Context
import com.hellmannratti.vcr.framework.AndroidTapeLogger
import com.hellmannratti.vcr.sessionkit.KotlinRandomProvider
import com.hellmannratti.vcr.sessionkit.Mode
import com.hellmannratti.vcr.sessionkit.RandomProvider
import com.hellmannratti.vcr.sessionkit.SessionKit
import com.hellmannratti.vcr.sessionkit.SystemClock
import com.hellmannratti.vcr.sessionkit.UuidGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds a single SessionRecorder and OkHttpClient for the whole app so
 * user actions and network events end up in the same NDJSON session file.
 */
class VcrApp : Application() {
    lateinit var sessionKit: SessionKit
        private set

    val clock = SystemClock
    val idGenerator = UuidGenerator
    val randomProvider: RandomProvider = KotlinRandomProvider(kotlin.random.Random.Default)


    // Runtime mode state for switching between RECORD and REPLAY
    private var _currentMode = MutableStateFlow(Mode.RECORD)
    val currentMode: StateFlow<Mode> = _currentMode.asStateFlow()

    /**
     * Switch between RECORD and REPLAY modes at runtime.
     * Rebuilds the HTTP client with the appropriate interceptor.
     */
    fun switchMode(newMode: Mode) {
        _currentMode.value = newMode
        sessionKit.switchMode(newMode)
    }

    /**
     * Update the HTTP client with a new replayer interceptor.
     * Used when loading a tape file at runtime in REPLAY mode.
     */
    fun loadTapeFile(file: java.io.File): Int {
        return sessionKit.loadTape(file)
    }

    /**
     * Clear the buffer by recreating the SessionRecorder instance.
     * This clears any pending writes in the executor queue without deleting the file.
     * Also rebuilds the HTTP client to restore recording capability.
     */
    fun clearBuffer() {
        sessionKit.clearBuffer()
    }

    override fun onCreate() {
        super.onCreate()
        val config = EnvConfig.resolve(this)
        _currentMode.value = config.mode
        sessionKit = SessionKit(
            config = config,
            baseDir = filesDir,
            clock = clock,
            idGenerator = idGenerator,
            randomProvider = randomProvider,
            logger = AndroidTapeLogger()
        )
    }

    companion object {
        fun from(context: Context): VcrApp = context.applicationContext as VcrApp
    }
}
