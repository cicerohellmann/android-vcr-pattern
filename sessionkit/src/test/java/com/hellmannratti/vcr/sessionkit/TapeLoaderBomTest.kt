package com.hellmannratti.vcr.sessionkit

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class TapeLoaderBomTest {

    @Test
    fun `loadLatestSession strips UTF-8 BOM on first line`() {
        val dir = createTempDir(prefix = "sessionkit_tape_")
        try {
            val file = File(dir, "events.ndjson")
            file.parentFile?.mkdirs()

            // First line contains a UTF-8 BOM (\uFEFF) before the JSON object.
            val content = buildString {
                append("\uFEFF")
                appendLine(
                    "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                )
                appendLine(
                    "{\"schema\":1,\"seq\":1,\"type\":\"REQUEST\",\"ts\":1700000000100,\"metadata\":{},\"requestId\":\"r1\",\"method\":\"GET\",\"url\":\"https://example.com/hello\",\"bodySha256\":null}"
                )
                appendLine(
                    "{\"schema\":1,\"seq\":2,\"type\":\"RESPONSE\",\"ts\":1700000000200,\"metadata\":{},\"requestId\":\"r1\",\"code\":200,\"headers\":{\"Content-Type\":\"text/plain\"},\"body\":\"ok\",\"durationMs\":1}"
                )
            }
            file.writeText(content)

            val tape = TapeLoader.loadLatestSession(file = file)
            assertEquals(1, tape.uniqueRequestCount)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `loadLatestSession tolerates CRLF line endings`() {
        val dir = createTempDir(prefix = "sessionkit_tape_")
        try {
            val file = File(dir, "events.ndjson")
            val content =
                "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}\r\n" +
                    "{\"schema\":1,\"seq\":1,\"type\":\"REQUEST\",\"ts\":1700000000100,\"metadata\":{},\"requestId\":\"r1\",\"method\":\"GET\",\"url\":\"https://example.com/hello\",\"bodySha256\":null}\r\n" +
                    "{\"schema\":1,\"seq\":2,\"type\":\"RESPONSE\",\"ts\":1700000000200,\"metadata\":{},\"requestId\":\"r1\",\"code\":200,\"headers\":{\"Content-Type\":\"text/plain\"},\"body\":\"ok\",\"durationMs\":1}\r\n"

            file.writeText(content)

            val tape = TapeLoader.loadLatestSession(file = file)
            assertEquals(1, tape.uniqueRequestCount)
        } finally {
            dir.deleteRecursively()
        }
    }
}
