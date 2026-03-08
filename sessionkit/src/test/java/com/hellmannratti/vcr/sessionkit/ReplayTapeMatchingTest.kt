package com.hellmannratti.vcr.sessionkit

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayTapeMatchingTest {

    private fun req(url: String, method: String = "GET"): Request {
        val builder = Request.Builder().url(url.toHttpUrl())
        return if (method == "GET" || method == "HEAD") {
            builder.method(method, null).build()
        } else {
            builder.method(method, ByteArray(0).toRequestBody()).build()
        }
    }

    @Test
    fun `find prefers exact body hash match before falling back to null hash bucket`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("POST", "https://api.test/submit", "abc") to listOf(
                    RecordedResponse(200, emptyMap(), "exact", 0)
                ),
                RequestKey("POST", "https://api.test/submit", null) to listOf(
                    RecordedResponse(200, emptyMap(), "fallback", 0)
                )
            )
        )

        val request = req("https://api.test/submit", method = "POST")

        assertEquals("exact", tape.find(request, bodySha256 = "abc")?.body)
        assertEquals("fallback", tape.find(request, bodySha256 = "abc")?.body)
        assertNull(tape.find(request, bodySha256 = "abc"))
    }

    @Test
    fun `find does not match a different method for the same url`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items", null) to listOf(
                    RecordedResponse(200, emptyMap(), "get", 0)
                )
            )
        )

        val request = req("https://api.test/items", method = "POST")

        assertNull(tape.find(request, bodySha256 = null))
    }

    @Test
    fun `find normalizes url patterns before matching`() {
        val tape = ReplayTape(
            map = mapOf(
                RequestKey("GET", "https://api.test/items/{id}", null) to listOf(
                    RecordedResponse(200, emptyMap(), "normalized", 0)
                )
            ),
            patterns = listOf(UrlPattern.fromRetrofitStyle("https://api.test/items/{id}"))
        )

        val request = req("https://api.test/items/42")

        assertEquals("normalized", tape.find(request, bodySha256 = null)?.body)
    }
}
