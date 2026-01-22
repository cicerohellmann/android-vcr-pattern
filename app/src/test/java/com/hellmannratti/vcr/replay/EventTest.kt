package com.hellmannratti.vcr.replay

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventTest {
    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    @Test fun `serialize and deserialize SessionStartEvent`() {
        val e: Event = SessionStartEvent(seq = 0L, ts = 1L, appVersion = "1.0.0", device = "emulator")
        val s = json.encodeToString(Event.serializer(), e)
        val back = json.decodeFromString<Event>(s)
        assertTrue(back is SessionStartEvent)
        back as SessionStartEvent
        assertEquals(0L, back.seq)
        assertEquals(1L, back.ts)
        assertEquals("1.0.0", back.appVersion)
        assertEquals("emulator", back.device)
    }

    @Test fun `serialize and deserialize ActionEvent`() {
        val e: Event = ActionEvent(seq = 1L, ts = 2L, name = "Click", details = buildJsonObject { put("x", 10); put("y", 20) })
        val s = json.encodeToString(Event.serializer(), e)
        val back = json.decodeFromString<Event>(s)
        assertTrue(back is ActionEvent)
        back as ActionEvent
        assertEquals("Click", back.name)
        assertEquals(1L, back.seq)
        assertEquals(2L, back.ts)
    }

    @Test fun `serialize and deserialize Request and Response events`() {
        val req: Event = RequestEvent(seq = 2L, ts = 3L, requestId = "abc", method = "GET", url = "https://example.com", bodySha256 = null)
        val res: Event = ResponseEvent(seq = 3L, ts = 4L, requestId = "abc", code = 200, headers = mapOf("content-type" to "text/plain"), body = "ok", durationMs = 5)
        val s1 = json.encodeToString(Event.serializer(), req)
        val s2 = json.encodeToString(Event.serializer(), res)
        val b1 = json.decodeFromString<Event>(s1)
        val b2 = json.decodeFromString<Event>(s2)
        assertTrue(b1 is RequestEvent)
        assertTrue(b2 is ResponseEvent)
        assertEquals("abc", (b2 as ResponseEvent).requestId)
    }
}
