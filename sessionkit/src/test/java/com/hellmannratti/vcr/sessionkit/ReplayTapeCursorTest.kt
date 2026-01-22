package com.hellmannratti.vcr.sessionkit

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayTapeCursorTest {

    private fun req(url: String, method: String = "GET"): Request {
        return Request.Builder()
            .url(url.toHttpUrl())
            .method(method, null)
            .build()
    }

    @Test
    fun `find is non-destructive and resettable`() {
        val key = RequestKey("GET", "https://api.test/items", null)
        val tape = ReplayTape(
            map = mapOf(
                key to listOf(
                    RecordedResponse(200, emptyMap(), "first", 0),
                    RecordedResponse(200, emptyMap(), "second", 0)
                )
            )
        )

        val r = req("https://api.test/items")

        assertEquals("first", tape.find(r, bodySha256 = null)?.body)
        assertEquals("second", tape.find(r, bodySha256 = null)?.body)
        assertNull(tape.find(r, bodySha256 = null))

        tape.resetCursors()

        assertEquals("first", tape.find(r, bodySha256 = null)?.body)
        assertEquals("second", tape.find(r, bodySha256 = null)?.body)
        assertNull(tape.find(r, bodySha256 = null))
    }
}
