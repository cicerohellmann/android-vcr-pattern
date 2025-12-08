package com.hellmannratti.vcr.replay

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

        val requestById = mutableMapOf<String, RequestEvent>()
        val map = mutableMapOf<RequestKey, ArrayDeque<RecordedResponse>>()

        // Helper function to normalize URL using patterns
        fun normalizeUrl(url: String): String {
            for (pattern in patterns) {
                if (pattern.matches(url)) {
                    return pattern.normalize(url)
                }
            }
            return url
        }

        file.source().buffer().use { buf ->
            while (!buf.exhausted()) {
                val line = buf.readUtf8Line() ?: continue
                // Skip meta lines that don't parse into Event
                val event = runCatching { json.decodeFromString<Event>(line) }.getOrNull() ?: continue
                when (event) {
                    is RequestEvent -> requestById[event.requestId] = event
                    is ResponseEvent -> {
                        val req = requestById[event.requestId] ?: continue
                        val normalizedUrl = normalizeUrl(req.url)
                        val key = RequestKey(req.method, normalizedUrl, req.bodySha256)
                        val queue = map.getOrPut(key) { ArrayDeque() }
                        queue.add(
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
        }

        return ReplayTape(map, patterns)
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

        // Find index of the last RESPONSE
        var lastResponseIdx = -1
        for (i in lines.lastIndex downTo 0) {
            val e = runCatching { json.decodeFromString<Event>(lines[i]) }.getOrNull()
            if (e is ResponseEvent) { lastResponseIdx = i; break }
        }

        // Find the most recent SESSION_START before that RESPONSE
        val startIdx = if (lastResponseIdx >= 0) {
            var s = 0
            for (i in lastResponseIdx downTo 0) {
                val e = runCatching { json.decodeFromString<Event>(lines[i]) }.getOrNull()
                if (e is SessionStartEvent) { s = i; break }
            }
            s
        } else 0

        val requestById = mutableMapOf<String, RequestEvent>()
        val map = mutableMapOf<RequestKey, ArrayDeque<RecordedResponse>>()

        // Helper function to normalize URL using patterns
        fun normalizeUrl(url: String): String {
            for (pattern in patterns) {
                if (pattern.matches(url)) {
                    return pattern.normalize(url)
                }
            }
            return url
        }

        for (i in startIdx..lines.lastIndex) {
            val event = runCatching { json.decodeFromString<Event>(lines[i]) }.getOrNull() ?: continue
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

        // Log tape statistics
        val totalResponses = map.values.sumOf { it.size }
        logger.i("TapeLoader", "Loaded tape from ${file.name}: ${map.size} unique requests, $totalResponses total responses")

        if (map.isEmpty()) {
            logger.e("TapeLoader", "Tape is empty! No recorded responses found in session.")
            throw NoTapeFoundException("Tape file exists but contains no recorded responses: ${file.absolutePath}")
        } else {
            logger.d("TapeLoader", "Tape contents:")
            map.forEach { (key, responses) ->
                logger.d("TapeLoader", "  ${key.method} ${key.url} (bodySha256=${key.bodySha256 ?: "null"}): ${responses.size} response(s)")
            }
        }

        return ReplayTape(map, patterns)
    }

    /**
     * Save a ReplayTape back to an NDJSON file. Overwrites existing file.
     */
    fun save(file: File, tape: ReplayTape, logger: TapeLogger = TapeLogger.NoOp) {
        val json = Json { classDiscriminator = "type" }
        file.parentFile?.mkdirs()
        val lines = mutableListOf<String>()
        // Helpful session marker for humans; players ignore it
        lines += json.encodeToString(Event.serializer(), SessionStartEvent(ts = System.currentTimeMillis(), appVersion = "unknown", device = "unknown"))
        for ((key, _, resp) in tape.entries()) {
            val requestId = java.util.UUID.randomUUID().toString()
            val req = RequestEvent(
                ts = System.currentTimeMillis(),
                requestId = requestId,
                method = key.method,
                url = key.url,
                bodySha256 = key.bodySha256
            )
            val res = ResponseEvent(
                ts = System.currentTimeMillis(),
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
}
