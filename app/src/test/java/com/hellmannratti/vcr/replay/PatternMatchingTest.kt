package com.hellmannratti.vcr.replay

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Test to verify URL pattern matching works with different IDs
 */
class PatternMatchingTest {
    
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
        // Create a tape file with responses for posts/4, posts/41, and posts/67
        tempTapeFile = File.createTempFile("test_pattern_tape", ".ndjson")
        tempTapeFile.writeText("""
            {"type":"SESSION_START","ts":1234567890000,"appVersion":"1.0.0","device":"test-device"}
            {"type":"REQUEST","ts":1234567890100,"requestId":"req-1","method":"GET","url":"https://jsonplaceholder.typicode.com/posts/4","bodySha256":null}
            {"type":"RESPONSE","ts":1234567890200,"requestId":"req-1","code":200,"headers":{"content-type":"application/json"},"body":"{\"id\":4,\"title\":\"post 4\"}","durationMs":100}
            {"type":"REQUEST","ts":1234567890300,"requestId":"req-2","method":"GET","url":"https://jsonplaceholder.typicode.com/posts/41","bodySha256":null}
            {"type":"RESPONSE","ts":1234567890400,"requestId":"req-2","code":200,"headers":{"content-type":"application/json"},"body":"{\"id\":41,\"title\":\"post 41\"}","durationMs":100}
            {"type":"REQUEST","ts":1234567890500,"requestId":"req-3","method":"GET","url":"https://jsonplaceholder.typicode.com/posts/67","bodySha256":null}
            {"type":"RESPONSE","ts":1234567890600,"requestId":"req-3","code":200,"headers":{"content-type":"application/json"},"body":"{\"id\":67,\"title\":\"post 67\"}","durationMs":100}
        """.trimIndent())
    }
    
    @After
    fun teardown() {
        tempTapeFile.delete()
    }
    
    @Test
    fun `should serve any recorded response when pattern matches with different ID`() {
        // Define the URL pattern
        val patterns = listOf(
            UrlPattern.fromRetrofitStyle("https://jsonplaceholder.typicode.com/posts/{id}")
        )
        
        // Load the tape with patterns
        val tape = TapeLoader.loadLatestSession(tempTapeFile, patterns, logger)
        
        println("[DEBUG_LOG] Tape loaded with ${tape.uniqueRequestCount} unique request(s)")
        
        // Create a replayer interceptor with the tape
        val replayer = ReplayerInterceptor(logger) { Mode.REPLAY }.apply {
            this.tape = tape
        }
        
        // Build client with the replayer
        val client = OkHttpClient.Builder()
            .addInterceptor(replayer)
            .build()
        
        // Try to request a DIFFERENT ID that wasn't recorded (e.g., /posts/23)
        val request = Request.Builder()
            .url("https://jsonplaceholder.typicode.com/posts/23")
            .get()
            .build()
        
        println("[DEBUG_LOG] Making request for /posts/23 (not in original recordings)")
        
        // This should work because the pattern matches and we have recorded responses
        val response = client.newCall(request).execute()
        
        // Verify we got A response (should be one of the recorded ones)
        assertEquals(200, response.code)
        val body = response.body?.string()
        assertNotNull(body)
        println("[DEBUG_LOG] Got response: $body")
        
        // The body should be one of the three recorded responses
        assertTrue(
            body == "{\"id\":4,\"title\":\"post 4\"}" ||
            body == "{\"id\":41,\"title\":\"post 41\"}" ||
            body == "{\"id\":67,\"title\":\"post 67\"}"
        )
    }
    
    @Test
    fun `should serve multiple responses in sequence for pattern-matched requests`() {
        // Define the URL pattern
        val patterns = listOf(
            UrlPattern.fromRetrofitStyle("https://jsonplaceholder.typicode.com/posts/{id}")
        )
        
        // Load the tape with patterns
        val tape = TapeLoader.loadLatestSession(tempTapeFile, patterns, logger)
        
        // Create a replayer interceptor with the tape
        val replayer = ReplayerInterceptor(logger) { Mode.REPLAY }.apply {
            this.tape = tape
        }
        
        // Build client with the replayer
        val client = OkHttpClient.Builder()
            .addInterceptor(replayer)
            .build()
        
        // Make three requests with different IDs
        val responses = mutableListOf<String>()
        
        for (id in listOf(99, 88, 77)) {
            val request = Request.Builder()
                .url("https://jsonplaceholder.typicode.com/posts/$id")
                .get()
                .build()
            
            println("[DEBUG_LOG] Making request for /posts/$id")
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            responses.add(body)
            println("[DEBUG_LOG] Got response: $body")
        }
        
        // All three requests should succeed and get responses
        assertEquals(3, responses.size)
        responses.forEach { body ->
            assertTrue(
                body == "{\"id\":4,\"title\":\"post 4\"}" ||
                body == "{\"id\":41,\"title\":\"post 41\"}" ||
                body == "{\"id\":67,\"title\":\"post 67\"}"
            )
        }
    }
}
