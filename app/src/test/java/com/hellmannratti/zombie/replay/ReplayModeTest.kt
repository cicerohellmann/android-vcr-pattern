package com.hellmannratti.zombie.replay

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests to ensure REPLAY mode serves responses from tape without making real HTTP requests.
 */
class ReplayModeTest {
    
    private lateinit var mockServer: MockWebServer
    private lateinit var tempTapeFile: File
    private val logger = TestLogger()

    class TestLogger : TapeLogger {
        override fun d(tag: String, msg: String) = println("[DEBUG] $tag: $msg")
        override fun i(tag: String, msg: String) = println("[INFO] $tag: $msg")
        override fun w(tag: String, msg: String) = println("[WARN] $tag: $msg")
        override fun e(tag: String, msg: String) = println("[ERROR] $tag: $msg")
    }
    
    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        
        // Create a temporary tape file with a recorded response
        tempTapeFile = File.createTempFile("test_tape", ".ndjson")
        tempTapeFile.writeText("""
            {"type":"SESSION_START","ts":1234567890000,"appVersion":"1.0.0","device":"test-device"}
            {"type":"REQUEST","ts":1234567890100,"requestId":"req-1","method":"GET","url":"http://example.com/api/test","bodySha256":null}
            {"type":"RESPONSE","ts":1234567890200,"requestId":"req-1","code":200,"headers":{"content-type":"application/json"},"body":"{\"message\":\"recorded response\"}","durationMs":100}
        """.trimIndent())
    }
    
    @After
    fun teardown() {
        mockServer.shutdown()
        tempTapeFile.delete()
    }
    
    @Test
    fun `REPLAY mode should serve from tape without making real HTTP requests`() {
        // Load the tape
        val tape = TapeLoader.loadLatestSession(tempTapeFile, emptyList(), logger)
        
        // Create a replayer interceptor with the tape
        val replayer = ReplayerInterceptor(logger) { Mode.REPLAY }.apply {
            this.tape = tape
        }
        
        // Build client with ONLY the replayer (simulating REPLAY mode)
        val client = OkHttpClient.Builder()
            .addInterceptor(replayer)
            .build()
        
        // Make a request that matches the tape
        val request = Request.Builder()
            .url("http://example.com/api/test")
            .get()
            .build()
        
        val response = client.newCall(request).execute()
        
        // Verify the response came from the tape
        assertEquals(200, response.code)
        val body = response.body?.string()
        assertEquals("{\"message\":\"recorded response\"}", body)
        
        // Verify NO real HTTP request was made to the mock server
        assertEquals(0, mockServer.requestCount)
    }
    
    @Test
    fun `REPLAY mode should throw exception when no exact match found`() {
        // Load the tape
        val tape = TapeLoader.loadLatestSession(tempTapeFile, emptyList(), logger)
        
        // Create a replayer interceptor with the tape
        val replayer = ReplayerInterceptor(logger) { Mode.REPLAY }.apply {
            this.tape = tape
        }
        
        // Build client with ONLY the replayer
        val client = OkHttpClient.Builder()
            .addInterceptor(replayer)
            .build()
        
        // Make a request that DOES NOT match the tape
        val request = Request.Builder()
            .url("http://example.com/api/different")
            .get()
            .build()
        
        // Should throw NoTapeFoundException when no match is found
        try {
            client.newCall(request).execute()
            fail("Expected NoTapeFoundException to be thrown")
        } catch (e: NoTapeFoundException) {
            // Expected behavior - no match in tape
            assertTrue(e.message?.contains("No recorded response found") == true)
        }
        
        // Verify NO real HTTP request was made
        assertEquals(0, mockServer.requestCount)
    }
}
