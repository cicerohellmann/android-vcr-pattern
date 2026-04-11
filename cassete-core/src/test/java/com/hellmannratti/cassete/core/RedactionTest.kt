package com.hellmannratti.cassete.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AC-7.6: Tests verifying redaction is applied during recording.
 *
 * These tests verify that HeaderRedactor, BodyRedactor, and UrlNormalizer
 * are applied when CasseteRuntime records HTTP requests and responses,
 * ensuring sensitive data never reaches the tape file.
 */
class RedactionTest {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    private fun createRuntime(
        config: CasseteConfig,
        dir: File
    ): CasseteRuntime = CasseteRuntime(
        config = config,
        baseDir = dir,
        clock = FixedClock(1700000000000),
        idGenerator = SequenceIdGenerator(
            ArrayDeque(List(20) { "req-$it" })
        ),
        logger = TapeLogger.NoOp
    )

    private fun readEventsFromTape(file: File): List<String> =
        file.readLines().filter { it.isNotBlank() }

    // ============================================================================
    // HeaderRedactor: request headers
    // ============================================================================

    @Test
    fun `request header redaction strips Authorization header value`() {
        val dir = createTempDir(prefix = "redact_req_hdr_")
        try {
            val runtime = createRuntime(
                CasseteConfig(
                    initialMode = Mode.RECORD,
                    requestHeaderRedactor = HeaderRedactor.redactAuthTokens()
                ),
                dir
            )

            runtime.recordHttpRequest(
                HttpRequestSnapshot(
                    method = "GET",
                    url = "https://api.test/data",
                    headers = mapOf(
                        "Authorization" to "Bearer secret-token-123",
                        "Accept" to "application/json"
                    )
                )
            )
            runtime.flush()

            val lines = readEventsFromTape(runtime.recordingFile)
            val requestLine = lines.first { it.contains("\"REQUEST\"") }
            val obj = json.parseToJsonElement(requestLine).asJsonObject()

            val headers = obj["headers"]!!.asJsonObject()
            assertEquals("<redacted>", headers["Authorization"]!!.asString())
            assertEquals("application/json", headers["Accept"]!!.asString())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `request header redactor returning null removes the header entirely`() {
        val dir = createTempDir(prefix = "redact_remove_hdr_")
        try {
            val stripCookies = HeaderRedactor { name, value ->
                if (name.equals("cookie", ignoreCase = true)) null else value
            }
            val runtime = createRuntime(
                CasseteConfig(
                    initialMode = Mode.RECORD,
                    requestHeaderRedactor = stripCookies
                ),
                dir
            )

            runtime.recordHttpRequest(
                HttpRequestSnapshot(
                    method = "GET",
                    url = "https://api.test/data",
                    headers = mapOf(
                        "Cookie" to "session=abc123",
                        "Accept" to "text/html"
                    )
                )
            )
            runtime.flush()

            val lines = readEventsFromTape(runtime.recordingFile)
            val requestLine = lines.first { it.contains("\"REQUEST\"") }
            val obj = json.parseToJsonElement(requestLine).asJsonObject()

            val headers = obj["headers"]!!.asJsonObject()
            assertNull("Cookie header should be removed", headers["Cookie"])
            assertEquals("text/html", headers["Accept"]!!.asString())
        } finally {
            dir.deleteRecursively()
        }
    }

    // ============================================================================
    // HeaderRedactor: response headers
    // ============================================================================

    @Test
    fun `response header redaction is applied during recording`() {
        val dir = createTempDir(prefix = "redact_resp_hdr_")
        try {
            val runtime = createRuntime(
                CasseteConfig(
                    initialMode = Mode.RECORD,
                    responseHeaderRedactor = HeaderRedactor { name, value ->
                        if (name.equals("set-cookie", ignoreCase = true)) "<redacted>" else value
                    }
                ),
                dir
            )

            val requestId = runtime.recordHttpRequest(
                HttpRequestSnapshot(method = "GET", url = "https://api.test/data")
            )!!
            runtime.recordHttpResponse(
                requestId,
                HttpResponseSnapshot(
                    code = 200,
                    headers = mapOf(
                        "Set-Cookie" to "session=secret; HttpOnly",
                        "Content-Type" to "application/json"
                    ),
                    bodyUtf8 = "{}",
                    durationMs = 10
                )
            )
            runtime.flush()

            val lines = readEventsFromTape(runtime.recordingFile)
            val responseLine = lines.first { it.contains("\"RESPONSE\"") }
            val obj = json.parseToJsonElement(responseLine).asJsonObject()

            val headers = obj["headers"]!!.asJsonObject()
            assertEquals("<redacted>", headers["Set-Cookie"]!!.asString())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `ignored response headers are stripped before redaction`() {
        val dir = createTempDir(prefix = "redact_ignored_")
        try {
            val runtime = createRuntime(
                CasseteConfig(
                    initialMode = Mode.RECORD,
                    ignoredResponseHeaders = setOf("content-encoding", "content-length", "x-internal-trace")
                ),
                dir
            )

            val requestId = runtime.recordHttpRequest(
                HttpRequestSnapshot(method = "GET", url = "https://api.test/data")
            )!!
            runtime.recordHttpResponse(
                requestId,
                HttpResponseSnapshot(
                    code = 200,
                    headers = mapOf(
                        "Content-Type" to "text/plain",
                        "Content-Encoding" to "gzip",
                        "Content-Length" to "42",
                        "X-Internal-Trace" to "abc123"
                    ),
                    bodyUtf8 = "hello",
                    durationMs = 5
                )
            )
            runtime.flush()

            val lines = readEventsFromTape(runtime.recordingFile)
            val responseLine = lines.first { it.contains("\"RESPONSE\"") }

            assertFalse("content-encoding should be stripped", responseLine.contains("gzip"))
            assertFalse("x-internal-trace should be stripped", responseLine.contains("abc123"))
            assertTrue("Content-Type should be kept", responseLine.contains("text/plain"))
        } finally {
            dir.deleteRecursively()
        }
    }

    // ============================================================================
    // BodyRedactor: request and response bodies
    // ============================================================================

    @Test
    fun `request body redactor is applied during recording`() {
        val dir = createTempDir(prefix = "redact_req_body_")
        try {
            val runtime = createRuntime(
                CasseteConfig(
                    initialMode = Mode.RECORD,
                    requestBodyRedactor = BodyRedactor { _, body ->
                        body.replace(Regex("\"password\":\"[^\"]+\""), "\"password\":\"***\"")
                    }
                ),
                dir
            )

            runtime.recordHttpRequest(
                HttpRequestSnapshot(
                    method = "POST",
                    url = "https://api.test/login",
                    bodyUtf8 = """{"username":"alice","password":"s3cret"}""",
                    bodySha256 = "somehash"
                )
            )
            runtime.flush()

            val lines = readEventsFromTape(runtime.recordingFile)
            val requestLine = lines.first { it.contains("\"REQUEST\"") }
            val obj = json.parseToJsonElement(requestLine).asJsonObject()
            val bodyUtf8 = obj["bodyUtf8"]!!.asString()

            assertTrue("password should be redacted", bodyUtf8.contains("\"password\":\"***\""))
            assertFalse("original password should not appear", bodyUtf8.contains("s3cret"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `response body redactor is applied during recording`() {
        val dir = createTempDir(prefix = "redact_resp_body_")
        try {
            val runtime = createRuntime(
                CasseteConfig(
                    initialMode = Mode.RECORD,
                    responseBodyRedactor = BodyRedactor { _, body ->
                        body.replace(Regex("\"email\":\"[^\"]+\""), "\"email\":\"<redacted>\"")
                    }
                ),
                dir
            )

            val requestId = runtime.recordHttpRequest(
                HttpRequestSnapshot(method = "GET", url = "https://api.test/profile")
            )!!
            runtime.recordHttpResponse(
                requestId,
                HttpResponseSnapshot(
                    code = 200,
                    headers = mapOf("Content-Type" to "application/json"),
                    bodyUtf8 = """{"name":"Alice","email":"alice@example.com"}""",
                    durationMs = 15
                )
            )
            runtime.flush()

            val lines = readEventsFromTape(runtime.recordingFile)
            val responseLine = lines.first { it.contains("\"RESPONSE\"") }
            val obj = json.parseToJsonElement(responseLine).asJsonObject()
            val body = obj["body"]!!.asString()

            assertTrue("email should be redacted", body.contains("\"email\":\"<redacted>\""))
            assertFalse("original email should not appear", body.contains("alice@example.com"))
        } finally {
            dir.deleteRecursively()
        }
    }

    // ============================================================================
    // UrlNormalizer: URL normalization during recording
    // ============================================================================

    @Test
    fun `URL normalizer is applied to recorded request URLs`() {
        val dir = createTempDir(prefix = "redact_url_")
        try {
            val runtime = createRuntime(
                CasseteConfig(
                    initialMode = Mode.RECORD,
                    urlNormalizers = listOf(
                        UrlPattern.fromRetrofitStyle("https://api.test/users/{userId}/posts/{postId}")
                    )
                ),
                dir
            )

            runtime.recordHttpRequest(
                HttpRequestSnapshot(
                    method = "GET",
                    url = "https://api.test/users/42/posts/99"
                )
            )
            runtime.flush()

            val lines = readEventsFromTape(runtime.recordingFile)
            val requestLine = lines.first { it.contains("\"REQUEST\"") }

            assertTrue("URL should be normalized", requestLine.contains("{userId}"))
            assertTrue("URL should be normalized", requestLine.contains("{postId}"))
            assertFalse("Concrete IDs should not appear", requestLine.contains("/42/"))
            assertFalse("Concrete IDs should not appear", requestLine.contains("/99"))
        } finally {
            dir.deleteRecursively()
        }
    }

    // ============================================================================
    // keepAll / keepBody defaults: no redaction when unconfigured
    // ============================================================================

    @Test
    fun `default keepAll redactors preserve all headers and bodies`() {
        val dir = createTempDir(prefix = "redact_default_")
        try {
            val runtime = createRuntime(
                CasseteConfig(initialMode = Mode.RECORD),
                dir
            )

            val requestId = runtime.recordHttpRequest(
                HttpRequestSnapshot(
                    method = "POST",
                    url = "https://api.test/data",
                    headers = mapOf("Authorization" to "Bearer token123"),
                    bodyUtf8 = """{"secret":"value"}""",
                    bodySha256 = "hash"
                )
            )!!
            runtime.recordHttpResponse(
                requestId,
                HttpResponseSnapshot(
                    code = 200,
                    headers = mapOf("Content-Type" to "application/json"),
                    bodyUtf8 = """{"email":"real@email.com"}""",
                    durationMs = 5
                )
            )
            runtime.flush()

            val lines = readEventsFromTape(runtime.recordingFile)
            val requestLine = lines.first { it.contains("\"REQUEST\"") }
            val responseLine = lines.first { it.contains("\"RESPONSE\"") }

            // With defaults, nothing is redacted
            assertTrue("Auth header preserved", requestLine.contains("Bearer token123"))
            assertTrue("Request body preserved", requestLine.contains("secret"))
            assertTrue("Response body preserved", responseLine.contains("real@email.com"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `recording in non-RECORD mode does not write events`() {
        val dir = createTempDir(prefix = "redact_passthrough_")
        try {
            val runtime = createRuntime(
                CasseteConfig(initialMode = Mode.PASSTHROUGH),
                dir
            )

            val requestId = runtime.recordHttpRequest(
                HttpRequestSnapshot(method = "GET", url = "https://api.test/data")
            )
            assertNull("Should not record in PASSTHROUGH mode", requestId)

            runtime.flush()
            val lines = readEventsFromTape(runtime.recordingFile)
            // Only SESSION_START should be present (no request/response)
            assertFalse("No REQUEST should be written", lines.any { it.contains("\"REQUEST\"") })
        } finally {
            dir.deleteRecursively()
        }
    }

    // ============================================================================
    // Helpers
    // ============================================================================

    private fun CasseteRuntime.flush() {
        // Use switchMode round-trip to trigger flush
        val currentMode = mode.value
        switchMode(Mode.REPLAY)
        switchMode(currentMode)
    }

    private fun kotlinx.serialization.json.JsonElement.asJsonObject() =
        this as kotlinx.serialization.json.JsonObject

    private fun kotlinx.serialization.json.JsonElement.asString() =
        (this as kotlinx.serialization.json.JsonPrimitive).content
}
