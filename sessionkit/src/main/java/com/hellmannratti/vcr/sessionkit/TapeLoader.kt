package com.hellmannratti.vcr.sessionkit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okio.buffer
import okio.source
import java.io.File

internal object TapeLoader {
    fun loadLatestSession(
        file: File,
        patterns: List<UrlPattern> = emptyList(),
        logger: TapeLogger = TapeLogger.NoOp
    ): ReplayTape {
        if (!file.exists()) {
            logger.e("TapeLoader", "Replay file not found: ${file.absolutePath}")
            throw NoTapeFoundException("No tape file found at: ${file.absolutePath}")
        }

        val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }

        val lines = mutableListOf<String>()
        file.source().buffer().use { buf ->
            while (!buf.exhausted()) buf.readUtf8Line()?.let(lines::add)
        }

        val events = parseAndValidateEvents(lines, json, logger)

        fun normalizeUrl(url: String): String {
            for (pattern in patterns) {
                if (pattern.matches(url)) {
                    return pattern.normalize(url)
                }
            }
            return url
        }

        val sessionEvents = latestSessionSlice(events)

        val tape = buildReplayTape(sessionEvents, patterns, ::normalizeUrl)

        val totalResponses = tape.entries().count()
        logger.i(
            "TapeLoader",
            "Loaded tape from ${file.name}: ${tape.uniqueRequestCount} unique requests, $totalResponses total responses"
        )

        if (tape.uniqueRequestCount == 0) {
            logger.e("TapeLoader", "Tape is empty! No recorded responses found in session.")
            throw NoTapeFoundException("Tape file exists but contains no recorded responses: ${file.absolutePath}")
        }

