package com.hellmannratti.zombie.replay

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecordingInterceptorTest {
    @Rule @JvmField val tmp = TemporaryFolder()

    private fun readEvents(file: File): List<Event> {
        val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
        val lines = file.readLines()
        return lines.mapNotNull { runCatching { json.decodeFromString<Event>(it) }.getOrNull() }
    }

    @Test fun `logs request and response in RECORD mode`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello").addHeader("content-type", "text/plain"))
        server.start()
        try {
            val base = tmp.newFolder("rec")
            val recorder = SessionRecorder(baseDir = base, logSessionStart = false)
            val client = OkHttpClient.Builder()
                .addInterceptor(RecordingInterceptor(recorder) { Mode.RECORD })
                .build()

            val request = Request.Builder().url(server.url("/greet")).build()
            val resp = client.newCall(request).execute()
            assertEquals(200, resp.code)
            assertEquals("hello", resp.body?.string())
            assertEquals("text/plain", resp.header("content-type"))

            // Allow background IO to flush
            Thread.sleep(100)

            val eventsFile = File(base, "sessions/events.ndjson")
            assertTrue(eventsFile.exists())
            val evs = readEvents(eventsFile)
            // Expect exactly a Request and Response
            assertEquals(2, evs.size)
            val req = evs[0] as RequestEvent
            val res = evs[1] as ResponseEvent
            assertEquals(req.requestId, res.requestId)
            assertEquals("GET", req.method)
            assertTrue(req.url.contains("/greet"))
            assertEquals(200, res.code)
            assertEquals("hello", res.body)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `does not log in PASSTHROUGH mode`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()
        try {
            val base = tmp.newFolder("passthrough")
            val recorder = SessionRecorder(baseDir = base, logSessionStart = false)
            val client = OkHttpClient.Builder()
                .addInterceptor(RecordingInterceptor(recorder) { Mode.PASSTHROUGH })
                .build()
            val request = Request.Builder().url(server.url("/ping")).build()
            client.newCall(request).execute().use { resp -> assertEquals(204, resp.code) }
            Thread.sleep(50)
            val eventsFile = File(base, "sessions/events.ndjson")
            if (eventsFile.exists()) {
                val lines = eventsFile.readLines()
                // No new events expected
                assertTrue(lines.isEmpty())
            }
        } finally {
            server.shutdown()
        }
    }
}
