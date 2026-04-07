package com.hellmannratti.vcr

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.provider.MediaStore
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CasseteVisualProofInstrumentedTest {

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
        AppTestOverrides.suppressToasts = true
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
    fun writesGitHubRecordReplayVisualProof() {
        val responseBody = """{"login":"cassete-proof"}"""
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseBody)
                .addHeader("content-type", "application/json")
                .setBodyDelay(650, TimeUnit.MILLISECONDS)
        )

        launchMainActivity(expectedMode = "RECORD")

        captureStillAndFrame(
            staticName = "sample_app_record_home.png",
            frameName = "000_record_home.png"
        )

        composeRule.onNodeWithText("Fetch GitHub User (torvalds)").performClick()
        waitForText("Loading")
        settleVisualState(delayMs = 120L)
        captureFrame("010_loading.png")

        waitForText("GitHub API Response")
        settleVisualState()
        captureStillAndFrame(
            staticName = "sample_app_record_response.png",
            frameName = "020_record_response.png"
        )

        composeRule.onNodeWithText("Close").performClick()
        waitForNoText("GitHub API Response")
        waitForNoText("Close")
        settleVisualState()
        composeRule.onNodeWithText("Switch to REPLAY").performClick()
        waitForText("Current Mode: REPLAY")
        settleVisualState()
        relaunchMainActivity(expectedMode = "REPLAY")

        captureStillAndFrame(
            staticName = "sample_app_replay_home.png",
            frameName = "030_replay_home.png"
        )

        server.shutdown()

        composeRule.onNodeWithText("Fetch GitHub User (torvalds)").performClick()
        waitForText("GitHub API Response")
        settleVisualState()
        captureStillAndFrame(
            staticName = "sample_app_replay_response.png",
            frameName = "040_replay_response.png"
        )
    }

    private fun launchMainActivity(expectedMode: String) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()
        waitForText("Current Mode: $expectedMode")
        settleVisualState()
    }

    private fun relaunchMainActivity(expectedMode: String) {
        scenario?.close()
        scenario = null
        launchMainActivity(expectedMode = expectedMode)
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForNoText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun settleVisualState(delayMs: Long = 300L) {
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        sleep(delayMs)
        composeRule.waitForIdle()
    }

    private fun captureStillAndFrame(
        staticName: String,
        frameName: String
    ) {
        val bitmap = captureRootBitmap()
        writeBitmap(relativeDir = "static", fileName = staticName, bitmap = bitmap)
        writeBitmap(relativeDir = "motion/github_record_to_replay", fileName = frameName, bitmap = bitmap)
    }

    private fun captureFrame(frameName: String) {
        writeBitmap(
            relativeDir = "motion/github_record_to_replay",
            fileName = frameName,
            bitmap = captureRootBitmap()
        )
    }

    private fun captureRootBitmap(): Bitmap {
        composeRule.waitForIdle()
        val fullScreenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        var windowBounds: Rect? = null
        scenario?.onActivity { activity ->
            val location = IntArray(2)
            val decorView = activity.window.decorView
            decorView.getLocationOnScreen(location)
            windowBounds = Rect(
                location[0],
                location[1],
                location[0] + decorView.width,
                location[1] + decorView.height
            )
        }

        val bounds = requireNotNull(windowBounds) { "Failed to resolve activity window bounds." }
        val left = bounds.left.coerceAtLeast(0)
        val top = bounds.top.coerceAtLeast(0)
        val width = bounds.width().coerceAtMost(fullScreenshot.width - left)
        val height = bounds.height().coerceAtMost(fullScreenshot.height - top)
        return Bitmap.createBitmap(fullScreenshot, left, top, width, height)
    }

    private fun writeBitmap(
        relativeDir: String,
        fileName: String,
        bitmap: Bitmap
    ) {
        val relativePath = "Download/cassete_visual_proof/$relativeDir/"
        deleteExistingDownload(relativePath = relativePath, fileName = fileName)

        val resolver = targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = requireNotNull(
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ) { "Failed to create Downloads entry for $fileName" }

        resolver.openOutputStream(uri)?.use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Failed to compress $fileName"
            }
        } ?: error("Failed to open output stream for $fileName")

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun deleteExistingDownload(
        relativePath: String,
        fileName: String
    ) {
        val resolver = targetContext.contentResolver
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection =
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(fileName, relativePath)

        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                resolver.delete(
                    ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                    null,
                    null
                )
            }
        }
    }

    private fun resetSessionArtifacts() {
        val sessionsDir = targetContext.filesDir.resolve("sessions")
        sessionsDir.deleteRecursively()
        sessionsDir.mkdirs()
        sessionsDir.resolve("events.ndjson").writeText("")
    }
}