        return tape
    }

    fun loadLatestSessionUiEvents(
        file: File,
        logger: TapeLogger = TapeLogger.NoOp
    ): List<UiEventRecorded> {
        if (!file.exists()) {
            logger.e("TapeLoader", "Replay file not found: ${file.absolutePath}")
            throw NoTapeFoundException("No tape file found at: ${file.absolutePath}")
        }

        val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }

        val lines = mutableListOf<String>()
        file.source().buffer().use { buf ->
            while (!buf.exhausted()) buf.readUtf8Line()?.let(lines::add)
        }

        val events = parseAndValidateEvents(lines, json, logger)
        val sessionEvents = latestSessionSlice(events)
        return sessionEvents.filterIsInstance<UiEventRecorded>()
    }

    internal fun parseAndValidateEvents(lines: List<String>, json: Json, logger: TapeLogger): List<Event> {
        val decoded = lines
            .asSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .mapIndexed { idx, rawLine ->
                // Some exporters/editors prepend a UTF-8 BOM (\uFEFF) to the first line.
                // kotlinx.serialization's JSON decoder does not accept BOM before '{', so we strip it.
                val line = if (idx == 0) rawLine.removePrefix("\uFEFF") else rawLine

                try {
                    decodeEvent(json = json, line = line, idx = idx)
                } catch (t: Throwable) {
                    val preview = line
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .take(200)
                    throw IllegalArgumentException(
                        "Malformed event at line ${idx + 1}: '${preview}'",
                        t
                    )
                }
            }
            .toList()

        // Some real-world tapes can be mildly corrupted (e.g., concurrent writers, partial writes,
        // manual edits) and end up with non-increasing seq values in file order.
        // For replay we only need a deterministic ordering key, so we normalize seq to be strictly
        // increasing while preserving file order.
        val events = normalizeSeqInFileOrder(decoded, logger)

        var lastSeq: Long? = null
        val requestIds = mutableSetOf<String>()
        for (e in events) {
            require(e.schema == 1) { "Unsupported schema version: ${e.schema}" }
            require(e.seq >= 0) { "Invalid seq: ${e.seq}" }
            if (lastSeq != null) {
                require(e.seq > lastSeq!!) { "Non-monotonic seq after normalization: ${e.seq} after ${lastSeq!!}" }
            }
            lastSeq = e.seq

            when (e) {
                is RequestEvent -> requestIds += e.requestId
                is ResponseEvent -> require(e.requestId in requestIds) { "Response references unknown requestId: ${e.requestId}" }
                else -> Unit
            }
        }

        return events
    }

    private fun normalizeSeqInFileOrder(events: List<Event>, logger: TapeLogger): List<Event> {
        var lastSeq: Long? = null
        var didNormalize = false
        val normalized = ArrayList<Event>(events.size)

        for (e in events) {
            val newSeq = when {
                e.seq < 0 -> 0L
                lastSeq == null -> e.seq
                e.seq > lastSeq!! -> e.seq
                else -> lastSeq!! + 1
            }

            if (newSeq != e.seq) {
                didNormalize = true
                logger.w(
                    "TapeLoader",
                    "Normalizing non-increasing seq in tape: ${e.seq} -> $newSeq (preserving file order)"
                )
            }

            val fixed = if (newSeq == e.seq) e else e.copyWithSeq(newSeq)
            normalized += fixed
            lastSeq = fixed.seq
        }

        if (didNormalize) {
            logger.w(
                "TapeLoader",
                "Tape contained non-increasing seq values; normalized to strictly increasing seq for replay."
            )
        }
        return normalized
    }

    private fun Event.copyWithSeq(seq: Long): Event {
        return when (this) {
            is SessionStartEvent -> copy(seq = seq)
            is UiEventRecorded -> copy(seq = seq)
            is ActionEvent -> copy(seq = seq)
            is RequestEvent -> copy(seq = seq)
            is ResponseEvent -> copy(seq = seq)
        }
    }

    private fun decodeEvent(json: Json, line: String, idx: Int): Event {
        // First try the current schema directly.
        runCatching { return json.decodeFromString<Event>(line) }

        // Fallback: tolerate legacy lines that omit the envelope fields.
        // We inject defaults and synthesize seq from line order.
        val element = json.parseToJsonElement(line)
        val obj = element as? JsonObject ?: error("Expected JSON object")

        val enriched: JsonObject = buildJsonObject {
            // Defaults for the schema-v1 envelope.
            put("schema", obj["schema"] ?: JsonPrimitive(1))
            put("seq", obj["seq"] ?: JsonPrimitive(idx.toLong()))
            put("ts", obj["ts"] ?: JsonPrimitive(0))
            put("metadata", obj["metadata"] ?: buildJsonObject { })

            // Preserve all original fields (including "type" + payload).
            for ((k, v) in obj) {
                if (k == "schema" || k == "seq" || k == "ts" || k == "metadata") continue
                put(k, v)
            }
        }

        return json.decodeFromJsonElement<Event>(enriched)
    }

    internal fun latestSessionSlice(events: List<Event>): List<Event> {
        val lastResponseIdx = events.indexOfLast { it is ResponseEvent }
        if (lastResponseIdx < 0) return events

        var startIdx = 0
        for (i in lastResponseIdx downTo 0) {
            if (events[i] is SessionStartEvent) {
                startIdx = i
                break
            }
        }
        return events.subList(startIdx, events.size)
    }

    private fun buildReplayTape(
        events: List<Event>,
        patterns: List<UrlPattern>,
        normalizeUrl: (String) -> String
    ): ReplayTape {
        val requestById = mutableMapOf<String, RequestEvent>()
        val map = mutableMapOf<RequestKey, ArrayDeque<RecordedResponse>>()

        for (event in events) {
            when (event) {
                is RequestEvent -> requestById[event.requestId] = event
                is ResponseEvent -> {
                    val req = requestById[event.requestId] ?: continue
                    val normalizedUrl = normalizeUrl(req.url)
                    val key = RequestKey(req.method, normalizedUrl, req.bodySha256)
                    map.getOrPut(key) { ArrayDeque() }.add(
                        RecordedResponse(event.code, event.headers, event.body, event.durationMs)
                    )
                }

                else -> Unit
            }
        }

        return ReplayTape(map, patterns)
    }
}
