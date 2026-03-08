package com.hellmannratti.cassete.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class TapeLoaderCharacterizationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    @Test(expected = NoTapeFoundException::class)
    fun `loadLatestSession throws when the tape file is missing`() {
        val dir = createTempDirectory(prefix = "cassete_missing_").toFile()
        try {
            TapeLoader.loadLatestSession(File(dir, "missing.ndjson"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test(expected = NoTapeFoundException::class)
    fun `loadLatestSession throws when the latest session contains no responses`() {
        val dir = createTempDirectory(prefix = "cassete_empty_").toFile()
        try {
            val file = File(dir, "events.ndjson")
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":1,\"type\":\"UI_EVENT\",\"ts\":1700000000100,\"metadata\":{},\"screen\":\"ApiTest\",\"event\":\"Open\"}"
                    )
                }
            )

            TapeLoader.loadLatestSession(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `loadLatestSessionUiEvents supports ui-only tapes for player replay`() {
        val dir = createTempDirectory(prefix = "cassete_ui_only_").toFile()
        try {
            val file = File(dir, "events.ndjson")
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":1,\"type\":\"UI_EVENT\",\"ts\":1700000000100,\"metadata\":{},\"screen\":\"ApiTest\",\"event\":\"Open\"}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":2,\"type\":\"UI_EVENT\",\"ts\":1700000000200,\"metadata\":{},\"screen\":\"ApiTest\",\"event\":\"Dismiss\"}"
                    )
                }
            )

            val events = TapeLoader.loadLatestSessionUiEvents(file)

            assertEquals(listOf("Open", "Dismiss"), events.map { it.event })
            assertEquals(listOf(1L, 2L), events.map { it.seq })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `parseAndValidateEvents backfills the schema-v1 envelope for legacy ndjson lines`() {
        val events = TapeLoader.parseAndValidateEvents(
            lines = listOf(
                "{\"type\":\"SESSION_START\",\"appVersion\":\"1.0\",\"device\":\"test\",\"legacy\":true}",
                "{\"type\":\"REQUEST\",\"requestId\":\"r1\",\"method\":\"POST\",\"url\":\"https://example.com/items\",\"bodySha256\":\"abc\",\"legacy\":true}",
                "{\"type\":\"RESPONSE\",\"requestId\":\"r1\",\"code\":201,\"headers\":{\"content-type\":\"text/plain\"},\"body\":\"ok\",\"durationMs\":12,\"legacy\":true}"
            ),
            json = json,
            logger = TapeLogger.NoOp
        )

        assertEquals(3, events.size)

        val start = events[0] as SessionStartEvent
        assertEquals(1, start.schema)
        assertEquals(0L, start.seq)
        assertEquals(0L, start.ts)
        assertEquals(emptyMap<String, String>(), start.metadata)

        val request = events[1] as RequestEvent
        assertEquals(1, request.schema)
        assertEquals(1L, request.seq)
        assertEquals(0L, request.ts)
        assertEquals(emptyMap<String, String>(), request.metadata)
        assertEquals("abc", request.bodySha256)

        val response = events[2] as ResponseEvent
        assertEquals(1, response.schema)
        assertEquals(2L, response.seq)
        assertEquals(0L, response.ts)
        assertEquals(emptyMap<String, String>(), response.metadata)
        assertEquals(201, response.code)
        assertEquals("ok", response.body)
    }

    @Test
    fun `parseAndValidateEvents rejects a response that appears before its request in file order`() {
        val error = runCatching {
            TapeLoader.parseAndValidateEvents(
                lines = listOf(
                    "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}",
                    "{\"schema\":1,\"seq\":1,\"type\":\"RESPONSE\",\"ts\":1700000000100,\"metadata\":{},\"requestId\":\"r1\",\"code\":200,\"headers\":{},\"body\":\"late\",\"durationMs\":1}",
                    "{\"schema\":1,\"seq\":2,\"type\":\"REQUEST\",\"ts\":1700000000200,\"metadata\":{},\"requestId\":\"r1\",\"method\":\"GET\",\"url\":\"https://example.com/items\",\"bodySha256\":null}"
                ),
                json = json,
                logger = TapeLogger.NoOp
            )
        }.exceptionOrNull()

        requireNotNull(error)
        assertTrue(error.message.orEmpty().contains("unknown requestId"))
    }

    @Test
    fun `loadLatestSession keeps only the last session that contains the final response`() {
        val dir = createTempDirectory(prefix = "cassete_sessions_").toFile()
        try {
            val file = File(dir, "events.ndjson")
            val lines = listOf(
                "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"emulator\"}",
                "{\"schema\":1,\"seq\":1,\"type\":\"REQUEST\",\"ts\":2,\"metadata\":{},\"requestId\":\"r1\",\"method\":\"GET\",\"url\":\"https://x/a\",\"bodySha256\":null}",
                "{\"schema\":1,\"seq\":2,\"type\":\"RESPONSE\",\"ts\":3,\"metadata\":{},\"requestId\":\"r1\",\"code\":200,\"headers\":{},\"body\":\"a\",\"durationMs\":0}",
                "{\"schema\":1,\"seq\":3,\"type\":\"SESSION_START\",\"ts\":4,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"emulator\"}",
                "{\"schema\":1,\"seq\":4,\"type\":\"REQUEST\",\"ts\":5,\"metadata\":{},\"requestId\":\"r2\",\"method\":\"GET\",\"url\":\"https://x/b\",\"bodySha256\":null}",
                "{\"schema\":1,\"seq\":5,\"type\":\"SESSION_START\",\"ts\":6,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"emulator\"}",
                "{\"schema\":1,\"seq\":6,\"type\":\"REQUEST\",\"ts\":7,\"metadata\":{},\"requestId\":\"r3\",\"method\":\"POST\",\"url\":\"https://x/c\",\"bodySha256\":\"abc\"}",
                "{\"schema\":1,\"seq\":7,\"type\":\"RESPONSE\",\"ts\":8,\"metadata\":{},\"requestId\":\"r3\",\"code\":201,\"headers\":{\"content-type\":\"text/plain\"},\"body\":\"ok\",\"durationMs\":1}"
            )
            file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))

            val tape = TapeLoader.loadLatestSession(file, logger = TapeLogger.NoOp)
            val entries = tape.entries().toList()

            assertEquals(1, entries.size)
            val (key, _, recordedResponse) = entries.single()
            assertEquals("POST", key.method)
            assertEquals("https://x/c", key.url)
            assertEquals("abc", key.bodySha256)
            assertEquals(201, recordedResponse.code)
            assertEquals("ok", recordedResponse.body)
        } finally {
            dir.deleteRecursively()
        }
    }
}
