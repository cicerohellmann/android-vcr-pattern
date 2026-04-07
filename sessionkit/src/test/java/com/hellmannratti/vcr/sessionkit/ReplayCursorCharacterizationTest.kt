package com.hellmannratti.vcr.sessionkit

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * AC-0.2: Characterization tests for replay cursor behavior (sequential progression through repeated requests)
 *
 * These tests capture the current replay cursor semantics:
 * - When the same request is recorded multiple times, replaying returns responses in order
 * - After consuming all responses for a request, subsequent replays return null
 * - Cursor advancement is tracked independently per RequestKey (method + URL + bodySha256)
 * - Different requests maintain independent cursor positions
 * - Cursor reset clears all tracking and allows re-replay from the beginning
 * - Mixed request types (different methods, URLs, or hashes) each have independent cursors
 */
class ReplayCursorCharacterizationTest {

    private fun req(url: String, method: String = "GET", body: ByteArray = byteArrayOf()): Request {
        val builder = Request.Builder().url(url.toHttpUrl())
        return if (method == "GET" || method == "HEAD") {
            builder.method(method, null).build()
        } else {
            builder.method(method, body.toRequestBody()).build()
        }
    }

    // ============================================================================
    // Tests for sequential progression through repeated requests
    // ============================================================================

    @Test
    fun `sequential progression - first call returns first response`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "response 1", 10)
                )
            )
        )

        val request = req("https://api.test/items", method = "GET")
        val response = tape.find(request, bodySha256 = null)

        assertNotNull(response)
        assertEquals("response 1", response?.body)
    }

    @Test
    fun `sequential progression - multiple responses returned in order`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "first", 10),
                    RecordedResponse(200, emptyMap(), "second", 11),
                    RecordedResponse(200, emptyMap(), "third", 12)
                )
            )
        )

        val request = req("https://api.test/items", method = "GET")

        val response1 = tape.find(request, bodySha256 = null)
        assertEquals("first", response1?.body)

        val response2 = tape.find(request, bodySha256 = null)
        assertEquals("second", response2?.body)

        val response3 = tape.find(request, bodySha256 = null)
        assertEquals("third", response3?.body)
    }

    @Test
    fun `sequential progression - two responses returned in order before exhaustion`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/submit", "hash123") to listOf(
                    RecordedResponse(201, emptyMap(), "created 1", 20),
                    RecordedResponse(201, emptyMap(), "created 2", 21)
                )
            )
        )

        val request = req("https://api.test/submit", method = "POST", body = byteArrayOf(1, 2, 3))

        val response1 = tape.find(request, bodySha256 = "hash123")
        assertEquals("created 1", response1?.body)

        val response2 = tape.find(request, bodySha256 = "hash123")
        assertEquals("created 2", response2?.body)
    }

    @Test
    fun `sequential progression - many responses returned sequentially`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "resp0", 10),
                    RecordedResponse(200, emptyMap(), "resp1", 10),
                    RecordedResponse(200, emptyMap(), "resp2", 10),
                    RecordedResponse(200, emptyMap(), "resp3", 10),
                    RecordedResponse(200, emptyMap(), "resp4", 10)
                )
            )
        )

        val request = req("https://api.test/items", method = "GET")

        for (i in 0..4) {
            val response = tape.find(request, bodySha256 = null)
            assertNotNull(response)
            assertEquals("resp$i", response?.body)
        }
    }

    // ============================================================================
    // Tests for cursor advancement and exhaustion
    // ============================================================================

    @Test
    fun `cursor advancement - after consuming all responses, subsequent calls return null`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "only response", 10)
                )
            )
        )

        val request = req("https://api.test/items", method = "GET")

        val response1 = tape.find(request, bodySha256 = null)
        assertNotNull(response1)
        assertEquals("only response", response1?.body)

        val response2 = tape.find(request, bodySha256 = null)
        assertNull(response2)
    }

    @Test
    fun `cursor advancement - exhaustion happens after last response in list`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/submit", "hash1") to listOf(
                    RecordedResponse(201, emptyMap(), "first", 20),
                    RecordedResponse(201, emptyMap(), "second", 21)
                )
            )
        )

        val request = req("https://api.test/submit", method = "POST")

        val response1 = tape.find(request, bodySha256 = "hash1")
        assertEquals("first", response1?.body)

        val response2 = tape.find(request, bodySha256 = "hash1")
        assertEquals("second", response2?.body)

        val response3 = tape.find(request, bodySha256 = "hash1")
        assertNull(response3)

        val response4 = tape.find(request, bodySha256 = "hash1")
        assertNull(response4)
    }

    @Test
    fun `cursor advancement - returns null consistently after exhaustion`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/single", null) to listOf(
                    RecordedResponse(200, emptyMap(), "one", 10)
                )
            )
        )

        val request = req("https://api.test/single", method = "GET")

        tape.find(request, bodySha256 = null) // Consume the only response

        // Request multiple times after exhaustion
        for (i in 0..4) {
            val response = tape.find(request, bodySha256 = null)
            assertNull(response)
        }
    }

    // ============================================================================
    // Tests for independent cursor tracking per RequestKey
    // ============================================================================

    @Test
    fun `independent cursors - different methods maintain separate cursor positions`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "GET response", 10)
                ),
                RequestKey("POST", "https://api.test/items", null) to listOf(
                    RecordedResponse(201, emptyMap(), "POST response", 20)
                )
            )
        )

        val getRequest = req("https://api.test/items", method = "GET")
        val postRequest = req("https://api.test/items", method = "POST")

        val getResponse = tape.find(getRequest, bodySha256 = null)
        assertEquals("GET response", getResponse?.body)

        val postResponse = tape.find(postRequest, bodySha256 = null)
        assertEquals("POST response", postResponse?.body)

        // Both cursors should now be exhausted
        assertNull(tape.find(getRequest, bodySha256 = null))
        assertNull(tape.find(postRequest, bodySha256 = null))
    }

    @Test
    fun `independent cursors - different URLs maintain separate cursor positions`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "items response", 10)
                ),
                RequestKey("GET", "https://api.test/users", null) to listOf(
                    RecordedResponse(200, emptyMap(), "users response", 10)
                )
            )
        )

        val itemsRequest = req("https://api.test/items", method = "GET")
        val usersRequest = req("https://api.test/users", method = "GET")

        val itemsResponse = tape.find(itemsRequest, bodySha256 = null)
        assertEquals("items response", itemsResponse?.body)

        val usersResponse = tape.find(usersRequest, bodySha256 = null)
        assertEquals("users response", usersResponse?.body)

        // Both cursors should now be exhausted
        assertNull(tape.find(itemsRequest, bodySha256 = null))
        assertNull(tape.find(usersRequest, bodySha256 = null))
    }

    @Test
    fun `independent cursors - same method+URL but different body hash maintain separate cursors`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/submit", "hash1") to listOf(
                    RecordedResponse(201, emptyMap(), "with hash1", 20)
                ),
                RequestKey("POST", "https://api.test/submit", "hash2") to listOf(
                    RecordedResponse(201, emptyMap(), "with hash2", 20)
                )
            )
        )

        val request = req("https://api.test/submit", method = "POST")

        val response1 = tape.find(request, bodySha256 = "hash1")
        assertEquals("with hash1", response1?.body)

        val response2 = tape.find(request, bodySha256 = "hash2")
        assertEquals("with hash2", response2?.body)

        // Both cursors should now be exhausted independently
        assertNull(tape.find(request, bodySha256 = "hash1"))
        assertNull(tape.find(request, bodySha256 = "hash2"))
    }

    @Test
    fun `independent cursors - multiple response lists with different keys advance independently`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "items 1", 10),
                    RecordedResponse(200, emptyMap(), "items 2", 10)
                ),
                RequestKey("GET", "https://api.test/users", null) to listOf(
                    RecordedResponse(200, emptyMap(), "users 1", 10),
                    RecordedResponse(200, emptyMap(), "users 2", 10),
                    RecordedResponse(200, emptyMap(), "users 3", 10)
                )
            )
        )

        val itemsRequest = req("https://api.test/items", method = "GET")
        val usersRequest = req("https://api.test/users", method = "GET")

        // Consume first item response
        assertEquals("items 1", tape.find(itemsRequest, bodySha256 = null)?.body)

        // Consume first user response
        assertEquals("users 1", tape.find(usersRequest, bodySha256 = null)?.body)

        // Consume second user response
        assertEquals("users 2", tape.find(usersRequest, bodySha256 = null)?.body)

        // Consume second item response
        assertEquals("items 2", tape.find(itemsRequest, bodySha256 = null)?.body)

        // Consume third user response
        assertEquals("users 3", tape.find(usersRequest, bodySha256 = null)?.body)

        // Both should now be exhausted
        assertNull(tape.find(itemsRequest, bodySha256 = null))
        assertNull(tape.find(usersRequest, bodySha256 = null))
    }

    // ============================================================================
    // Tests for cursor reset behavior
    // ============================================================================

    @Test
    fun `cursor reset - allows re-replay from the beginning`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "response 1", 10),
                    RecordedResponse(200, emptyMap(), "response 2", 11)
                )
            )
        )

        val request = req("https://api.test/items", method = "GET")

        // First pass: consume all responses
        assertEquals("response 1", tape.find(request, bodySha256 = null)?.body)
        assertEquals("response 2", tape.find(request, bodySha256 = null)?.body)
        assertNull(tape.find(request, bodySha256 = null))

        // Reset cursors
        tape.resetCursors()

        // Second pass: should be able to replay from the beginning
        assertEquals("response 1", tape.find(request, bodySha256 = null)?.body)
        assertEquals("response 2", tape.find(request, bodySha256 = null)?.body)
        assertNull(tape.find(request, bodySha256 = null))
    }

    @Test
    fun `cursor reset - resets all independent cursors`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "items", 10)
                ),
                RequestKey("GET", "https://api.test/users", null) to listOf(
                    RecordedResponse(200, emptyMap(), "users", 10)
                )
            )
        )

        val itemsRequest = req("https://api.test/items", method = "GET")
        val usersRequest = req("https://api.test/users", method = "GET")

        // Consume both responses
        tape.find(itemsRequest, bodySha256 = null)
        tape.find(usersRequest, bodySha256 = null)

        // Both should be exhausted
        assertNull(tape.find(itemsRequest, bodySha256 = null))
        assertNull(tape.find(usersRequest, bodySha256 = null))

        // Reset cursors
        tape.resetCursors()

        // Both should be replayable again
        assertNotNull(tape.find(itemsRequest, bodySha256 = null))
        assertNotNull(tape.find(usersRequest, bodySha256 = null))
    }

    @Test
    fun `cursor reset - clears tracking for single-response requests`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/single", null) to listOf(
                    RecordedResponse(200, emptyMap(), "single response", 10)
                )
            )
        )

        val request = req("https://api.test/single", method = "GET")

        // First consumption
        val response1 = tape.find(request, bodySha256 = null)
        assertEquals("single response", response1?.body)

        // Should be exhausted now
        assertNull(tape.find(request, bodySha256 = null))

        // Reset
        tape.resetCursors()

        // Should be replayable again
        val response2 = tape.find(request, bodySha256 = null)
        assertEquals("single response", response2?.body)

        // Should be exhausted again
        assertNull(tape.find(request, bodySha256 = null))
    }

    @Test
    fun `cursor reset - allows multiple reset/replay cycles`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "response", 10)
                )
            )
        )

        val request = req("https://api.test/items", method = "GET")

        // First cycle
        assertEquals("response", tape.find(request, bodySha256 = null)?.body)
        assertNull(tape.find(request, bodySha256 = null))

        // Reset and second cycle
        tape.resetCursors()
        assertEquals("response", tape.find(request, bodySha256 = null)?.body)
        assertNull(tape.find(request, bodySha256 = null))

        // Reset and third cycle
        tape.resetCursors()
        assertEquals("response", tape.find(request, bodySha256 = null)?.body)
        assertNull(tape.find(request, bodySha256 = null))
    }

    // ============================================================================
    // Tests for mixed request cursor tracking
    // ============================================================================

    @Test
    fun `mixed request cursors - interleaved access maintains independent positions`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/a", null) to listOf(
                    RecordedResponse(200, emptyMap(), "a1", 10),
                    RecordedResponse(200, emptyMap(), "a2", 10),
                    RecordedResponse(200, emptyMap(), "a3", 10)
                ),
                RequestKey("GET", "https://api.test/b", null) to listOf(
                    RecordedResponse(200, emptyMap(), "b1", 10),
                    RecordedResponse(200, emptyMap(), "b2", 10)
                )
            )
        )

        val requestA = req("https://api.test/a", method = "GET")
        val requestB = req("https://api.test/b", method = "GET")

        // Interleaved access
        assertEquals("a1", tape.find(requestA, bodySha256 = null)?.body)
        assertEquals("b1", tape.find(requestB, bodySha256 = null)?.body)
        assertEquals("a2", tape.find(requestA, bodySha256 = null)?.body)
        assertEquals("b2", tape.find(requestB, bodySha256 = null)?.body)
        assertEquals("a3", tape.find(requestA, bodySha256 = null)?.body)

        // A should be exhausted
        assertNull(tape.find(requestA, bodySha256 = null))

        // B should still be exhausted
        assertNull(tape.find(requestB, bodySha256 = null))
    }

    @Test
    fun `mixed request cursors - three independent request types each maintain state`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "items", 10),
                    RecordedResponse(200, emptyMap(), "items again", 10)
                ),
                RequestKey("POST", "https://api.test/items", "hash1") to listOf(
                    RecordedResponse(201, emptyMap(), "created", 20)
                ),
                RequestKey("DELETE", "https://api.test/items/42", null) to listOf(
                    RecordedResponse(204, emptyMap(), "", 15)
                )
            )
        )

        val getRequest = req("https://api.test/items", method = "GET")
        val postRequest = req("https://api.test/items", method = "POST")
        val deleteRequest = req("https://api.test/items/42", method = "DELETE")

        // Access each in different order
        assertEquals("items", tape.find(getRequest, bodySha256 = null)?.body)
        assertEquals("created", tape.find(postRequest, bodySha256 = "hash1")?.body)
        assertEquals("items again", tape.find(getRequest, bodySha256 = null)?.body)
        assertEquals("", tape.find(deleteRequest, bodySha256 = null)?.body)

        // All should now be exhausted
        assertNull(tape.find(getRequest, bodySha256 = null))
        assertNull(tape.find(postRequest, bodySha256 = "hash1"))
        assertNull(tape.find(deleteRequest, bodySha256 = null))
    }

    @Test
    fun `mixed request cursors - body hash fallback maintains separate cursor from exact match`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/submit", "exact") to listOf(
                    RecordedResponse(201, emptyMap(), "exact match", 20)
                ),
                RequestKey("POST", "https://api.test/submit", null) to listOf(
                    RecordedResponse(201, emptyMap(), "fallback 1", 20),
                    RecordedResponse(201, emptyMap(), "fallback 2", 20)
                )
            )
        )

        val request = req("https://api.test/submit", method = "POST")

        // First call with exact hash
        assertEquals("exact match", tape.find(request, bodySha256 = "exact")?.body)

        // Call with non-matching hash falls back to null
        assertEquals("fallback 1", tape.find(request, bodySha256 = "other")?.body)
        assertEquals("fallback 2", tape.find(request, bodySha256 = "other")?.body)

        // Exhausted fallback
        assertNull(tape.find(request, bodySha256 = "other")?.body)

        // Exact should still be exhausted
        assertNull(tape.find(request, bodySha256 = "exact")?.body)
    }

    @Test
    fun `mixed request cursors - after reset, all cursors restart independently`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "items1", 10),
                    RecordedResponse(200, emptyMap(), "items2", 10)
                ),
                RequestKey("GET", "https://api.test/users", null) to listOf(
                    RecordedResponse(200, emptyMap(), "users1", 10)
                )
            )
        )

        val itemsRequest = req("https://api.test/items", method = "GET")
        val usersRequest = req("https://api.test/users", method = "GET")

        // First pass: consume items partially, users completely
        assertEquals("items1", tape.find(itemsRequest, bodySha256 = null)?.body)
        assertEquals("users1", tape.find(usersRequest, bodySha256 = null)?.body)

        // Reset
        tape.resetCursors()

        // Second pass: should both restart from beginning
        assertEquals("items1", tape.find(itemsRequest, bodySha256 = null)?.body)
        assertEquals("items2", tape.find(itemsRequest, bodySha256 = null)?.body)
        assertEquals("users1", tape.find(usersRequest, bodySha256 = null)?.body)

        // Both should now be exhausted
        assertNull(tape.find(itemsRequest, bodySha256 = null))
        assertNull(tape.find(usersRequest, bodySha256 = null))
    }
}
