package com.hellmannratti.cassete.ktor

import com.hellmannratti.cassete.core.Cassete
import com.hellmannratti.cassete.core.CasseteConfig
import com.hellmannratti.cassete.core.Mode
import com.hellmannratti.cassete.core.NoTapeFoundException
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CasseteKtorPluginTest {

    @Test
    fun `records and replays through the CIO engine`() = runBlocking {
        val baseDir = createTempDir(prefix = "cassete_ktor_cio_")
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("hello from ktor")
                .addHeader("content-type", "text/plain")
        )
        server.start()
        val targetUrl = server.url("/hello").toString()

        try {
            val controller = Cassete.create(
                config = CasseteConfig(initialMode = Mode.RECORD),
                baseDir = baseDir
            )
            val client = HttpClient(CIO) {
                install(CasseteKtor) {
                    this.controller = controller
                }
            }

            try {
                val recorded = client.get(targetUrl)
                assertEquals(200, recorded.status.value)
                assertEquals("hello from ktor", recorded.bodyAsText())

                controller.switchMode(Mode.REPLAY)
                server.shutdown()

                val replayed = client.get(targetUrl)
                assertEquals(200, replayed.status.value)
                assertEquals("hello from ktor", replayed.bodyAsText())
                assertEquals("text/plain", replayed.headers["content-type"])
            } finally {
                client.close()
            }
        } finally {
            baseDir.deleteRecursively()
            runCatching { server.shutdown() }
        }
    }

    @Test
    fun `uses request body hash to distinguish repeated POST requests`() = runBlocking {
        val baseDir = createTempDir(prefix = "cassete_ktor_post_")
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("first"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("second"))
        server.start()
        val targetUrl = server.url("/submit").toString()

        try {
            val controller = Cassete.create(
                config = CasseteConfig(initialMode = Mode.RECORD),
                baseDir = baseDir
            )
            val client = HttpClient(CIO) {
                install(CasseteKtor) {
                    this.controller = controller
                }
            }

            suspend fun post(body: String): String {
                return client.post(targetUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }.bodyAsText()
            }

            try {
                assertEquals("first", post("""{"id":1}"""))
                assertEquals("second", post("""{"id":2}"""))

                controller.switchMode(Mode.REPLAY)
                server.shutdown()

                assertEquals("first", post("""{"id":1}"""))
                assertEquals("second", post("""{"id":2}"""))
            } finally {
                client.close()
            }
        } finally {
            baseDir.deleteRecursively()
            runCatching { server.shutdown() }
        }
    }

    @Test
    fun `throws on replay miss by default`() = runBlocking {
        val baseDir = createTempDir(prefix = "cassete_ktor_miss_")
        val client = HttpClient(CIO) {
            install(CasseteKtor) {
                controller = Cassete.create(
                    config = CasseteConfig(initialMode = Mode.REPLAY),
                    baseDir = baseDir
                )
            }
        }

        try {
            val error = runCatching {
                client.get("https://example.test/missing")
            }.exceptionOrNull()

            requireNotNull(error)
            assertTrue(error is NoTapeFoundException || error.cause is NoTapeFoundException)
        } finally {
            client.close()
            baseDir.deleteRecursively()
        }
    }
}
