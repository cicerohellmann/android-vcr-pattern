package com.hellmannratti.vcr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ReplayNondeterminismTest {
    @Test
    fun `replays random time and uuid in recorded order`() {
        val dir = createTempDirectory(prefix = "replay_inputs_").toFile()

        try {
            val tape = File(dir, "events.ndjson")
            tape.writeText(
                buildString {
                    appendLine("""{"schema":1,"seq":0,"type":"SESSION_START","ts":1,"metadata":{},"appVersion":"1.0","device":"test"}""")
                    appendLine("""{"schema":1,"seq":1,"type":"ACTION","ts":2,"metadata":{},"name":"ND_RANDOM_INT","details":{"from":1,"until":10,"value":4}}""")
                    appendLine("""{"schema":1,"seq":2,"type":"ACTION","ts":3,"metadata":{},"name":"ND_TIME","details":{"nowMs":12345}}""")
                    appendLine("""{"schema":1,"seq":3,"type":"ACTION","ts":4,"metadata":{},"name":"ND_UUID","details":{"value":"uuid-1"}}""")
                }
            )

            val replay = ReplayNondeterminism()
            replay.load(tape)

            assertEquals(4, replay.nextRandomInt(1, 10))
            assertEquals(12345L, replay.nextNowMs())
            assertEquals("uuid-1", replay.nextUuid())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `reset restores the recorded nondeterminism stream`() {
        val dir = createTempDirectory(prefix = "replay_inputs_reset_").toFile()

        try {
            val tape = File(dir, "events.ndjson")
            tape.writeText(
                buildString {
                    appendLine("""{"schema":1,"seq":0,"type":"SESSION_START","ts":1,"metadata":{},"appVersion":"1.0","device":"test"}""")
                    appendLine("""{"schema":1,"seq":1,"type":"ACTION","ts":2,"metadata":{},"name":"ND_RANDOM_INT","details":{"from":1,"until":10,"value":7}}""")
                }
            )

            val replay = ReplayNondeterminism()
            replay.load(tape)

            assertEquals(7, replay.nextRandomInt(1, 10))
            replay.reset()
            assertEquals(7, replay.nextRandomInt(1, 10))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `mismatched action order fails fast`() {
        val dir = createTempDirectory(prefix = "replay_inputs_mismatch_").toFile()

        try {
            val tape = File(dir, "events.ndjson")
            tape.writeText(
                buildString {
                    appendLine("""{"schema":1,"seq":0,"type":"SESSION_START","ts":1,"metadata":{},"appVersion":"1.0","device":"test"}""")
                    appendLine("""{"schema":1,"seq":1,"type":"ACTION","ts":2,"metadata":{},"name":"ND_UUID","details":{"value":"uuid-1"}}""")
                }
            )

            val replay = ReplayNondeterminism()
            replay.load(tape)

            val error = runCatching { replay.nextRandomInt(1, 10) }.exceptionOrNull()
            requireNotNull(error)
            assertTrue(error.message.orEmpty().contains("expected ND_RANDOM_INT"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
