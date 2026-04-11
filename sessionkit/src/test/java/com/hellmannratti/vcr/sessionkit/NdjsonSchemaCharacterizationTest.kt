package com.hellmannratti.vcr.sessionkit

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AC-0.6: Characterization tests for current NDJSON schema compatibility (round-trip serialize/deserialize)
 *
 * These tests capture the current serialization contract:
 * - Every event type round-trips through JSON without data loss
 * - The "type" discriminator field is always present in serialized output
 * - Envelope fields (schema, seq, ts, metadata) are always present when encodeDefaults=true
 * - Default values are preserved across round-trips
 * - Optional/nullable fields serialize correctly (null bodySha256, null ts)
 * - Legacy NDJSON lines without envelope fields can be deserialized via TapeLoader fallback
 * - SessionRecorder output is consumable by TapeLoader (write/read round-trip)
 * - Unknown fields in JSON are ignored during deserialization (forward compatibility)
 */
class NdjsonSchemaCharacterizationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    // ============================================================================
    // Round-trip: each event type survives encode -> decode without data loss
    // ============================================================================

    @Test
    fun `round-trip SessionStartEvent preserves all fields`() {
        val original: Event = SessionStartEvent(
            schema = 1, seq = 0, ts = 1700000000000, metadata = mapOf("env" to "test"),
            appVersion = "2.1.0", device = "Pixel 7"
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Event>(encoded)

        assertTrue(decoded is SessionStartEvent)
        decoded as SessionStartEvent
        assertEquals(1, decoded.schema)
        assertEquals(0L, decoded.seq)
        assertEquals(1700000000000L, decoded.ts)
        assertEquals(mapOf("env" to "test"), decoded.metadata)
        assertEquals("2.1.0", decoded.appVersion)
        assertEquals("Pixel 7", decoded.device)
    }

    @Test
    fun `round-trip ActionEvent preserves all fields including details map`() {
        val original: Event = ActionEvent(
            schema = 1, seq = 1, ts = 1700000000100,
            metadata = emptyMap(), name = "tap_button",
            details = buildJsonObject { put("x", 100); put("y", 200) }
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Event>(encoded)

        assertTrue(decoded is ActionEvent)
        decoded as ActionEvent
        assertEquals("tap_button", decoded.name)
        assertEquals(100, decoded.details["x"]!!.jsonPrimitive.long)
        assertEquals(200, decoded.details["y"]!!.jsonPrimitive.long)
    }

    @Test
    fun `round-trip UiEventRecorded preserves all fields including payload map`() {
        val original: Event = UiEventRecorded(
            schema = 1, seq = 2, ts = 1700000000200,
            metadata = emptyMap(), screen = "HomeScreen", event = "scroll",
            payload = buildJsonObject { put("direction", "down"); put("offset", 350) }
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Event>(encoded)

        assertTrue(decoded is UiEventRecorded)
        decoded as UiEventRecorded
        assertEquals("HomeScreen", decoded.screen)
        assertEquals("scroll", decoded.event)
        assertEquals("down", decoded.payload["direction"]!!.jsonPrimitive.content)
    }

    @Test
    fun `round-trip RequestEvent preserves all fields including non-null bodySha256`() {
        val original: Event = RequestEvent(
            schema = 1, seq = 3, ts = 1700000000300,
            metadata = emptyMap(), requestId = "req-1", method = "POST",
            url = "https://api.example.com/items", bodySha256 = "abc123def456"
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Event>(encoded)

        assertTrue(decoded is RequestEvent)
        decoded as RequestEvent
        assertEquals("req-1", decoded.requestId)
        assertEquals("POST", decoded.method)
        assertEquals("https://api.example.com/items", decoded.url)
        assertEquals("abc123def456", decoded.bodySha256)
    }

    @Test
    fun `round-trip RequestEvent preserves null bodySha256`() {
        val original: Event = RequestEvent(
            schema = 1, seq = 4, ts = 1700000000400,
            metadata = emptyMap(), requestId = "req-2", method = "GET",
            url = "https://api.example.com/items", bodySha256 = null
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Event>(encoded)

        decoded as RequestEvent
        assertNull(decoded.bodySha256)
    }

    @Test
    fun `round-trip ResponseEvent preserves all fields`() {
        val original: Event = ResponseEvent(
            schema = 1, seq = 5, ts = 1700000000500,
            metadata = emptyMap(), requestId = "req-1", code = 201,
            headers = mapOf("Content-Type" to "application/json", "X-Request-Id" to "abc"),
            body = """{"id":42,"name":"widget"}""", durationMs = 150
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Event>(encoded)

        assertTrue(decoded is ResponseEvent)
        decoded as ResponseEvent
        assertEquals("req-1", decoded.requestId)
        assertEquals(201, decoded.code)
        assertEquals("application/json", decoded.headers["Content-Type"])
        assertEquals("abc", decoded.headers["X-Request-Id"])
        assertEquals("""{"id":42,"name":"widget"}""", decoded.body)
        assertEquals(150L, decoded.durationMs)
    }

    // ============================================================================
    // Serialized JSON shape: discriminator and envelope fields present
    // ============================================================================

    @Test
    fun `serialized output contains type discriminator field`() {
        val events: List<Event> = listOf(
            SessionStartEvent(seq = 0, ts = 1, appVersion = "1.0", device = "d"),
            ActionEvent(seq = 1, ts = 2, name = "a"),
            UiEventRecorded(seq = 2, ts = 3, screen = "s", event = "e"),
            RequestEvent(seq = 3, ts = 4, requestId = "r", method = "GET", url = "https://x.com"),
            ResponseEvent(seq = 4, ts = 5, requestId = "r", code = 200, headers = emptyMap(), body = "", durationMs = 0)
        )
        val expectedTypes = listOf("SESSION_START", "ACTION", "UI_EVENT", "REQUEST", "RESPONSE")

        events.zip(expectedTypes).forEach { (event, expectedType) ->
            val encoded = json.encodeToString(event)
            val obj = json.parseToJsonElement(encoded).jsonObject
            assertEquals(expectedType, obj["type"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun `serialized output always includes envelope fields with encodeDefaults`() {
        val event: Event = RequestEvent(
            seq = 0, requestId = "r", method = "GET", url = "https://x.com"
        )
        val encoded = json.encodeToString(event)
        val obj = json.parseToJsonElement(encoded).jsonObject

        assertTrue("schema field present", "schema" in obj)
        assertTrue("seq field present", "seq" in obj)
        assertTrue("ts field present", "ts" in obj)
        assertTrue("metadata field present", "metadata" in obj)
    }

    @Test
    fun `default values are written when encodeDefaults is true`() {
        val event: Event = ActionEvent(seq = 5, name = "test")
        val encoded = json.encodeToString(event)
        val obj = json.parseToJsonElement(encoded).jsonObject

        assertEquals(1, obj["schema"]!!.jsonPrimitive.long)
        assertEquals("{}", obj["metadata"].toString())
        assertEquals("{}", obj["details"].toString())
    }

    // ============================================================================
    // Legacy NDJSON deserialization via TapeLoader fallback
    // ============================================================================

    @Test
    fun `TapeLoader decodes legacy lines missing envelope fields`() {
        val dir = createTempDir(prefix = "ndjson_legacy_")
        try {
            val file = File(dir, "events.ndjson")
            // Legacy format: no schema, seq, ts, or metadata fields
            file.writeText(buildString {
                appendLine("""{"type":"SESSION_START","appVersion":"1.0","device":"test"}""")
                appendLine("""{"type":"REQUEST","requestId":"r1","method":"GET","url":"https://example.com/api","bodySha256":null}""")
                appendLine("""{"type":"RESPONSE","requestId":"r1","code":200,"headers":{},"body":"hello","durationMs":5}""")
            })

            val tape = TapeLoader.loadLatestSession(file = file)
            assertEquals(1, tape.uniqueRequestCount)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `TapeLoader assigns sequential seq to legacy lines without seq field`() {
        val legacyLines = listOf(
            """{"type":"SESSION_START","appVersion":"1.0","device":"test"}""",
            """{"type":"REQUEST","requestId":"r1","method":"GET","url":"https://example.com/api","bodySha256":null}""",
            """{"type":"RESPONSE","requestId":"r1","code":200,"headers":{},"body":"ok","durationMs":1}"""
        )
        val parseJson = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }
        val events = TapeLoader.parseAndValidateEvents(legacyLines, parseJson, TapeLogger.NoOp)

        assertEquals(3, events.size)
        assertEquals(0L, events[0].seq)
        assertEquals(1L, events[1].seq)
        assertEquals(2L, events[2].seq)
    }

    @Test
    fun `TapeLoader decodes legacy lines with extra unknown fields`() {
        val dir = createTempDir(prefix = "ndjson_extra_")
        try {
            val file = File(dir, "events.ndjson")
            // Legacy lines include an extra field "legacy":true that should be ignored
            file.writeText(buildString {
                appendLine("""{"type":"SESSION_START","appVersion":"1.0","device":"test","legacy":true}""")
                appendLine("""{"type":"REQUEST","requestId":"r1","method":"GET","url":"https://example.com/test","bodySha256":null,"extra_field":"ignored"}""")
                appendLine("""{"type":"RESPONSE","requestId":"r1","code":200,"headers":{},"body":"ok","durationMs":1,"custom":99}""")
            })

            val tape = TapeLoader.loadLatestSession(file = file)
            assertEquals(1, tape.uniqueRequestCount)
        } finally {
            dir.deleteRecursively()
        }
    }

    // ============================================================================
    // Write/read round-trip: SessionRecorder -> TapeLoader
    // ============================================================================

    @Test
    fun `SessionRecorder output is loadable by TapeLoader`() {
        val dir = createTempDir(prefix = "ndjson_recorder_")
        try {
            val recorder = SessionRecorder(
                baseDir = dir,
                logSessionStart = true,
                appVersion = "1.0.0",
                device = "test-device"
            )

            recorder.log(RequestEvent(seq = -1, requestId = "r1", method = "GET", url = "https://api.test/data"))
            recorder.log(ResponseEvent(
                seq = -1, requestId = "r1", code = 200,
                headers = mapOf("Content-Type" to "application/json"),
                body = """{"result":"ok"}""", durationMs = 42
            ))
            recorder.flush()

            val tape = TapeLoader.loadLatestSession(file = recorder.file())
            assertEquals(1, tape.uniqueRequestCount)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `SessionRecorder writes valid NDJSON lines parseable as JSON objects`() {
        val dir = createTempDir(prefix = "ndjson_valid_")
        try {
            val recorder = SessionRecorder(
                baseDir = dir,
                logSessionStart = true,
                appVersion = "1.0.0",
                device = "test-device"
            )

            recorder.log(RequestEvent(seq = -1, requestId = "r1", method = "POST", url = "https://api.test/submit", bodySha256 = "hash123"))
            recorder.log(ResponseEvent(seq = -1, requestId = "r1", code = 201, headers = emptyMap(), body = "created", durationMs = 10))
            recorder.flush()

            val lines = recorder.file().readLines().filter { it.isNotBlank() }
            assertTrue("Should have at least 3 lines (start + req + resp)", lines.size >= 3)

            // Every line must be valid JSON with a "type" discriminator
            lines.forEach { line ->
                val obj = json.parseToJsonElement(line).jsonObject
                assertNotNull("type field present", obj["type"])
                assertNotNull("schema field present", obj["schema"])
                assertNotNull("seq field present", obj["seq"])
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `SessionRecorder assigns monotonically increasing seq values`() {
        val dir = createTempDir(prefix = "ndjson_seq_")
        try {
            val recorder = SessionRecorder(
                baseDir = dir,
                logSessionStart = true,
                appVersion = "1.0.0",
                device = "test-device"
            )

            recorder.log(ActionEvent(seq = -1, name = "action1"))
            recorder.log(ActionEvent(seq = -1, name = "action2"))
            recorder.log(RequestEvent(seq = -1, requestId = "r1", method = "GET", url = "https://api.test/x"))
            recorder.log(ResponseEvent(seq = -1, requestId = "r1", code = 200, headers = emptyMap(), body = "ok", durationMs = 1))
            recorder.flush()

            val lines = recorder.file().readLines().filter { it.isNotBlank() }
            val seqValues = lines.map { line ->
                json.parseToJsonElement(line).jsonObject["seq"]!!.jsonPrimitive.long
            }

            // Verify strictly increasing
            seqValues.zipWithNext().forEach { (a, b) ->
                assertTrue("seq must be strictly increasing: $a < $b", b > a)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    // ============================================================================
    // NDJSON format edge cases
    // ============================================================================

    @Test
    fun `response body containing newlines is escaped in single JSON line`() {
        val bodyWithNewlines = "line1\nline2\nline3"
        val original: Event = ResponseEvent(
            seq = 0, requestId = "r1", code = 200,
            headers = emptyMap(), body = bodyWithNewlines, durationMs = 1
        )
        val encoded = json.encodeToString(original)

        // The encoded string must be a single line (no raw newlines)
        assertEquals(1, encoded.lines().size)

        // Round-trip preserves the newlines in the body
        val decoded = json.decodeFromString<Event>(encoded) as ResponseEvent
        assertEquals(bodyWithNewlines, decoded.body)
    }

    @Test
    fun `response body containing JSON is preserved as string not parsed`() {
        val jsonBody = """{"users":[{"id":1,"name":"Alice"},{"id":2,"name":"Bob"}]}"""
        val original: Event = ResponseEvent(
            seq = 0, requestId = "r1", code = 200,
            headers = mapOf("Content-Type" to "application/json"),
            body = jsonBody, durationMs = 5
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Event>(encoded) as ResponseEvent

        assertEquals(jsonBody, decoded.body)
    }

    @Test
    fun `metadata map with multiple entries round-trips correctly`() {
        val original: Event = RequestEvent(
            seq = 0, ts = 100,
            metadata = mapOf("trace_id" to "abc123", "span_id" to "def456", "sampled" to "true"),
            requestId = "r1", method = "GET", url = "https://api.test/x"
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Event>(encoded) as RequestEvent

        assertEquals(3, decoded.metadata.size)
        assertEquals("abc123", decoded.metadata["trace_id"])
        assertEquals("def456", decoded.metadata["span_id"])
        assertEquals("true", decoded.metadata["sampled"])
    }

    @Test
    fun `null ts is preserved across round-trip`() {
        val original: Event = ActionEvent(seq = 0, ts = null, name = "test")
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Event>(encoded) as ActionEvent

        assertNull(decoded.ts)
    }

    @Test
    fun `empty details and payload maps round-trip as empty objects`() {
        val action: Event = ActionEvent(seq = 0, name = "empty_details", details = emptyMap())
        val ui: Event = UiEventRecorded(seq = 1, screen = "s", event = "e", payload = emptyMap())

        val actionDecoded = json.decodeFromString<Event>(json.encodeToString(action)) as ActionEvent
        val uiDecoded = json.decodeFromString<Event>(json.encodeToString(ui)) as UiEventRecorded

        assertTrue(actionDecoded.details.isEmpty())
        assertTrue(uiDecoded.payload.isEmpty())
    }

    // ============================================================================
    // Forward compatibility: unknown fields ignored
    // ============================================================================

    @Test
    fun `deserialization ignores unknown fields in current schema events`() {
        val lineWithExtra = """{"schema":1,"seq":0,"ts":100,"metadata":{},"type":"SESSION_START","appVersion":"1.0","device":"test","futureField":"value","anotherNew":42}"""
        val decoded = json.decodeFromString<Event>(lineWithExtra)

        assertTrue(decoded is SessionStartEvent)
        decoded as SessionStartEvent
        assertEquals("1.0", decoded.appVersion)
        assertEquals("test", decoded.device)
    }

    @Test
    fun `full NDJSON file with all event types loads as valid tape`() {
        val dir = createTempDir(prefix = "ndjson_full_")
        try {
            val file = File(dir, "events.ndjson")
            file.writeText(buildString {
                appendLine("""{"schema":1,"seq":0,"ts":1000,"metadata":{},"type":"SESSION_START","appVersion":"2.0","device":"Pixel"}""")
                appendLine("""{"schema":1,"seq":1,"ts":1001,"metadata":{},"type":"ACTION","name":"open_app","details":{}}""")
                appendLine("""{"schema":1,"seq":2,"ts":1002,"metadata":{},"type":"UI_EVENT","screen":"Home","event":"tap","payload":{"button":"refresh"}}""")
                appendLine("""{"schema":1,"seq":3,"ts":1003,"metadata":{},"type":"REQUEST","requestId":"r1","method":"GET","url":"https://api.test/data","bodySha256":null}""")
                appendLine("""{"schema":1,"seq":4,"ts":1004,"metadata":{},"type":"RESPONSE","requestId":"r1","code":200,"headers":{"Content-Type":"application/json"},"body":"{\"ok\":true}","durationMs":50}""")
                appendLine("""{"schema":1,"seq":5,"ts":1005,"metadata":{},"type":"REQUEST","requestId":"r2","method":"POST","url":"https://api.test/submit","bodySha256":"sha256hash"}""")
                appendLine("""{"schema":1,"seq":6,"ts":1006,"metadata":{},"type":"RESPONSE","requestId":"r2","code":201,"headers":{},"body":"created","durationMs":100}""")
            })

            val tape = TapeLoader.loadLatestSession(file = file)
            assertEquals(2, tape.uniqueRequestCount)
        } finally {
            dir.deleteRecursively()
        }
    }
}
