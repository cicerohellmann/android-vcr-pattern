package com.hellmannratti.vcr.framework

import android.content.Context
import com.hellmannratti.vcr.VcrApp
import com.hellmannratti.vcr.framework.AndroidTapeLogger
import com.hellmannratti.vcr.replay.AppConfig
import com.hellmannratti.vcr.replay.Mode
import com.hellmannratti.vcr.replay.NoTapeFoundException
import com.hellmannratti.vcr.replay.RecordingInterceptor
import com.hellmannratti.vcr.replay.ReplayerInterceptor
import com.hellmannratti.vcr.replay.TapeLoader
import com.hellmannratti.vcr.replay.UrlPattern
import okhttp3.OkHttpClient

/**
 * Minimal OkHttp client wiring RecordingInterceptor as the outermost interceptor.
 */
object HttpClients {
    fun recording(context: Context): OkHttpClient {
        val app = VcrApp.from(context)
        val recorder = app.recorder
        return OkHttpClient.Builder()
            .addInterceptor(RecordingInterceptor(recorder, clock = app.clock, idGenerator = app.idGenerator) { Mode.RECORD })
            .build()
    }

    /**
     * Step 8: Wiring example — full client with Recording + Replayer and mode switching.
     *
     * Interceptor order matters: Recording first (outermost), Replayer second.
     */
    fun client(context: Context, config: AppConfig): OkHttpClient {
        val app = VcrApp.from(context)
        val recorder = app.recorder

        return when (config.mode) {
            Mode.PASSTHROUGH -> {
                // Plain client: no recording, no replay
                OkHttpClient.Builder().build()
            }
            Mode.RECORD -> {
                OkHttpClient.Builder()
                    .addInterceptor(
                        RecordingInterceptor(
                            recorder = recorder,
                            clock = app.clock,
                            idGenerator = app.idGenerator
                        ) { config.mode }
                    )
                    .build()
            }
            Mode.REPLAY -> {
                val file = config.tapeFile ?: app.recorder.file()
                val logger = AndroidTapeLogger()
                
                try {
                    // Define URL patterns for multiple APIs
                    val patterns = listOf(
                        // Pokemon API
                        UrlPattern.fromRetrofitStyle("https://pokeapi.co/api/v2/pokemon/{id}"),
                        
                        // GitHub API
                        UrlPattern.fromRetrofitStyle("https://api.github.com/users/{name}"),
                        UrlPattern.fromRetrofitStyle("https://api.github.com/repos/{name}/{name}"),
                        
                        // JSONPlaceholder (testing API)
                        UrlPattern.fromRetrofitStyle("https://jsonplaceholder.typicode.com/posts/{id}"),
                        UrlPattern.fromRetrofitStyle("https://jsonplaceholder.typicode.com/users/{id}")
                    )
                    
                    val replayer = ReplayerInterceptor(logger) { config.mode }.apply {
                        tape = TapeLoader.loadLatestSession(file, patterns, logger)
                    }
                    OkHttpClient.Builder()
                        .addInterceptor(replayer)
                        .build()
                } catch (e: NoTapeFoundException) {
                    logger.w("HttpClients", "No tape found, using passthrough mode: ${e.message}")
                    // Fall back to a plain client if tape doesn't exist
                    OkHttpClient.Builder().build()
                }
            }
        }
    }
}
