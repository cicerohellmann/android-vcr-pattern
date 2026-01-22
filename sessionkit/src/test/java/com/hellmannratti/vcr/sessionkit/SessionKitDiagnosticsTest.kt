package com.hellmannratti.vcr.sessionkit

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SessionKitDiagnosticsTest {

    @Test
    fun `loadTape writes diagnostics file on malformed tape`() {
        val dir = createTempDir(prefix = "sessionkit_diag_")
        try {
            val tapeFile = File(dir, "bad.ndjson").apply {
                parentFile?.mkdirs()
                writeText("{not json}\n")
            }

            val kit = SessionKit(
                config = SessionKitConfig(mode = Mode.REPLAY, tapeFile = tapeFile),
                baseDir = dir,
                logger = TapeLogger.NoOp
            )

            runCatching { kit.loadTape(tapeFile) }
                .onSuccess { error("Expected loadTape to fail") }

            val diag = kit.lastTapeLoadErrorFile
            assertNotNull(diag)
            assertTrue(diag!!.exists())

            val content = diag.readText()
            assertTrue(content.contains("Tape load failed"))
            assertTrue(content.contains("bad.ndjson"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
