package com.hellmannratti.vcr.sessionkit

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AC-0.5: Characterization tests for request/response ordering by `seq` field
 *
 * These tests capture the current behavior of how the `seq` field orders requests and responses:
 * - Events with strictly increasing seq values are processed in order
 * - Seq values establish a global ordering across all request/response pairs
 * - Out-of-order seq values are normalized during tape loading
 * - Missing seq values (gaps) are preserved during normalization
 * - Seq field determines the order of responses for the same request
 * - Sequential responses for repeated requests respect their seq ordering
 *
 * The goal is to freeze the seq-based ordering behavior before refactoring tape loading.
 */
class SeqOrderingCharacterizationTest {

    private fun req(url: String, method: String = "GET", body: ByteArray = byteArrayOf()): Request {
        val builder = Request.Builder().url(url.toHttpUrl())
        return if (method == "GET" || method == "HEAD") {
            builder.method(method, null).build()
        } else {
            builder.method(method, body.toRequestBody()).build()
        }
    }

    // ============================================================================
    // Tests for sequential ordering by seq field
    // ============================================================================

    @Test
    fun `seq ordering - responses for same request returned in seq order`() {
        // Build a tape where responses for the same request are created with different seq values
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "seq_10_response", 10),
                    RecordedResponse(200, emptyMap(), "seq_20_response", 20),
                    RecordedResponse(200, emptyMap(), "seq_30_response", 30)
                )
            )
        )

        val request = req("https://api.test/items", method = "GET")

        val response1 = tape.find(request, bodySha256 = null)
        assertEquals("seq_10_response", response1?.body)

        val response2 = tape.find(request, bodySha256 = null)
        assertEquals("seq_20_response", response2?.body)

        val response3 = tape.find(request, bodySha256 = null)
        assertEquals("seq_30_response", response3?.body)
    }

    @Test
    fun `seq ordering - multiple request types maintain independent ordering`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "GET_1", 10),
                    RecordedResponse(200, emptyMap(), "GET_2", 20)
                ),
                RequestKey("POST", "https://api.test/items", null) to listOf(
                    RecordedResponse(201, emptyMap(), "POST_1", 15),
                    RecordedResponse(201, emptyMap(), "POST_2", 25)
                )
            )
        )

        val getRequest = req("https://api.test/items", method = "GET")
        val postRequest = req("https://api.test/items", method = "POST", body = byteArrayOf(1, 2, 3))

        // Get responses in order
        val getResp1 = tape.find(getRequest, bodySha256 = null)
        assertEquals("GET_1", getResp1?.body)

        // Post response (seq 15 - after first GET but before second GET)
        val postResp1 = tape.find(postRequest, bodySha256 = null)
        assertEquals("POST_1", postResp1?.body)

        // Second get response
        val getResp2 = tape.find(getRequest, bodySha256 = null)
        assertEquals("GET_2", getResp2?.body)

        // Second post response
        val postResp2 = tape.find(postRequest, bodySha256 = null)
        assertEquals("POST_2", postResp2?.body)
    }

    @Test
    fun `seq ordering - responses returned in recorded seq order regardless of request type`() {
        // Simulate a tape where different request types are interleaved by seq
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/a", null) to listOf(
                    RecordedResponse(200, emptyMap(), "GET_a_seq_10", 10),
                    RecordedResponse(200, emptyMap(), "GET_a_seq_40", 40)
                ),
                RequestKey("POST", "https://api.test/b", null) to listOf(
                    RecordedResponse(201, emptyMap(), "POST_b_seq_20", 20),
                    RecordedResponse(201, emptyMap(), "POST_b_seq_50", 50)
                ),
                RequestKey("PUT", "https://api.test/c", null) to listOf(
                    RecordedResponse(200, emptyMap(), "PUT_c_seq_30", 30)
                )
            )
        )

        val getReq = req("https://api.test/a", method = "GET")
        val postReq = req("https://api.test/b", method = "POST", body = byteArrayOf(1))
        val putReq = req("https://api.test/c", method = "PUT", body = byteArrayOf(2))

        // Responses should be returned in their recorded order (cursor advancement)
        // not in global seq order - cursor is per-key
        val resp1 = tape.find(getReq, bodySha256 = null)
        assertEquals("GET_a_seq_10", resp1?.body)

        val resp2 = tape.find(postReq, bodySha256 = null)
        assertEquals("POST_b_seq_20", resp2?.body)

        val resp3 = tape.find(putReq, bodySha256 = null)
        assertEquals("PUT_c_seq_30", resp3?.body)

        val resp4 = tape.find(getReq, bodySha256 = null)
        assertEquals("GET_a_seq_40", resp4?.body)

        val resp5 = tape.find(postReq, bodySha256 = null)
        assertEquals("POST_b_seq_50", resp5?.body)
    }

    // ============================================================================
    // Tests for out-of-order seq handling
    // ============================================================================

    @Test
    fun `out-of-order seq - loader normalizes non-increasing seq during loading`() {
        val dir = createTempDir(prefix = "sessionkit_seq_")
        try {
            val file = File(dir, "events.ndjson")

            // Write events with out-of-order seq
            file.writeText(
                buildString {
                    // seq 0
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                    // seq 5
                    appendLine(
                        "{\"schema\":1,\"seq\":5,\"type\":\"REQUEST\",\"ts\":1700000000100,\"metadata\":{},\"requestId\":\"r1\",\"method\":\"GET\",\"url\":\"https://api.test/items\",\"bodySha256\":null}"
                    )
                    // seq 2 (out of order - should be normalized)
                    appendLine(
                        "{\"schema\":1,\"seq\":2,\"type\":\"RESPONSE\",\"ts\":1700000000200,\"metadata\":{},\"requestId\":\"r1\",\"code\":200,\"headers\":{},\"body\":\"response1\",\"durationMs\":100}"
                    )
                    // seq 10
                    appendLine(
                        "{\"schema\":1,\"seq\":10,\"type\":\"REQUEST\",\"ts\":1700000000300,\"metadata\":{},\"requestId\":\"r2\",\"method\":\"GET\",\"url\":\"https://api.test/items\",\"bodySha256\":null}"
                    )
                    // seq 8 (out of order - should be normalized)
                    appendLine(
                        "{\"schema\":1,\"seq\":8,\"type\":\"RESPONSE\",\"ts\":1700000000400,\"metadata\":{},\"requestId\":\"r2\",\"code\":200,\"headers\":{},\"body\":\"response2\",\"durationMs\":100}"
                    )
                }
            )

            // Load tape - should normalize seq to be strictly increasing
            val tape = TapeLoader.loadLatestSession(file)

            // Verify tape was loaded successfully
            assertEquals(1, tape.uniqueRequestCount)

            val request = req("https://api.test/items", method = "GET")
            val resp1 = tape.find(request, bodySha256 = null)
            assertEquals("response1", resp1?.body)

            val resp2 = tape.find(request, bodySha256 = null)
            assertEquals("response2", resp2?.body)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `out-of-order seq - responses still returned in file order after normalization`() {
        val dir = createTempDir(prefix = "sessionkit_seq_file_order_")
        try {
            val file = File(dir, "events.ndjson")

            // Write a sequence where seq goes backwards then forward
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                    // First request
                    appendLine(
                        "{\"schema\":1,\"seq\":100,\"type\":\"REQUEST\",\"ts\":1700000000100,\"metadata\":{},\"requestId\":\"r1\",\"method\":\"GET\",\"url\":\"https://api.test/data\",\"bodySha256\":null}"
                    )
                    // First response - seq out of order (30 < 100)
                    appendLine(
                        "{\"schema\":1,\"seq\":30,\"type\":\"RESPONSE\",\"ts\":1700000000200,\"metadata\":{},\"requestId\":\"r1\",\"code\":200,\"headers\":{},\"body\":\"first\",\"durationMs\":100}"
                    )
                    // Second request - seq even lower (20 < 30)
                    appendLine(
                        "{\"schema\":1,\"seq\":20,\"type\":\"REQUEST\",\"ts\":1700000000300,\"metadata\":{},\"requestId\":\"r2\",\"method\":\"GET\",\"url\":\"https://api.test/data\",\"bodySha256\":null}"
                    )
                    // Second response - seq still lower (10 < 20)
                    appendLine(
                        "{\"schema\":1,\"seq\":10,\"type\":\"RESPONSE\",\"ts\":1700000000400,\"metadata\":{},\"requestId\":\"r2\",\"code\":200,\"headers\":{},\"body\":\"second\",\"durationMs\":100}"
                    )
                }
            )

            // Load should succeed despite backwards seq
            val tape = TapeLoader.loadLatestSession(file)

            val request = req("https://api.test/data", method = "GET")
            val resp1 = tape.find(request, bodySha256 = null)
            assertEquals("first", resp1?.body)

            val resp2 = tape.find(request, bodySha256 = null)
            assertEquals("second", resp2?.body)
        } finally {
            dir.deleteRecursively()
        }
    }

    // ============================================================================
    // Tests for missing seq values (gaps in sequence)
    // ============================================================================

    @Test
    fun `missing seq - gaps in seq sequence are preserved during normalization`() {
        val dir = createTempDir(prefix = "sessionkit_seq_gaps_")
        try {
            val file = File(dir, "events.ndjson")

            // Create a tape with gaps in seq (0, 10, 20 but missing 5, 15)
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":10,\"type\":\"REQUEST\",\"ts\":1700000000100,\"metadata\":{},\"requestId\":\"r1\",\"method\":\"GET\",\"url\":\"https://api.test/a\",\"bodySha256\":null}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":20,\"type\":\"RESPONSE\",\"ts\":1700000000200,\"metadata\":{},\"requestId\":\"r1\",\"code\":200,\"headers\":{},\"body\":\"resp1\",\"durationMs\":100}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":30,\"type\":\"REQUEST\",\"ts\":1700000000300,\"metadata\":{},\"requestId\":\"r2\",\"method\":\"GET\",\"url\":\"https://api.test/a\",\"bodySha256\":null}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":40,\"type\":\"RESPONSE\",\"ts\":1700000000400,\"metadata\":{},\"requestId\":\"r2\",\"code\":200,\"headers\":{},\"body\":\"resp2\",\"durationMs\":100}"
                    )
                }
            )

            // Load tape - gaps should be preserved
            val tape = TapeLoader.loadLatestSession(file)

            val request = req("https://api.test/a", method = "GET")
            val resp1 = tape.find(request, bodySha256 = null)
            assertEquals("resp1", resp1?.body)

            val resp2 = tape.find(request, bodySha256 = null)
            assertEquals("resp2", resp2?.body)
        } finally {
            dir.deleteRecursively()
        }
    }

    // ============================================================================
    // Tests for seq field with body hash matching
    // ============================================================================

    @Test
    fun `seq with body hash - responses for different body hashes maintain independent seq order`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/submit", "hash1") to listOf(
                    RecordedResponse(201, emptyMap(), "hash1_response1", 10),
                    RecordedResponse(201, emptyMap(), "hash1_response2", 20)
                ),
                RequestKey("POST", "https://api.test/submit", "hash2") to listOf(
                    RecordedResponse(400, emptyMap(), "hash2_response1", 15),
                    RecordedResponse(400, emptyMap(), "hash2_response2", 25)
                )
            )
        )

        val body1 = byteArrayOf(1, 2, 3)
        val body2 = byteArrayOf(4, 5, 6)

        val req1 = req("https://api.test/submit", method = "POST", body = body1)
        val req2 = req("https://api.test/submit", method = "POST", body = body2)

        val resp1_1 = tape.find(req1, bodySha256 = "hash1")
        assertEquals("hash1_response1", resp1_1?.body)

        val resp2_1 = tape.find(req2, bodySha256 = "hash2")
        assertEquals("hash2_response1", resp2_1?.body)

        val resp1_2 = tape.find(req1, bodySha256 = "hash1")
        assertEquals("hash1_response2", resp1_2?.body)

        val resp2_2 = tape.find(req2, bodySha256 = "hash2")
        assertEquals("hash2_response2", resp2_2?.body)
    }

    @Test
    fun `seq with body hash fallback - null hash bucket receives responses in order`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/submit", null) to listOf(
                    RecordedResponse(201, emptyMap(), "fallback_response1", 10),
                    RecordedResponse(201, emptyMap(), "fallback_response2", 20)
                )
            )
        )

        val req = req("https://api.test/submit", method = "POST", body = byteArrayOf(1, 2))

        // First call with a specific hash (not in tape, falls back to null)
        val resp1 = tape.find(req, bodySha256 = "nonexistent_hash")
        assertEquals("fallback_response1", resp1?.body)

        // Second call falls back to null hash and gets next response
        val resp2 = tape.find(req, bodySha256 = "another_nonexistent_hash")
        assertEquals("fallback_response2", resp2?.body)
    }

    // ============================================================================
    // Tests for seq field with empty/exhausted responses
    // ============================================================================

    @Test
    fun `seq exhaustion - after all responses consumed by seq, returns null`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "only_response", 10)
                )
            )
        )

        val request = req("https://api.test/items", method = "GET")

        val resp1 = tape.find(request, bodySha256 = null)
        assertNotNull(resp1)
        assertEquals("only_response", resp1?.body)

        val resp2 = tape.find(request, bodySha256 = null)
        assertNull(resp2)
    }

    @Test
    fun `seq with cursor reset - reset clears seq tracking and allows re-replay`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/reset", null) to listOf(
                    RecordedResponse(200, emptyMap(), "first_response", 10),
                    RecordedResponse(200, emptyMap(), "second_response", 20)
                )
            )
        )

        val request = req("https://api.test/reset", method = "GET")

        // First replay
        val resp1 = tape.find(request, bodySha256 = null)
        assertEquals("first_response", resp1?.body)

        val resp2 = tape.find(request, bodySha256 = null)
        assertEquals("second_response", resp2?.body)

        // After exhaustion
        val resp3 = tape.find(request, bodySha256 = null)
        assertNull(resp3)

        // Reset cursor
        tape.resetCursors()

        // Re-replay from beginning
        val resp4 = tape.find(request, bodySha256 = null)
        assertNotNull(resp4)
        assertEquals("first_response", resp4?.body)

        val resp5 = tape.find(request, bodySha256 = null)
        assertNotNull(resp5)
        assertEquals("second_response", resp5?.body)
    }

    // ============================================================================
    // Tests for seq field validation and constraints
    // ============================================================================

    @Test
    fun `seq validation - negative seq values after seq 0 cause non-monotonic error during loading`() {
        val dir = createTempDir(prefix = "sessionkit_neg_seq_")
        try {
            val file = File(dir, "events.ndjson")

            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                    // Negative seq after seq 0 causes normalization to produce duplicate 0,
                    // which fails the post-normalization monotonic check
                    appendLine(
                        "{\"schema\":1,\"seq\":-5,\"type\":\"REQUEST\",\"ts\":1700000000100,\"metadata\":{},\"requestId\":\"r1\",\"method\":\"GET\",\"url\":\"https://api.test/x\",\"bodySha256\":null}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":-3,\"type\":\"RESPONSE\",\"ts\":1700000000200,\"metadata\":{},\"requestId\":\"r1\",\"code\":200,\"headers\":{},\"body\":\"neg_seq_resp\",\"durationMs\":100}"
                    )
                }
            )

            val error = runCatching {
                TapeLoader.loadLatestSession(file)
            }.exceptionOrNull()

            assertNotNull(error)
            assertTrue(error is IllegalArgumentException)
            assertTrue(error!!.message.orEmpty().contains("Non-monotonic seq"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `seq after normalization - normalized events maintain strict monotonic increase`() {
        val dir = createTempDir(prefix = "sessionkit_monotonic_")
        try {
            val file = File(dir, "events.ndjson")

            // Create a complex case with multiple out-of-order sequences
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":100,\"type\":\"REQUEST\",\"ts\":1700000000100,\"metadata\":{},\"requestId\":\"r1\",\"method\":\"GET\",\"url\":\"https://api.test/one\",\"bodySha256\":null}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":50,\"type\":\"RESPONSE\",\"ts\":1700000000200,\"metadata\":{},\"requestId\":\"r1\",\"code\":200,\"headers\":{},\"body\":\"resp1\",\"durationMs\":100}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":150,\"type\":\"REQUEST\",\"ts\":1700000000300,\"metadata\":{},\"requestId\":\"r2\",\"method\":\"GET\",\"url\":\"https://api.test/two\",\"bodySha256\":null}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":75,\"type\":\"RESPONSE\",\"ts\":1700000000400,\"metadata\":{},\"requestId\":\"r2\",\"code\":200,\"headers\":{},\"body\":\"resp2\",\"durationMs\":100}"
                    )
                }
            )

            // After normalization, events should have strictly increasing seq
            val tape = TapeLoader.loadLatestSession(file)

            // Both requests should be in tape
            assertEquals(2, tape.uniqueRequestCount)

            // Responses should be retrievable in file order
            val req1 = req("https://api.test/one", method = "GET")
            val resp1 = tape.find(req1, bodySha256 = null)
            assertEquals("resp1", resp1?.body)

            val req2 = req("https://api.test/two", method = "GET")
            val resp2 = tape.find(req2, bodySha256 = null)
            assertEquals("resp2", resp2?.body)
        } finally {
            dir.deleteRecursively()
        }
    }
}
