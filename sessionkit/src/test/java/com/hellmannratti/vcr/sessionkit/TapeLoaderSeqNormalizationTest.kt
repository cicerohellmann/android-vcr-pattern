package com.hellmannratti.vcr.sessionkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TapeLoaderSeqNormalizationTest {

    private class CapturingLogger : TapeLogger {
        val warnings = mutableListOf<String>()

        override fun d(tag: String, message: String) {}
        override fun i(tag: String, message: String) {}

        override fun w(tag: String, message: String) {
            warnings += "[$tag] $message"
        }

        override fun e(tag: String, message: String) {}
    }

    @Test
    fun `loadLatestSession normalizes non-increasing seq in file order`() {
        val dir = createTempDir(prefix = "sessionkit_tape_")
        try {
            val file = File(dir, "events.ndjson")
            file.parentFile?.mkdirs()

            // Intentionally out-of-order seq: 2 then 1.
            // This can happen in practice if a tape was edited or written concurrently.
            file.writeText(
                buildString {
                    appendLine(
                        "{\"schema\":1,\"seq\":0,\"type\":\"SESSION_START\",\"ts\":1700000000000,\"metadata\":{},\"appVersion\":\"1.0\",\"device\":\"test\"}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":2,\"type\":\"REQUEST\",\"ts\":1700000000100,\"metadata\":{},\"requestId\":\"r1\",\"method\":\"GET\",\"url\":\"https://example.com/hello\",\"bodySha256\":null}"
                    )
                    appendLine(
                        "{\"schema\":1,\"seq\":1,\"type\":\"RESPONSE\",\"ts\":1700000000200,\"metadata\":{},\"requestId\":\"r1\",\"code\":200,\"headers\":{\"Content-Type\":\"text/plain\"},\"body\":\"ok\",\"durationMs\":1}"
                    )
                }
            )

            val logger = CapturingLogger()
            val tape = TapeLoader.loadLatestSession(file = file, logger = logger)

            assertEquals(1, tape.uniqueRequestCount)
            assertTrue(logger.warnings.any { it.contains("Normalizing non-increasing seq") })
        } finally {
            dir.deleteRecursively()
        }
    }
}
