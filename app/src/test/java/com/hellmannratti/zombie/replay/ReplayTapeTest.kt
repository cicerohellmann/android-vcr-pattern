package com.hellmannratti.zombie.replay

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class ReplayTapeTest {

    private fun req(url: String, method: String = "GET", bodySha: String? = null): Pair<Request, String?> {
        val builder = Request.Builder().url(url.toHttpUrl())
        val req = if (method == "GET" || method == "HEAD") {
            builder.method(method, null).build()
        } else {
            val empty = okhttp3.RequestBody.create(null, ByteArray(0))
            builder.method(method, empty).build()
        }
        return req to bodySha
    }

    @Test fun `exact hash match then FIFO`() {
        val key = RequestKey("GET", "https://api.test/items", "abc")
        val map = mutableMapOf(
            key to ArrayDeque(listOf(
                RecordedResponse(200, mapOf("content-type" to "application/json"), "first", 1),
                RecordedResponse(200, mapOf("content-type" to "application/json"), "second", 1)
            ))
        )
        val tape = ReplayTape(map)

        val (r, sha) = req("https://api.test/items", bodySha = "abc")
        val m1 = tape.find(r, sha)
        val m2 = tape.find(r, sha)
        val m3 = tape.find(r, sha)
        assertEquals("first", m1?.body)
        assertEquals("second", m2?.body)
        assertNull(m3)
    }

    @Test fun `fallback to null-hash bucket`() {
        val keyNull = RequestKey("POST", "https://api.test/submit", null)
        val map = mutableMapOf(
            keyNull to ArrayDeque(listOf(
                RecordedResponse(201, mapOf(), "created", 0)
            ))
        )
        val tape = ReplayTape(map)
        val (r, sha) = req("https://api.test/submit", method = "POST", bodySha = "deadbeef")
        val m = tape.find(r, sha)
        assertNotNull(m)
        assertEquals(201, m!!.code)
        assertEquals("created", m.body)
    }
}
