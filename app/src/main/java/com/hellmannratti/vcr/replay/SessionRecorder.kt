package com.hellmannratti.vcr.replay

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * NDJSON session recorder (platform-agnostic).
 * - Accepts a base directory instead of Android Context.
 * - Optional metadata (appVersion/device) provided by the host app.
 */
class SessionRecorder(
    baseDir: File,
    logSessionStart: Boolean = true,
    private val clock: Clock = SystemClock,
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

    private val nextSeq: AtomicLong

    init {
        val dir = File(baseDir, "sessions").apply { mkdirs() }
        // Use a single, stable file and append to it across runs
        file = File(dir, "events.ndjson")
        if (!file.exists()) {
            file.createNewFile()
        }

        nextSeq = AtomicLong((readLastSeq(file) ?: -1L) + 1L)

        // Log a typed session-start event unless disabled (e.g., in REPLAY mode)
        if (logSessionStart) {
            log(
                SessionStartEvent(
                    seq = -1,
                    ts = clock.nowMs(),
                    appVersion = appVersion,
                    device = device
                )
            )
        }
    }

    fun log(event: Event) {
        val enriched = event.withEnvelope(
            seq = nextSeq.getAndIncrement(),
            ts = event.ts ?: clock.nowMs(),
            metadata = event.metadata
        )
        val line = json.encodeToString(enriched)
        ioExecutor.execute { file.appendText(line + "\n") }
    }

    fun file(): File = file

    private fun readLastSeq(file: File): Long? {
        val lastEvent = file.useLines { lines ->
            lines
                .mapNotNull { line -> runCatching { json.decodeFromString<Event>(line) }.getOrNull() }
                .lastOrNull()
        }
        return lastEvent?.seq
    }
}

private fun Event.withEnvelope(
    seq: Long,
    ts: Long?,
    metadata: Map<String, String>
): Event {
    return when (this) {
        is SessionStartEvent -> copy(seq = seq, ts = ts, metadata = metadata)
        is UiEventRecorded -> copy(seq = seq, ts = ts, metadata = metadata)
        is ActionEvent -> copy(seq = seq, ts = ts, metadata = metadata)
        is RequestEvent -> copy(seq = seq, ts = ts, metadata = metadata)
        is ResponseEvent -> copy(seq = seq, ts = ts, metadata = metadata)
    }
}
