package com.hellmannratti.vcr

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CasseteInstrumentedE2ETest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var targetContext: Context
    private lateinit var app: VcrApp
    private lateinit var server: MockWebServer
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        app = targetContext.applicationContext as VcrApp
        resetSessionArtifacts()

        server = MockWebServer()
        server.start()

        AppTestOverrides.apiEndpoints = ApiEndpoints(
            pokemonBaseUrl = server.url("/pokeapi/api/v2").toString().trimEnd('/'),
            githubBaseUrl = server.url("/github").toString().trimEnd('/'),
            jsonPlaceholderBaseUrl = server.url("/jsonplaceholder").toString().trimEnd('/')
        )
        app.reinitializeForTesting()
    }

    @After
    fun tearDown() {
        scenario?.close()
        runCatching { server.shutdown() }
        resetSessionArtifacts()
        AppTestOverrides.reset()
        app.reinitializeForTesting()
    }

    @Test
    fun githubFlowRecordsResponseIntoTapeOnDevice() {
        val responseBody = """{"login":"torvalds-device-record","name":"Device Record"}"""
        server.enqueue(jsonResponse(responseBody))

        launchMainActivity()

        composeRule.onNodeWithText("Fetch GitHub User (torvalds)").performClick()
        waitForText("GitHub API Response")
        assertTextVisible("torvalds-device-record")

        composeRule.waitUntil(timeoutMillis = 10_000) {
            val tape = sessionFile()
            tape.exists() && tape.readText().contains("torvalds-device-record")
        }

        val tapeText = sessionFile().readText()
        assertTrue(tapeText.contains("torvalds-device-record"))
        assertTrue(tapeText.contains("\"type\":\"REQUEST\""))
        assertTrue(tapeText.contains("\"type\":\"RESPONSE\""))
    }

    @Test
    fun githubFlowReplaysRecordedResponseAfterBackendShutdown() {
        val responseBody = """{"login":"torvalds-device-replay","name":"Device Replay"}"""
        server.enqueue(jsonResponse(responseBody))

        launchMainActivity()

        composeRule.onNodeWithText("Fetch GitHub User (torvalds)").performClick()
        waitForText("GitHub API Response")
        assertTextVisible("torvalds-device-replay")
        composeRule.onNodeWithText("Close").performClick()

        composeRule.onNodeWithText("Switch to REPLAY").performClick()
        waitForText("Current Mode: REPLAY")

        server.shutdown()

        composeRule.onNodeWithText("Fetch GitHub User (torvalds)").performClick()
        waitForText("GitHub API Response")
        assertTextVisible("torvalds-device-replay")
        assertEquals(1, server.requestCount)
    }

    private fun launchMainActivity() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()
        waitForText("Current Mode: RECORD")
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertTextVisible(text: String) {
        waitForText(text)
        assertTrue(
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun sessionFile() = targetContext.filesDir.resolve("sessions/events.ndjson")

    private fun resetSessionArtifacts() {
        val sessionsDir = targetContext.filesDir.resolve("sessions")
        sessionsDir.deleteRecursively()
        sessionsDir.mkdirs()
        sessionsDir.resolve("events.ndjson").writeText("")
    }

    private fun jsonResponse(body: String): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setBody(body)
            .addHeader("content-type", "application/json")
    }
}
