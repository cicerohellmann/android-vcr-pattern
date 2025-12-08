package com.hellmannratti.zombie

import android.app.Application
import android.content.Context
import com.hellmannratti.zombie.replay.AppConfig
import com.hellmannratti.zombie.replay.HttpClients.client
import com.hellmannratti.zombie.replay.Mode
import com.hellmannratti.zombie.replay.SessionRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient

/**
 * Holds a single SessionRecorder and OkHttpClient for the whole app so
 * user actions and network events end up in the same NDJSON session file.
 */
class ZombieApp : Application() {
    lateinit var recorder: SessionRecorder
        private set

    lateinit var okHttp: OkHttpClient
        private set

    lateinit  var config: AppConfig
        private set

    // Use default random - pattern matching makes replay independent of random sequence
    val random: kotlin.random.Random by lazy {
        kotlin.random.Random.Default
    }


    // Runtime mode state for switching between RECORD and REPLAY
    private var _currentMode = MutableStateFlow(Mode.RECORD)
    val currentMode: StateFlow<Mode> = _currentMode.asStateFlow()

    /**
     * Switch between RECORD and REPLAY modes at runtime.
     * Rebuilds the HTTP client with the appropriate interceptor.
     */
    fun switchMode(newMode: Mode) {
        _currentMode.value = newMode
        config = AppConfig(mode = newMode, tapeFile = config.tapeFile)
        
        okHttp = client(
            context = this,
            config = config
        )
    }

    /**
     * Update the HTTP client with a new replayer interceptor.
     * Used when loading a tape file at runtime in REPLAY mode.
     */
    fun updateHttpClientWithTape(replayer: com.hellmannratti.zombie.replay.ReplayerInterceptor) {
        okHttp = OkHttpClient.Builder()
            .addInterceptor(replayer)
            .build()
    }

    /**
     * Clear the buffer by recreating the SessionRecorder instance.
     * This clears any pending writes in the executor queue without deleting the file.
     */
    fun clearBuffer() {
        recorder = SessionRecorder(
            baseDir = filesDir,
            logSessionStart = config.mode != Mode.REPLAY,
            appVersion = "1.0",
            device = android.os.Build.MODEL ?: "unknown"
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Resolve mode before constructing the recorder so we can avoid logging a new SESSION_START in REPLAY
        config = EnvConfig.resolve(this)
        recorder = SessionRecorder(
                    baseDir = filesDir,
                    logSessionStart = config.mode != Mode.REPLAY,
                    appVersion = "1.0",
                    device = android.os.Build.MODEL ?: "unknown"
                )
        // Build the HTTP client according to the selected mode (LIVE / RECORD / REPLAY)
        okHttp = client(
            context = this,
            config = config
        )
    }

    companion object {
        fun from(context: Context): ZombieApp = context.applicationContext as ZombieApp
    }
}
