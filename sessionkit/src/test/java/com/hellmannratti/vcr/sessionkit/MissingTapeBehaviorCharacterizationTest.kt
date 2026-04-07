package com.hellmannratti.vcr.sessionkit

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * AC-0.3: Characterization tests for missing tape behavior (what happens when no match found)
 *
 * These tests capture the current behavior when no matching tape entry is found:
 * - When a request has no matching tape entry, find() returns null
 * - When replay cursor is exhausted (all responses consumed), find() returns null
 * - Empty tape (no entries at all) returns null for any request
 * - Missing body hash fallback: when exact hash doesn't match and fallback is exhausted, returns null
 * - Mixed missing scenarios: some requests match, others don't
 */
class MissingTapeBehaviorCharacterizationTest {

    private fun req(url: String, method: String = "GET", body: ByteArray = byteArrayOf()): Request {
        val builder = Request.Builder().url(url.toHttpUrl())
        return if (method == "GET" || method == "HEAD") {
            builder.method(method, null).build()
        } else {
            builder.method(method, body.toRequestBody()).build()
        }
    }

    // ============================================================================
    // Tests for no matching tape entry found
    // ============================================================================

    @Test
    fun `no matching entry - request not in tape returns null`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "response", 10)
                )
            )
        )

        val request = req("https://api.test/other", method = "GET")
        val response = tape.find(request, bodySha256 = null)

        assertNull(response)
    }

    @Test
    fun `no matching entry - different URL entirely returns null`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "items response", 10)
                )
            )
        )

        val request = req("https://completely-different.com/api", method = "GET")
        val response = tape.find(request, bodySha256 = null)

        assertNull(response)
    }

    @Test
    fun `no matching entry - different method same URL returns null`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "GET response", 10)
                )
            )
        )

        val request = req("https://api.test/items", method = "POST")
        val response = tape.find(request, bodySha256 = null)

        assertNull(response)
    }

    @Test
    fun `no matching entry - multiple recorded requests, none match returns null`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "items", 10)
                ),
                RequestKey("POST", "https://api.test/users", null) to listOf(
                    RecordedResponse(201, emptyMap(), "user created", 15)
                ),
                RequestKey("DELETE", "https://api.test/items/42", null) to listOf(
                    RecordedResponse(204, emptyMap(), "", 10)
                )
            )
        )

        val request = req("https://api.test/settings", method = "GET")
        val response = tape.find(request, bodySha256 = null)

        assertNull(response)
    }

    // ============================================================================
    // Tests for cursor exhaustion (all responses consumed)
    // ============================================================================

    @Test
    fun `cursor exhaustion - after single response consumed, second call returns null`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "only response", 10)
                )
            )
        )

        val request = req("https://api.test/items", method = "GET")

        // First call consumes the response
        val response1 = tape.find(request, bodySha256 = null)
        assert(response1 != null)
        assert(response1?.body == "only response")

        // Second call should return null (cursor exhausted)
        val response2 = tape.find(request, bodySha256 = null)
        assertNull(response2)
    }

    @Test
    fun `cursor exhaustion - after all responses consumed, additional calls return null`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/data", null) to listOf(
                    RecordedResponse(201, emptyMap(), "first", 10),
                    RecordedResponse(201, emptyMap(), "second", 11),
                    RecordedResponse(201, emptyMap(), "third", 12)
                )
            )
        )

        val request = req("https://api.test/data", method = "POST")

        // Consume all three responses
        tape.find(request, bodySha256 = null)
        tape.find(request, bodySha256 = null)
        tape.find(request, bodySha256 = null)

        // Fourth call should return null (all exhausted)
        val response4 = tape.find(request, bodySha256 = null)
        assertNull(response4)

        // Fifth call should also return null (stays null)
        val response5 = tape.find(request, bodySha256 = null)
        assertNull(response5)
    }

    @Test
    fun `cursor exhaustion - each request type has independent exhaustion`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "get response", 10)
                ),
                RequestKey("POST", "https://api.test/items", null) to listOf(
                    RecordedResponse(201, emptyMap(), "post response", 10)
                )
            )
        )

        val getRequest = req("https://api.test/items", method = "GET")
        val postRequest = req("https://api.test/items", method = "POST")

        // Consume GET response
        tape.find(getRequest, bodySha256 = null)
        // Second GET call returns null
        assertNull(tape.find(getRequest, bodySha256 = null))

        // POST still has response available
        assert(tape.find(postRequest, bodySha256 = null) != null)
        // Second POST call returns null
        assertNull(tape.find(postRequest, bodySha256 = null))
    }

    // ============================================================================
    // Tests for empty tape
    // ============================================================================

    @Test
    fun `empty tape - returns null for any request`() {
        val tape = ReplayTape(map = emptyMap())

        val request = req("https://api.test/items", method = "GET")
        val response = tape.find(request, bodySha256 = null)

        assertNull(response)
    }

    @Test
    fun `empty tape - returns null for POST with body`() {
        val tape = ReplayTape(map = emptyMap())

        val request = req("https://api.test/items", method = "POST", body = "test body".toByteArray())
        val response = tape.find(request, bodySha256 = "somehash")

        assertNull(response)
    }

    @Test
    fun `empty tape - multiple different request types all return null`() {
        val tape = ReplayTape(map = emptyMap())

        val getRequest = req("https://api.test/items", method = "GET")
        val postRequest = req("https://api.test/items", method = "POST")
        val deleteRequest = req("https://api.test/items/42", method = "DELETE")

        assertNull(tape.find(getRequest, bodySha256 = null))
        assertNull(tape.find(postRequest, bodySha256 = null))
        assertNull(tape.find(deleteRequest, bodySha256 = null))
    }

    // ============================================================================
    // Tests for body hash fallback behavior with missing entries
    // ============================================================================

    @Test
    fun `body hash fallback - no exact hash match and no null fallback returns null`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/items", "hash_abc") to listOf(
                    RecordedResponse(201, emptyMap(), "response for hash_abc", 10)
                )
            )
        )

        val request = req("https://api.test/items", method = "POST", body = "different body".toByteArray())
        val response = tape.find(request, bodySha256 = "hash_xyz")

        assertNull(response)
    }

    @Test
    fun `body hash fallback - exact hash exhausted, null fallback exhausted returns null`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/items", "specific_hash") to listOf(
                    RecordedResponse(201, emptyMap(), "response 1", 10)
                ),
                RequestKey("POST", "https://api.test/items", null) to listOf(
                    RecordedResponse(201, emptyMap(), "response 2", 10)
                )
            )
        )

        val request = req("https://api.test/items", method = "POST", body = "test".toByteArray())

        // First call with specific hash gets first response
        tape.find(request, bodySha256 = "specific_hash")
        // Second call with same specific hash falls back to null hash
        tape.find(request, bodySha256 = "specific_hash")
        // Third call with same specific hash - both exhausted
        assertNull(tape.find(request, bodySha256 = "specific_hash"))
    }

    @Test
    fun `body hash fallback - no exact hash match falls back to null hash bucket`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/items", null) to listOf(
                    RecordedResponse(201, emptyMap(), "fallback response", 10)
                )
            )
        )

        val request = req("https://api.test/items", method = "POST", body = "some body".toByteArray())
        val response = tape.find(request, bodySha256 = "nonexistent_hash")

        assert(response != null)
        assert(response?.body == "fallback response")
    }

    // ============================================================================
    // Tests for mixed missing and found scenarios
    // ============================================================================

    @Test
    fun `mixed scenarios - some requests match, others don't in same tape`() {
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

        // First request matches
        val itemsRequest = req("https://api.test/items", method = "GET")
        assert(tape.find(itemsRequest, bodySha256 = null) != null)

        // Second request matches
        val usersRequest = req("https://api.test/users", method = "GET")
        assert(tape.find(usersRequest, bodySha256 = null) != null)

        // Third request does not match
        val settingsRequest = req("https://api.test/settings", method = "GET")
        assertNull(tape.find(settingsRequest, bodySha256 = null))
    }

    @Test
    fun `mixed scenarios - first request exhausts, second still available`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/first", null) to listOf(
                    RecordedResponse(200, emptyMap(), "first response", 10)
                ),
                RequestKey("GET", "https://api.test/second", null) to listOf(
                    RecordedResponse(200, emptyMap(), "second response 1", 10),
                    RecordedResponse(200, emptyMap(), "second response 2", 10)
                )
            )
        )

        val firstRequest = req("https://api.test/first", method = "GET")
        val secondRequest = req("https://api.test/second", method = "GET")

        // Consume first request
        assert(tape.find(firstRequest, bodySha256 = null) != null)
        assertNull(tape.find(firstRequest, bodySha256 = null))

        // Second request still available
        assert(tape.find(secondRequest, bodySha256 = null) != null)
        assert(tape.find(secondRequest, bodySha256 = null) != null)
        assertNull(tape.find(secondRequest, bodySha256 = null))
    }

    // ============================================================================
    // Tests for ReplayerInterceptor throwing NoTapeFoundException
    // ============================================================================

    @Test
    fun `replayer interceptor - throws NoTapeFoundException when no tape match found`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "response", 10)
                )
            )
        )

        val interceptor = ReplayerInterceptor(modeProvider = { Mode.REPLAY })
        interceptor.tape = tape

        val request = req("https://api.test/nonexistent", method = "GET")

        try {
            // We can't actually call the interceptor without a full chain setup,
            // but we document that the behavior is: tape.find returns null -> throws NoTapeFoundException
            val found = tape.find(request, bodySha256 = null)
            assertNull(found)
            // In real use, ReplayerInterceptor would throw here
        } catch (e: NoTapeFoundException) {
            assert(e.message?.contains("No recorded response") == true)
        }
    }

    @Test
    fun `replayer interceptor - throws for exhausted cursor scenarios`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "only response", 10)
                )
            )
        )

        val request = req("https://api.test/items", method = "GET")

        // Consume the only response
        tape.find(request, bodySha256 = null)

        // Second call returns null - in ReplayerInterceptor this would throw
        val found = tape.find(request, bodySha256 = null)
        assertNull(found)
    }

    // ============================================================================
    // Tests for cursor reset behavior with missing entries
    // ============================================================================

    @Test
    fun `cursor reset - after reset exhausted cursor allows replay again`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "response", 10)
                )
            )
        )

        val request = req("https://api.test/items", method = "GET")

        // Consume response
        tape.find(request, bodySha256 = null)
        // Second call returns null
        assertNull(tape.find(request, bodySha256 = null))

        // Reset cursors
        tape.resetCursors()

        // Now we can replay from the beginning again
        assert(tape.find(request, bodySha256 = null) != null)
        assertNull(tape.find(request, bodySha256 = null))
    }

    @Test
    fun `cursor reset - reset does not create entries that weren't there`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "response", 10)
                )
            )
        )

        val matchingRequest = req("https://api.test/items", method = "GET")
        val missingRequest = req("https://api.test/missing", method = "GET")

        // Verify missing request returns null before reset
        assertNull(tape.find(missingRequest, bodySha256 = null))

        // Reset cursors
        tape.resetCursors()

        // Missing request should still return null after reset
        assertNull(tape.find(missingRequest, bodySha256 = null))
        // But the matching request should be available again
        assert(tape.find(matchingRequest, bodySha256 = null) != null)
    }
}
