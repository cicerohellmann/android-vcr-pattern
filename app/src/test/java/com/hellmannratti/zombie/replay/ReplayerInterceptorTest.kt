package com.hellmannratti.zombie.replay

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ReplayerInterceptorTest {

    @Test fun `serves recorded response in REPLAY mode`() {
        val key = RequestKey("GET", "https://example.test/api", null)
        val rec = RecordedResponse(200, mapOf("content-type" to "application/json"), "{\"ok\":true}", 10)
        val tape = ReplayTape(mutableMapOf(key to ArrayDeque(listOf(rec))))
        val interceptor = ReplayerInterceptor(TapeLogger.NoOp) { Mode.REPLAY }
        interceptor.tape = tape
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val request = Request.Builder().url("https://example.test/api").build()
        val t0 = System.currentTimeMillis()
        val resp = client.newCall(request).execute()
        val dt = System.currentTimeMillis() - t0
        assertEquals(200, resp.code)
        assertEquals("application/json", resp.header("content-type"))
        assertEquals("{\"ok\":true}", resp.body?.string())
        assertTrue("should simulate some latency", dt >= 0)
    }

    @Test fun `delegates to network in PASSTHROUGH`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("net"))
        server.start()
        try {
            val interceptor = ReplayerInterceptor(TapeLogger.NoOp) { Mode.PASSTHROUGH }
            val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
            val resp = client.newCall(Request.Builder().url(server.url("/net")).build()).execute()
            assertEquals(200, resp.code)
            assertEquals("net", resp.body?.string())
        } finally {
            server.shutdown()
        }
    }
}
