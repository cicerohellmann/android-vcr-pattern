package com.hellmannratti.vcr.sessionkit

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * AC-0.1: Characterization tests for request matching by method + URL + optional body hash
 *
 * These tests capture the current request matching behavior:
 * - HTTP requests are matched by method + URL + optional body hash
 * - Different methods for the same URL do not match
 * - Different URLs for the same method do not match
 * - Body hash is considered when present; requests without body hashes fall back to null bucket
 * - URL normalization through patterns is applied before matching
 */
class RequestMatchingCharacterizationTest {

    private fun req(url: String, method: String = "GET", body: ByteArray = byteArrayOf()): Request {
        val builder = Request.Builder().url(url.toHttpUrl())
        return if (method == "GET" || method == "HEAD") {
            builder.method(method, null).build()
        } else {
            builder.method(method, body.toRequestBody()).build()
        }
    }

    // ============================================================================
    // Tests for matching by HTTP method
    // ============================================================================

    @Test
    fun `request matching by method - GET request matches GET in tape`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "GET response", 10)
                )
            )
        )

        val request = req("https://api.test/items", method = "GET")
        val response = tape.find(request, bodySha256 = null)

        assertNotNull(response)
        assertEquals("GET response", response?.body)
        assertEquals(200, response?.code)
    }

    @Test
    fun `request matching by method - POST request does not match GET in tape`() {
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
    fun `request matching by method - PUT request does not match POST in tape`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/items", null) to listOf(
                    RecordedResponse(201, emptyMap(), "POST response", 20)
                )
            )
        )

        val request = req("https://api.test/items", method = "PUT")
        val response = tape.find(request, bodySha256 = null)

        assertNull(response)
    }

    @Test
    fun `request matching by method - DELETE request does not match POST in tape`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/items/42", null) to listOf(
                    RecordedResponse(201, emptyMap(), "POST response", 20)
                )
            )
        )

        val request = req("https://api.test/items/42", method = "DELETE")
        val response = tape.find(request, bodySha256 = null)

        assertNull(response)
    }

    @Test
    fun `request matching by method - HEAD request does not match GET in tape`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/status", null) to listOf(
                    RecordedResponse(200, emptyMap(), "", 5)
                )
            )
        )

        val request = req("https://api.test/status", method = "HEAD")
        val response = tape.find(request, bodySha256 = null)

        assertNull(response)
    }

    // ============================================================================
    // Tests for matching by URL
    // ============================================================================

    @Test
    fun `request matching by URL - exact URL match works`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "items list", 10)
                )
            )
        )

        val request = req("https://api.test/items", method = "GET")
        val response = tape.find(request, bodySha256 = null)

        assertNotNull(response)
        assertEquals("items list", response?.body)
    }

    @Test
    fun `request matching by URL - different URL does not match same method`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "items", 10)
                )
            )
        )

        val request = req("https://api.test/users", method = "GET")
        val response = tape.find(request, bodySha256 = null)

        assertNull(response)
    }

    @Test
    fun `request matching by URL - different domain does not match`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api1.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "api1 response", 10)
                )
            )
        )

        val request = req("https://api2.test/items", method = "GET")
        val response = tape.find(request, bodySha256 = null)

        assertNull(response)
    }

    @Test
    fun `request matching by URL - different protocol does not match`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "https response", 10)
                )
            )
        )

        val request = req("http://api.test/items", method = "GET")
        val response = tape.find(request, bodySha256 = null)

        assertNull(response)
    }

    @Test
    fun `request matching by URL - URL with query parameters matches exactly`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items?limit=10", null) to listOf(
                    RecordedResponse(200, emptyMap(), "limited list", 10)
                )
            )
        )

        val request = req("https://api.test/items?limit=10", method = "GET")
        val response = tape.find(request, bodySha256 = null)

        assertNotNull(response)
        assertEquals("limited list", response?.body)
    }

    @Test
    fun `request matching by URL - different query parameters do not match`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items?limit=10", null) to listOf(
                    RecordedResponse(200, emptyMap(), "limited list", 10)
                )
            )
        )

        val request = req("https://api.test/items?limit=20", method = "GET")
        val response = tape.find(request, bodySha256 = null)

        assertNull(response)
    }

    // ============================================================================
    // Tests for matching with body hash (POST/PUT requests)
    // ============================================================================

    @Test
    fun `request matching with body hash - exact body hash match works`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/submit", "bodyhash123") to listOf(
                    RecordedResponse(201, emptyMap(), "created", 20)
                )
            )
        )

        val request = req("https://api.test/submit", method = "POST", body = byteArrayOf(1, 2, 3))
        val response = tape.find(request, bodySha256 = "bodyhash123")

        assertNotNull(response)
        assertEquals("created", response?.body)
        assertEquals(201, response?.code)
    }

    @Test
    fun `request matching with body hash - different body hash does not match`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/submit", "bodyhash123") to listOf(
                    RecordedResponse(201, emptyMap(), "created", 20)
                )
            )
        )

        val request = req("https://api.test/submit", method = "POST", body = byteArrayOf(1, 2, 3))
        val response = tape.find(request, bodySha256 = "differenthash")

        assertNull(response)
    }

    @Test
    fun `request matching with body hash - null body hash does not match specific hash bucket`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/submit", "bodyhash123") to listOf(
                    RecordedResponse(201, emptyMap(), "specific", 20)
                )
            )
        )

        val request = req("https://api.test/submit", method = "POST")
        val response = tape.find(request, bodySha256 = null)

        assertNull(response)
    }

    @Test
    fun `request matching with body hash - prefers exact hash match before null hash fallback`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/submit", "abc") to listOf(
                    RecordedResponse(201, emptyMap(), "exact", 20)
                ),
                RequestKey("POST", "https://api.test/submit", null) to listOf(
                    RecordedResponse(201, emptyMap(), "fallback", 20)
                )
            )
        )

        val request = req("https://api.test/submit", method = "POST")
        val response = tape.find(request, bodySha256 = "abc")

        assertNotNull(response)
        assertEquals("exact", response?.body)
    }

    @Test
    fun `request matching with body hash - falls back to null hash bucket when exact match not found`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/submit", "abc") to listOf(
                    RecordedResponse(201, emptyMap(), "exact", 20)
                ),
                RequestKey("POST", "https://api.test/submit", null) to listOf(
                    RecordedResponse(201, emptyMap(), "fallback", 20)
                )
            )
        )

        val request = req("https://api.test/submit", method = "POST")
        val response = tape.find(request, bodySha256 = "xyz")

        assertNotNull(response)
        assertEquals("fallback", response?.body)
    }

    @Test
    fun `request matching with body hash - falls back to null hash bucket when exact bucket exhausted`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/submit", "abc") to listOf(
                    RecordedResponse(201, emptyMap(), "exact", 20)
                ),
                RequestKey("POST", "https://api.test/submit", null) to listOf(
                    RecordedResponse(201, emptyMap(), "fallback", 20)
                )
            )
        )

        val request = req("https://api.test/submit", method = "POST")
        val response = tape.find(request, bodySha256 = "abc")

        assertNotNull(response)
        assertEquals("exact", response?.body)

        // When exact bucket is exhausted, falls back to the null hash bucket
        val response2 = tape.find(request, bodySha256 = "abc")

        assertNotNull(response2)
        assertEquals("fallback", response2?.body)
    }

    // ============================================================================
    // Tests for composite matching (method + URL + body hash)
    // ============================================================================

    @Test
    fun `composite matching - POST to one URL does not match POST to different URL`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/items", "hash1") to listOf(
                    RecordedResponse(201, emptyMap(), "items create", 20)
                )
            )
        )

        val request = req("https://api.test/users", method = "POST")
        val response = tape.find(request, bodySha256 = "hash1")

        assertNull(response)
    }

    @Test
    fun `composite matching - POST to URL with one hash does not match same URL with different hash`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/items", "hash1") to listOf(
                    RecordedResponse(201, emptyMap(), "create with hash1", 20)
                )
            )
        )

        val request = req("https://api.test/items", method = "POST")
        val response = tape.find(request, bodySha256 = "hash2")

        assertNull(response)
    }

    @Test
    fun `composite matching - multiple responses for same request key are returned sequentially`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/items", "hash1") to listOf(
                    RecordedResponse(201, emptyMap(), "first create", 20),
                    RecordedResponse(201, emptyMap(), "second create", 22),
                    RecordedResponse(201, emptyMap(), "third create", 21)
                )
            )
        )

        val request = req("https://api.test/items", method = "POST")

        val response1 = tape.find(request, bodySha256 = "hash1")
        assertEquals("first create", response1?.body)

        val response2 = tape.find(request, bodySha256 = "hash1")
        assertEquals("second create", response2?.body)

        val response3 = tape.find(request, bodySha256 = "hash1")
        assertEquals("third create", response3?.body)

        val response4 = tape.find(request, bodySha256 = "hash1")
        assertNull(response4)
    }

    // ============================================================================
    // Tests for non-matching requests
    // ============================================================================

    @Test
    fun `non-matching request - completely different method URL and hash does not match`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/items", "hash1") to listOf(
                    RecordedResponse(201, emptyMap(), "create", 20)
                )
            )
        )

        val request = req("https://api.test/users", method = "DELETE")
        val response = tape.find(request, bodySha256 = "hash2")

        assertNull(response)
    }

    @Test
    fun `non-matching request - empty tape never matches`() {
        val tape = ReplayTape(map = emptyMap())

        val request = req("https://api.test/items", method = "GET")
        val response = tape.find(request, bodySha256 = null)

        assertNull(response)
    }

    // ============================================================================
    // Tests for URL normalization
    // ============================================================================

    @Test
    fun `URL normalization - normalized URL matches request with concrete parameters`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items/{id}", null) to listOf(
                    RecordedResponse(200, emptyMap(), "item detail", 10)
                )
            ),
            patterns = listOf(UrlPattern.fromRetrofitStyle("https://api.test/items/{id}"))
        )

        val request = req("https://api.test/items/42", method = "GET")
        val response = tape.find(request, bodySha256 = null)

        assertNotNull(response)
        assertEquals("item detail", response?.body)
    }

    @Test
    fun `URL normalization - different ID values with same pattern normalize to same bucket`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items/{id}", null) to listOf(
                    RecordedResponse(200, emptyMap(), "item 1", 10),
                    RecordedResponse(200, emptyMap(), "item 2", 10)
                )
            ),
            patterns = listOf(UrlPattern.fromRetrofitStyle("https://api.test/items/{id}"))
        )

        val request1 = req("https://api.test/items/42", method = "GET")
        val response1 = tape.find(request1, bodySha256 = null)
        assertEquals("item 1", response1?.body)

        val request2 = req("https://api.test/items/99", method = "GET")
        val response2 = tape.find(request2, bodySha256 = null)
        assertEquals("item 2", response2?.body)
    }

    @Test
    fun `URL normalization - different patterns apply independently`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items/{id}", null) to listOf(
                    RecordedResponse(200, emptyMap(), "items pattern", 10)
                ),
                RequestKey("GET", "https://api.test/users/{userId}/profile", null) to listOf(
                    RecordedResponse(200, emptyMap(), "users pattern", 10)
                )
            ),
            patterns = listOf(
                UrlPattern.fromRetrofitStyle("https://api.test/items/{id}"),
                UrlPattern.fromRetrofitStyle("https://api.test/users/{userId}/profile")
            )
        )

        val itemRequest = req("https://api.test/items/42", method = "GET")
        val itemResponse = tape.find(itemRequest, bodySha256 = null)
        assertEquals("items pattern", itemResponse?.body)

        tape.resetCursors()

        val userRequest = req("https://api.test/users/99/profile", method = "GET")
        val userResponse = tape.find(userRequest, bodySha256 = null)
        assertEquals("users pattern", userResponse?.body)
    }
}
