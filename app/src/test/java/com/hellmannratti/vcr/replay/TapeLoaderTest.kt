package com.hellmannratti.vcr.replay

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TapeLoaderTest {
    @Rule @JvmField val tmp = TemporaryFolder()

    private val json = Json { classDiscriminator = "type" }

    private fun writeLines(f: File, lines: List<String>) { f.writeText(lines.joinToString("\n", postfix = "\n")) }

    private fun ev(e: Event) = json.encodeToString(Event.serializer(), e)

    @Test(expected = NoTapeFoundException::class)
    fun `loadLatestSession throws when file missing`() {
        val f = File(tmp.root, "missing.ndjson")
        TapeLoader.loadLatestSession(f)
    }

    @Test(expected = NoTapeFoundException::class)
    fun `loadLatestSession throws when empty`() {
        val f = tmp.newFile("empty.ndjson")
        writeLines(f, listOf())
        TapeLoader.loadLatestSession(f)
    }

    @Test fun `loadLatestSession loads only last complete session`() {
        val f = tmp.newFile("multi.ndjson")
        val lines = mutableListOf<String>()
        // First session
        lines += ev(SessionStartEvent(1, "1.0", "emulator"))
        lines += ev(RequestEvent(2, "r1", "GET", "https://x/a"))
        lines += ev(ResponseEvent(3, "r1", 200, mapOf(), "a", 0))
        // Second session, incomplete (no response)
        lines += ev(SessionStartEvent(4, "1.0", "emulator"))
        lines += ev(RequestEvent(5, "r2", "GET", "https://x/b"))
        // Third session, complete
        lines += ev(SessionStartEvent(6, "1.0", "emulator"))
        lines += ev(RequestEvent(7, "r3", "POST", "https://x/c", bodySha256 = "abc"))
        lines += ev(ResponseEvent(8, "r3", 201, mapOf("content-type" to "text/plain"), "ok", 1))

        writeLines(f, lines)
        val tape = TapeLoader.loadLatestSession(f, logger = object : TapeLogger {
                    override fun d(tag: String, message: String) {}
                    override fun i(tag: String, message: String) {}
                    override fun w(tag: String, message: String) {}
                    override fun e(tag: String, message: String) {}
                })
        // Should contain just the POST https://x/c with hash "abc"
        val entries = tape.entries().toList()
        assertEquals(1, entries.size)
        val (key, _, rec) = entries[0]
        assertEquals("POST", key.method)
        assertEquals("https://x/c", key.url)
        assertEquals("abc", key.bodySha256)
        assertEquals(201, rec.code)
        assertEquals("ok", rec.body)
    }

    @Test fun `save then load round-trip`() {
        val f = tmp.newFile("round.ndjson")
        val map = mutableMapOf(
            RequestKey("GET", "https://a/b", null) to ArrayDeque(listOf(
                RecordedResponse(200, mapOf("x" to "1"), "one", 2)
            )),
            RequestKey("POST", "https://a/c", "abc") to ArrayDeque(listOf(
                RecordedResponse(201, mapOf("y" to "2"), "two", 3),
                RecordedResponse(202, mapOf("z" to "3"), "three", 4)
            ))
        )
        val tape = ReplayTape(map)
        TapeLoader.save(f, tape)
        val loaded = TapeLoader.loadFromFile(f)
        val orig = tape.entries().map { it.first to it.third }.groupBy({ it.first }) { it.second }
        val again = loaded.entries().map { it.first to it.third }.groupBy({ it.first }) { it.second }
        assertEquals(orig.keys, again.keys)
        for (k in orig.keys) {
            val l1 = orig[k]!!
            val l2 = again[k]!!
            assertEquals(l1.size, l2.size)
            for (i in l1.indices) {
                assertEquals(l1[i].code, l2[i].code)
                assertEquals(l1[i].headers, l2[i].headers)
                assertEquals(l1[i].body, l2[i].body)
                assertEquals(l1[i].durationMs, l2[i].durationMs)
            }
        }
    }
}
