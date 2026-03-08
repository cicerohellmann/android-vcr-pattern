package com.hellmannratti.cassete.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File

object TapeLoader {
    fun loadLatestSession(
        file: File,
        normalizers: List<UrlNormalizer> = emptyList(),
        logger: TapeLogger = TapeLogger.NoOp
    ): ReplayTape {
        if (!file.exists()) {
            logger.e("TapeLoader", "Replay file not found: ${file.absolutePath}")
            throw NoTapeFoundException("No tape file found at: ${file.absolutePath}")
        }

        val events = parseAndValidateEvents(
            lines = file.readLines(),
            json = eventJson(),
            logger = logger
        )
        val sessionEvents = latestSessionSlice(events)
        val tape = buildReplayTape(sessionEvents, normalizers)

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

        val events = parseAndValidateEvents(
            lines = file.readLines(),
            json = eventJson(),
            logger = logger
        )
        return latestSessionSlice(events).filterIsInstance<UiEventRecorded>()
    }

    fun parseAndValidateEvents(
        lines: List<String>,
        json: Json = eventJson(),
        logger: TapeLogger = TapeLogger.NoOp
    ): List<Event> {
        val decoded = lines
            .asSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .mapIndexed { index, rawLine ->
                val line = if (index == 0) rawLine.removePrefix("\uFEFF") else rawLine
                try {
                    decodeEvent(json = json, line = line, index = index)
                } catch (t: Throwable) {
                    val preview = line.replace("\n", "\\n").replace("\r", "\\r").take(200)
                    throw IllegalArgumentException("Malformed event at line ${index + 1}: '$preview'", t)
                }
            }
            .toList()

        val events = normalizeSeqInFileOrder(decoded, logger)
        var lastSeq: Long? = null
        val requestIds = mutableSetOf<String>()
        for (event in events) {
            require(event.schema == 1) { "Unsupported schema version: ${event.schema}" }
            require(event.seq >= 0) { "Invalid seq: ${event.seq}" }
            if (lastSeq != null) {
                require(event.seq > lastSeq) {
                    "Non-monotonic seq after normalization: ${event.seq} after $lastSeq"
                }
            }
            lastSeq = event.seq

            when (event) {
                is RequestEvent -> requestIds += event.requestId
                is ResponseEvent -> require(event.requestId in requestIds) {
                    "Response references unknown requestId: ${event.requestId}"
                }
                else -> Unit
            }
        }

        return events
    }

    fun latestSessionSlice(events: List<Event>): List<Event> {
        val lastResponseIndex = events.indexOfLast { it is ResponseEvent }
        if (lastResponseIndex < 0) return events

        var startIndex = 0
        for (index in lastResponseIndex downTo 0) {
            if (events[index] is SessionStartEvent) {
                startIndex = index
                break
            }
        }
        return events.subList(startIndex, events.size)
    }

    private fun eventJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    private fun normalizeSeqInFileOrder(events: List<Event>, logger: TapeLogger): List<Event> {
        var lastSeq: Long? = null
        var normalizedAny = false
        val normalized = ArrayList<Event>(events.size)

        for (event in events) {
            val newSeq = when {
                event.seq < 0 -> 0L
                lastSeq == null -> event.seq
                event.seq > lastSeq -> event.seq
                else -> lastSeq + 1
            }

            if (newSeq != event.seq) {
                normalizedAny = true
                logger.w(
                    "TapeLoader",
                    "Normalizing non-increasing seq in tape: ${event.seq} -> $newSeq (preserving file order)"
                )
            }

            val fixed = if (newSeq == event.seq) event else event.copyWithSeq(newSeq)
            normalized += fixed
            lastSeq = fixed.seq
        }

        if (normalizedAny) {
            logger.w(
                "TapeLoader",
                "Tape contained non-increasing seq values; normalized to strictly increasing seq for replay."
            )
        }

        return normalized
    }

    private fun decodeEvent(json: Json, line: String, index: Int): Event {
        runCatching { return json.decodeFromString<Event>(line) }

        val element = json.parseToJsonElement(line)
        val obj = element as? JsonObject ?: error("Expected JSON object")
        val enriched = buildJsonObject {
            put("schema", obj["schema"] ?: JsonPrimitive(1))
            put("seq", obj["seq"] ?: JsonPrimitive(index.toLong()))
            put("ts", obj["ts"] ?: JsonPrimitive(0))
            put("metadata", obj["metadata"] ?: buildJsonObject { })
            for ((key, value) in obj) {
                if (key == "schema" || key == "seq" || key == "ts" || key == "metadata") continue
                put(key, value)
            }
        }

        return json.decodeFromJsonElement(enriched)
    }

    private fun buildReplayTape(
        events: List<Event>,
        normalizers: List<UrlNormalizer>
    ): ReplayTape {
        val requestById = mutableMapOf<String, RequestEvent>()
        val map = mutableMapOf<RequestKey, MutableList<RecordedResponse>>()

        for (event in events) {
            when (event) {
                is RequestEvent -> requestById[event.requestId] = event
                is ResponseEvent -> {
                    val request = requestById[event.requestId] ?: continue
                    val key = HttpRequestSnapshot(
                        method = request.method,
                        url = request.url,
                        headers = request.headers,
                        bodySha256 = request.bodySha256,
                        bodyUtf8 = request.bodyUtf8
                    ).asKey(normalizers)
                    map.getOrPut(key) { mutableListOf() }.add(
                        RecordedResponse(
                            code = event.code,
                            headers = event.headers,
                            body = event.body,
                            durationMs = event.durationMs
                        )
                    )
                }
                else -> Unit
            }
        }

        return ReplayTape(map = map, normalizers = normalizers)
    }
}

private fun Event.copyWithSeq(seq: Long): Event = when (this) {
    is SessionStartEvent -> copy(seq = seq)
    is UiEventRecorded -> copy(seq = seq)
    is ActionEvent -> copy(seq = seq)
    is RequestEvent -> copy(seq = seq)
    is ResponseEvent -> copy(seq = seq)
}
