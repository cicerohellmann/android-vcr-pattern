package com.hellmannratti.cassete.okhttp

import com.hellmannratti.cassete.core.Cassete
import com.hellmannratti.cassete.core.CasseteConfig
import com.hellmannratti.cassete.core.Mode
import com.hellmannratti.cassete.core.NoTapeFoundException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CasseteOkHttpInterceptorTest {

    @Test
    fun `records and replays a response without a live network`() {
        val baseDir = createTempDir(prefix = "cassete_okhttp_")
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("hello")
                .addHeader("content-type", "text/plain")
        )
        server.start()

        try {
            val controller = Cassete.create(
                config = CasseteConfig(initialMode = Mode.RECORD),
                baseDir = baseDir
            )
            val client = OkHttpClient.Builder()
                .addInterceptor(CasseteOkHttp.interceptor(controller))
                .build()

            client.newCall(Request.Builder().url(server.url("/hello")).build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("hello", response.body?.string())
            }

            controller.switchMode(Mode.REPLAY)
            server.shutdown()

            client.newCall(Request.Builder().url(server.url("/hello")).build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("hello", response.body?.string())
                assertEquals("text/plain", response.header("content-type"))
            }
        } finally {
            baseDir.deleteRecursively()
            runCatching { server.shutdown() }
        }
    }

    @Test
    fun `uses request body hash to distinguish repeated POST requests`() {
        val baseDir = createTempDir(prefix = "cassete_okhttp_post_")
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("first"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("second"))
        server.start()

        try {
            val controller = Cassete.create(
                config = CasseteConfig(initialMode = Mode.RECORD),
                baseDir = baseDir
            )
            val client = OkHttpClient.Builder()
                .addInterceptor(CasseteOkHttp.interceptor(controller))
                .build()

            fun post(body: String): String {
                val request = Request.Builder()
                    .url(server.url("/submit"))
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response -> return response.body?.string().orEmpty() }
            }

            assertEquals("first", post("""{"id":1}"""))
            assertEquals("second", post("""{"id":2}"""))

            controller.switchMode(Mode.REPLAY)
            server.shutdown()

            assertEquals("first", post("""{"id":1}"""))
            assertEquals("second", post("""{"id":2}"""))
        } finally {
            baseDir.deleteRecursively()
            runCatching { server.shutdown() }
        }
    }

    @Test
    fun `throws on replay miss by default`() {
        val baseDir = createTempDir(prefix = "cassete_okhttp_miss_")
        try {
            val controller = Cassete.create(
                config = CasseteConfig(initialMode = Mode.REPLAY),
                baseDir = baseDir
            )
            val client = OkHttpClient.Builder()
                .addInterceptor(CasseteOkHttp.interceptor(controller))
                .build()

            val error = runCatching {
                client.newCall(Request.Builder().url("https://example.test/missing").build()).execute()
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is NoTapeFoundException || error.cause is NoTapeFoundException)
        } finally {
            baseDir.deleteRecursively()
        }
    }
}
