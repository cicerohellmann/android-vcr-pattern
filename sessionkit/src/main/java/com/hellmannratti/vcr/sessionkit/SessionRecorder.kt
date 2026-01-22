package com.hellmannratti.vcr.sessionkit

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** NDJSON session recorder (platform-agnostic). */
internal class SessionRecorder(
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

    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val file: File

    private val nextSeq: AtomicLong

    init {
        val dir = File(baseDir, "sessions").apply { mkdirs() }
        file = File(dir, "events.ndjson")
        if (!file.exists()) {
            file.createNewFile()
        }

        nextSeq = AtomicLong((readLastSeq(file) ?: -1L) + 1L)

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
        // Assign seq and write on the single writer thread so file order always matches seq order,
        // even if multiple threads call log(...) concurrently.
        ioExecutor.execute {
            val enriched = event.withEnvelope(
                seq = nextSeq.getAndIncrement(),
                ts = event.ts ?: clock.nowMs(),
                metadata = event.metadata
            )
            val line = json.encodeToString(enriched)
            file.appendText(line + "\n")
        }
    }

    /**
     * Blocks until all currently queued writes are flushed to disk.
     * Safe to call multiple times.
     */
    fun flush() {
        // Submit a no-op behind all pending work and wait for completion.
        ioExecutor.submit { }.get()
    }

    /**
     * Flushes and stops the background writer.
     * After calling this, [log] must no longer be used.
     */
    fun close() {
        runCatching { flush() }
        ioExecutor.shutdown()
        // Best-effort; avoid hanging forever.
        runCatching { ioExecutor.awaitTermination(2, TimeUnit.SECONDS) }
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
