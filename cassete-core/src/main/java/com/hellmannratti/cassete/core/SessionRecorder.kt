package com.hellmannratti.cassete.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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
        ioExecutor.execute {
            val enriched = event.withEnvelope(
                seq = nextSeq.getAndIncrement(),
                ts = event.ts ?: clock.nowMs(),
                metadata = event.metadata
            )
            file.appendText(json.encodeToString(Event.serializer(), enriched) + "\n")
        }
    }

    fun flush() {
        ioExecutor.submit { }.get()
    }

    fun close() {
        runCatching { flush() }
        ioExecutor.shutdown()
        runCatching { ioExecutor.awaitTermination(2, TimeUnit.SECONDS) }
    }

    fun file(): File = file

    private fun readLastSeq(file: File): Long? {
        val lastEvent = file.useLines { lines ->
            lines
                .mapNotNull { line -> runCatching { json.decodeFromString(Event.serializer(), line) }.getOrNull() }
                .lastOrNull()
        }
        return lastEvent?.seq
    }
}

private fun Event.withEnvelope(
    seq: Long,
    ts: Long?,
    metadata: Map<String, String>
): Event = when (this) {
    is SessionStartEvent -> copy(seq = seq, ts = ts, metadata = metadata)
    is UiEventRecorded -> copy(seq = seq, ts = ts, metadata = metadata)
    is ActionEvent -> copy(seq = seq, ts = ts, metadata = metadata)
    is RequestEvent -> copy(seq = seq, ts = ts, metadata = metadata)
    is ResponseEvent -> copy(seq = seq, ts = ts, metadata = metadata)
}
