package com.hellmannratti.vcr.sessionkit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SessionPlayerTest {
    @Test
    fun `step forward emits events in order and advances position`() = runBlocking {
        val file = writeTape(
            uiEvents = listOf(
                UiEventRecorded(seq = 1, screen = "ApiTest", event = "FetchRandomPokemon"),
                UiEventRecorded(seq = 2, screen = "ApiTest", event = "DismissModal")
            )
        )

        val emitted = mutableListOf<SessionPlayer.RecordedUiEvent>()
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val player = SessionPlayer(
            scope = scope,
            emitUiEvent = { emitted += it },
            resetToInitialState = { }
        )

        player.loadLatestSession(file)
        assertEquals(0, player.state.value.position)
        assertEquals(2, player.state.value.total)

        assertTrue(player.stepForward())
        assertEquals(1, player.state.value.position)
        assertEquals("FetchRandomPokemon", emitted[0].event)

        assertTrue(player.stepForward())
        assertEquals(2, player.state.value.position)
        assertEquals("DismissModal", emitted[1].event)

        assertEquals(false, player.stepForward())
    }

    @Test
    fun `seek backwards resets then fast-forwards to target position`() = runBlocking {
        val file = writeTape(
            uiEvents = listOf(
                UiEventRecorded(seq = 1, screen = "ApiTest", event = "A"),
                UiEventRecorded(seq = 2, screen = "ApiTest", event = "B"),
                UiEventRecorded(seq = 3, screen = "ApiTest", event = "C")
            )
        )

        val emitted = mutableListOf<String>()
        var resets = 0
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val player = SessionPlayer(
            scope = scope,
            emitUiEvent = { emitted += it.event },
            resetToInitialState = { resets++ }
        )

        player.loadLatestSession(file)
        player.stepForward() // A
        player.stepForward() // B
        assertEquals(listOf("A", "B"), emitted)
        assertEquals(2, player.state.value.position)

        // Seek back to position=1 should reset and then fast-forward emitting A.
        player.seek(1)
        assertEquals(1, resets)
        assertEquals(1, player.state.value.position)
        assertEquals(listOf("A", "B", "A"), emitted)
    }

    private fun writeTape(uiEvents: List<UiEventRecorded>): File {
        val json = Json { classDiscriminator = "type" }
        val file = kotlin.io.path.createTempFile(prefix = "vcr_session_", suffix = ".ndjson").toFile()
        val lines = buildList {
            add(
                json.encodeToString(
                    Event.serializer(),
                    SessionStartEvent(seq = 0, ts = 0, appVersion = "test", device = "test")
                )
            )
            uiEvents.forEach { e ->
                add(json.encodeToString(Event.serializer(), e))
            }
        }
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        return file
    }
}
