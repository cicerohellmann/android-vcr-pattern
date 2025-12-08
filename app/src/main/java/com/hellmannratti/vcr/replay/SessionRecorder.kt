package com.hellmannratti.vcr.replay

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/**
 * NDJSON session recorder (platform-agnostic).
 * - Accepts a base directory instead of Android Context.
 * - Optional metadata (appVersion/device) provided by the host app.
 */
class SessionRecorder(
    baseDir: File,
    logSessionStart: Boolean = true,
    private val appVersion: String = "unknown",
    private val device: String = "unknown"
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val file: File

    init {
        val dir = File(baseDir, "sessions").apply { mkdirs() }
        // Use a single, stable file and append to it across runs
        file = File(dir, "events.ndjson")
        if (!file.exists()) {
            file.createNewFile()
        }

        // Log a typed session-start event unless disabled (e.g., in REPLAY mode)
        if (logSessionStart) {
            log(
                SessionStartEvent(
                    ts = System.currentTimeMillis(),
                    appVersion = appVersion,
                    device = device
                )
            )
        }
    }

    fun log(event: Event) {
        val line = json.encodeToString(event)
        ioExecutor.execute { file.appendText(line + "\n") }
    }

    fun file(): File = file
}
