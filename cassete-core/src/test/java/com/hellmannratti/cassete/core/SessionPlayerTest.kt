package com.hellmannratti.cassete.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempFile

@OptIn(ExperimentalCoroutinesApi::class)
class SessionPlayerTest {
    @Test
    fun `step forward emits events in order and advances position`() = runTest {
        val file = writeTape(
            uiEvents = listOf(
                UiEventRecorded(seq = 1, screen = "ApiTest", event = "FetchRandomPokemon"),
                UiEventRecorded(seq = 2, screen = "ApiTest", event = "DismissModal")
            )
        )

        try {
            val emitted = mutableListOf<SessionPlayer.RecordedUiEvent>()
            val player = SessionPlayer(
                scope = this,
                dispatcher = StandardTestDispatcher(testScheduler),
                emitUiEvent = { emitted += it },
                resetToInitialState = { }
            )

            player.loadLatestSession(file)

            assertEquals(0, player.state.value.position)
            assertEquals(2, player.state.value.total)
            assertEquals(file.absolutePath, player.state.value.sourceFile)

            assertTrue(player.stepForward())
            assertEquals(1, player.state.value.position)
            assertEquals(listOf("FetchRandomPokemon"), emitted.map { it.event })

            assertTrue(player.stepForward())
            assertEquals(2, player.state.value.position)
            assertEquals(
                listOf("FetchRandomPokemon", "DismissModal"),
                emitted.map { it.event }
            )

            assertFalse(player.stepForward())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `play emits remaining events and stops at end`() = runTest {
        val file = writeTape(
            uiEvents = listOf(
                UiEventRecorded(seq = 1, screen = "ApiTest", event = "A"),
                UiEventRecorded(seq = 2, screen = "ApiTest", event = "B"),
                UiEventRecorded(seq = 3, screen = "ApiTest", event = "C")
            )
        )

        try {
            val emitted = mutableListOf<String>()
            val player = SessionPlayer(
                scope = this,
                dispatcher = StandardTestDispatcher(testScheduler),
                emitUiEvent = { emitted += it.event },
                resetToInitialState = { }
            )

            player.loadLatestSession(file)
            player.play(stepDelayMs = 1L)
            advanceUntilIdle()

            assertEquals(listOf("A", "B", "C"), emitted)
            assertEquals(3, player.state.value.position)
            assertFalse(player.state.value.playing)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `step back resets state and replays up to the previous position`() = runTest {
        val file = writeTape(
            uiEvents = listOf(
                UiEventRecorded(seq = 1, screen = "ApiTest", event = "A"),
                UiEventRecorded(seq = 2, screen = "ApiTest", event = "B"),
                UiEventRecorded(seq = 3, screen = "ApiTest", event = "C")
            )
        )

        try {
            val emitted = mutableListOf<String>()
            var resets = 0
            val player = SessionPlayer(
                scope = this,
                dispatcher = StandardTestDispatcher(testScheduler),
                emitUiEvent = { emitted += it.event },
                resetToInitialState = { resets++ }
            )

            player.loadLatestSession(file)
            player.stepForward()
            player.stepForward()

            assertEquals(listOf("A", "B"), emitted)
            assertEquals(2, player.state.value.position)

            assertTrue(player.stepBack())
            assertEquals(1, resets)
            assertEquals(1, player.state.value.position)
            assertEquals(listOf("A", "B", "A"), emitted)
        } finally {
            file.delete()
        }
    }

    private fun writeTape(uiEvents: List<UiEventRecorded>): java.io.File {
        val json = Json { classDiscriminator = "type" }
        val file = createTempFile(prefix = "cassete_session_", suffix = ".ndjson").toFile()
        val lines = buildList {
            add(
                json.encodeToString(
                    Event.serializer(),
                    SessionStartEvent(seq = 0, ts = 0, appVersion = "test", device = "test")
                )
            )
            uiEvents.forEach { event ->
                add(json.encodeToString(Event.serializer(), event))
            }
        }
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        return file
    }
}
