package com.hellmannratti.vcr.replay

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.buffer
import okio.source
import java.io.File

/**
 * Build a ReplayTape by reading the NDJSON event file and pairing REQUEST/RESPONSE.
 */
object TapeLoader {
    fun loadFromFile(file: File, patterns: List<UrlPattern> = emptyList()): ReplayTape {
        val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }

        val lines = mutableListOf<String>()
        file.source().buffer().use { buf ->
            while (!buf.exhausted()) buf.readUtf8Line()?.let(lines::add)
        }

        val events = parseAndValidateEvents(lines, json)

        // Helper function to normalize URL using patterns
        fun normalizeUrl(url: String): String {
            for (pattern in patterns) {
                if (pattern.matches(url)) {
                    return pattern.normalize(url)
                }
            }
            return url
        }

        return buildReplayTape(events, patterns, ::normalizeUrl)
    }

    /**
     * Load only the latest complete session from the NDJSON file.
     * A "session" is defined as the lines after the last SESSION_START that occurs
     * before the last RESPONSE in the file. This avoids picking up a fresh REPLAY run
     * that logged a new SESSION_START but has no responses yet.
     *
     * @param patterns URL patterns for normalizing dynamic URLs (e.g., /pokemon/{id})
     * @throws NoTapeFoundException if file doesn't exist or tape is empty
     */
    fun loadLatestSession(file: File, patterns: List<UrlPattern> = emptyList(), logger: TapeLogger = TapeLogger.NoOp): ReplayTape {
        // Throw exception if file doesn't exist
        if (!file.exists()) {
            logger.e("TapeLoader", "Replay file not found: ${file.absolutePath}")
            throw NoTapeFoundException("No tape file found at: ${file.absolutePath}")
        }

        val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }

        // Read all lines once (sufficient for typical test-sized files)
        val lines = mutableListOf<String>()
        file.source().buffer().use { buf ->
            while (!buf.exhausted()) buf.readUtf8Line()?.let(lines::add)
        }

        val events = parseAndValidateEvents(lines, json)

        // Helper function to normalize URL using patterns
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

        // Log tape statistics
        val totalResponses = tape.entries().count()
        logger.i("TapeLoader", "Loaded tape from ${file.name}: ${tape.uniqueRequestCount} unique requests, $totalResponses total responses")

        if (tape.uniqueRequestCount == 0) {
            logger.e("TapeLoader", "Tape is empty! No recorded responses found in session.")
            throw NoTapeFoundException("Tape file exists but contains no recorded responses: ${file.absolutePath}")
        } else {
            logger.d("TapeLoader", "Tape contents:")
            tape.entries().groupBy({ it.first }, { it.third }).forEach { (key, responses) ->
                logger.d("TapeLoader", "  ${key.method} ${key.url} (bodySha256=${key.bodySha256 ?: "null"}): ${responses.size} response(s)")
            }
        }

        return tape
    }

    /**
     * Save a ReplayTape back to an NDJSON file. Overwrites existing file.
     */
    fun save(
        file: File,
        tape: ReplayTape,
        logger: TapeLogger = TapeLogger.NoOp,
        clock: Clock = SystemClock,
        idGenerator: IdGenerator = UuidGenerator
    ) {
        val json = Json { classDiscriminator = "type" }
        file.parentFile?.mkdirs()
        val lines = mutableListOf<String>()
        var seq = 0L
        // Helpful session marker for humans; players ignore it
        lines += json.encodeToString(
            Event.serializer(),
            SessionStartEvent(seq = seq++, ts = clock.nowMs(), appVersion = "unknown", device = "unknown")
        )
        for ((key, _, resp) in tape.entries()) {
            val requestId = idGenerator.uuid()
            val req = RequestEvent(
                seq = seq++,
                ts = clock.nowMs(),
                requestId = requestId,
                method = key.method,
                url = key.url,
                bodySha256 = key.bodySha256
            )
            val res = ResponseEvent(
                seq = seq++,
                ts = clock.nowMs(),
                requestId = requestId,
                code = resp.code,
                headers = resp.headers,
                body = resp.body,
                durationMs = resp.durationMs
            )
            lines += json.encodeToString(Event.serializer(), req)
            lines += json.encodeToString(Event.serializer(), res)
        }
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        val total = lines.count { it.contains("\"type\":\"Response\"") }
        logger.i("TapeLoader", "Saved tape to ${'$'}{file.name}: ${'$'}total responses written")
    }

    private fun parseAndValidateEvents(lines: List<String>, json: Json): List<Event> {
        val events = lines
            .filter { it.isNotBlank() }
            .mapIndexed { idx, line ->
                try {
                    json.decodeFromString<Event>(line)
                } catch (t: Throwable) {
                    throw IllegalArgumentException("Malformed event at line ${idx + 1}", t)
                }
            }

        var lastSeq: Long? = null
        val requestIds = mutableSetOf<String>()
        for (e in events) {
            require(e.schema == 1) { "Unsupported schema version: ${e.schema}" }
            require(e.seq >= 0) { "Invalid seq: ${e.seq}" }
            if (lastSeq != null) {
                require(e.seq > lastSeq!!) { "Non-monotonic seq: ${e.seq} after ${lastSeq!!}" }
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

    private fun latestSessionSlice(events: List<Event>): List<Event> {
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
